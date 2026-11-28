package com.hospital.backend.service.impl;

import com.hospital.backend.dto.bed.HistoricalOccupancyReportDTO;
import com.hospital.backend.dto.bed.HistoricalOccupancyReportDTO.DailyOccupancyMetricDTO;
import com.hospital.backend.model.Bed;
import com.hospital.backend.model.BedBooking;
import com.hospital.backend.model.BedOccupancyEvent;
import com.hospital.backend.repository.BedBookingRepository;
import com.hospital.backend.repository.BedOccupancyEventRepository;
import com.hospital.backend.repository.BedRepository;
import com.hospital.backend.service.BedOccupancyAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BedOccupancyAnalyticsServiceImpl implements BedOccupancyAnalyticsService {

    private final BedOccupancyEventRepository eventRepository;
    private final BedRepository bedRepository;
    private final BedBookingRepository bedBookingRepository;

    private static final String DEFAULT_TIMEZONE = "Asia/Colombo";

    @Override
    public HistoricalOccupancyReportDTO getHistoricalOccupancyForPreset(String preset, String wardId, String bedType, String timeZoneId) {
        ZoneId zone = resolveZone(timeZoneId);
        ZonedDateTime now = ZonedDateTime.now(zone);
        Instant end = now.toInstant();
        Instant start;

        if ("TODAY".equalsIgnoreCase(preset)) {
            start = now.truncatedTo(ChronoUnit.DAYS).toInstant();
        } else if ("7D".equalsIgnoreCase(preset) || "LAST_7_DAYS".equalsIgnoreCase(preset)) {
            start = now.minusDays(7).truncatedTo(ChronoUnit.DAYS).toInstant();
        } else if ("30D".equalsIgnoreCase(preset) || "LAST_30_DAYS".equalsIgnoreCase(preset)) {
            start = now.minusDays(30).truncatedTo(ChronoUnit.DAYS).toInstant();
        } else {
            // Default 7 days
            start = now.minusDays(7).truncatedTo(ChronoUnit.DAYS).toInstant();
        }

        return getHistoricalOccupancy(start, end, wardId, bedType, timeZoneId);
    }

    @Override
    public HistoricalOccupancyReportDTO getDailyOccupancy(LocalDate date, String wardId, String bedType, String timeZoneId) {
        ZoneId zone = resolveZone(timeZoneId);
        ZonedDateTime dayStart = date.atStartOfDay(zone);
        ZonedDateTime dayEnd = dayStart.plusDays(1);
        return getHistoricalOccupancy(dayStart.toInstant(), dayEnd.toInstant(), wardId, bedType, timeZoneId);
    }

    @Override
    public HistoricalOccupancyReportDTO getHistoricalOccupancy(Instant start, Instant end, String wardId, String bedType, String timeZoneId) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("Valid interval required: start must be strictly before end");
        }

        ZoneId zone = resolveZone(timeZoneId);

        // 1. Resolve filtered beds
        List<Bed> allBeds;
        try {
            allBeds = bedRepository.findByActive(true);
            if (allBeds == null || allBeds.isEmpty()) {
                allBeds = bedRepository.findAll();
            }
        } catch (Exception e) {
            allBeds = bedRepository.findAll();
        }

        List<Bed> eligibleBeds = allBeds.stream()
                .filter(b -> wardId == null || wardId.isBlank() || wardId.equals(b.getWardId()) || wardId.equalsIgnoreCase(b.getWard()))
                .filter(b -> bedType == null || bedType.isBlank() || bedType.equalsIgnoreCase(b.getType()))
                .filter(Bed::isActive)
                .toList();

        double intervalHours = Duration.between(start, end).toMinutes() / 60.0;
        double totalAvailableBedHours = eligibleBeds.size() * intervalHours;

        // 2. Interval-based reconstruction of occupied bed hours
        double totalOccupiedBedHours = 0.0;
        Map<String, Double> wardUtilization = new HashMap<>();
        Map<String, Double> bedTypeUtilization = new HashMap<>();

        for (Bed bed : eligibleBeds) {
            List<BedOccupancyEvent> bedEvents = eventRepository.findByBedIdOrderByOccurredAtAsc(bed.getId());
            if (bedEvents == null || bedEvents.isEmpty()) {
                bedEvents = eventRepository.findByBedIdAndOccurredAtBetweenOrderByOccurredAtAsc(bed.getId(), start.minus(Duration.ofDays(30)), end);
            }
            double bedOccupiedHours = calculateBedOccupiedHoursInWindow(bedEvents, bed, start, end);
            totalOccupiedBedHours += bedOccupiedHours;

            String wardKey = bed.getWard() != null ? bed.getWard() : (bed.getWardId() != null ? bed.getWardId() : "General");
            wardUtilization.put(wardKey, wardUtilization.getOrDefault(wardKey, 0.0) + bedOccupiedHours);

            String typeKey = bed.getType() != null ? bed.getType() : "GENERAL";
            bedTypeUtilization.put(typeKey, bedTypeUtilization.getOrDefault(typeKey, 0.0) + bedOccupiedHours);
        }

        // 3. Occupancy rate calculation (Safe against 0 denominator, do not clamp >100% to mask invalid data)
        double occupancyRate = 0.0;
        if (totalAvailableBedHours > 0) {
            occupancyRate = (totalOccupiedBedHours / totalAvailableBedHours) * 100.0;
            occupancyRate = Math.round(occupancyRate * 100.0) / 100.0;
        }

        // 4. Counts in period
        List<BedOccupancyEvent> periodEvents = eventRepository.findByOccurredAtBetweenOrderByOccurredAtAsc(start, end);
        long admissionsCount = periodEvents.stream()
                .filter(e -> "BED_OCCUPIED".equals(e.getEventType()) && (e.getSequenceNumber() == null || e.getSequenceNumber() <= 1))
                .count();
        long dischargesCount = periodEvents.stream()
                .filter(e -> "DISCHARGE_COMPLETED".equals(e.getEventType()))
                .count();
        long transfersCount = periodEvents.stream()
                .filter(e -> "TRANSFER_COMPLETED".equals(e.getEventType()))
                .count();

        // 5. Average Length of Stay (ALOS)
        double alosHours = calculateAverageLengthOfStay(start, end);

        // 6. Data Completeness Status
        String completenessStatus = "COMPLETE";
        String completenessNotice = "Full historical interval reconstruction available.";

        long totalEvents = eventRepository.count();
        if (totalEvents == 0) {
            completenessStatus = "CURRENT_SNAPSHOT_ONLY";
            completenessNotice = "No historical occupancy events recorded yet. Historical metrics derived from current real-time snapshot.";
        } else {
            List<BedOccupancyEvent> allRecorded = eventRepository.findAll();
            if (allRecorded == null || allRecorded.isEmpty()) {
                allRecorded = eventRepository.findByOccurredAtBetweenOrderByOccurredAtAsc(Instant.EPOCH, end);
            }
            Instant earliest = (allRecorded != null) ? allRecorded.stream()
                    .map(BedOccupancyEvent::getOccurredAt)
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(start) : start;
            Instant earliestDay = earliest.atZone(zone).truncatedTo(ChronoUnit.DAYS).toInstant();
            if (start.isBefore(earliestDay)) {
                completenessStatus = "PARTIAL_HISTORY";
                completenessNotice = "Event logging commenced at " + earliest + ". Selected period includes intervals prior to event log activation.";
            }
        }

        // 7. Daily Timeline Breakdown
        List<DailyOccupancyMetricDTO> dailyTimeline = generateDailyTimeline(start, end, eligibleBeds, zone);

        return HistoricalOccupancyReportDTO.builder()
                .periodStart(start)
                .periodEnd(end)
                .timeZone(zone.getId())
                .filterWardId(wardId)
                .filterBedType(bedType)
                .totalOccupiedBedHours(Math.round(totalOccupiedBedHours * 100.0) / 100.0)
                .totalAvailableBedHours(Math.round(totalAvailableBedHours * 100.0) / 100.0)
                .occupancyRatePercentage(occupancyRate)
                .admissionsCount(admissionsCount)
                .dischargesCount(dischargesCount)
                .transfersCount(transfersCount)
                .averageLengthOfStayHours(Math.round(alosHours * 100.0) / 100.0)
                .wardUtilizationBedHours(wardUtilization)
                .bedTypeUtilizationBedHours(bedTypeUtilization)
                .dailyTimeline(dailyTimeline)
                .dataCompletenessStatus(completenessStatus)
                .completenessNotice(completenessNotice)
                .generatedAt(Instant.now())
                .build();
    }

    private double calculateBedOccupiedHoursInWindow(List<BedOccupancyEvent> events, Bed bed, Instant windowStart, Instant windowEnd) {
        if (events.isEmpty()) {
            // If no events recorded but bed is currently OCCUPIED, consider occupancy starting from lastStateChangedAt or windowStart
            if ("OCCUPIED".equalsIgnoreCase(bed.getStatus())) {
                Instant occStart = bed.getLastStateChangedAt() != null ? bed.getLastStateChangedAt() : windowStart;
                return calculateOverlapHours(occStart, Instant.now(), windowStart, windowEnd);
            }
            return 0.0;
        }

        double totalHours = 0.0;
        Instant currentOccupiedStart = null;

        for (BedOccupancyEvent event : events) {
            String type = event.getEventType();
            if ("BED_OCCUPIED".equals(type) || "ADMISSION_STARTED".equals(type)) {
                if (currentOccupiedStart == null) {
                    currentOccupiedStart = event.getOccurredAt();
                }
            } else if ("BED_VACATED".equals(type) || "DISCHARGE_COMPLETED".equals(type)) {
                if (currentOccupiedStart != null) {
                    totalHours += calculateOverlapHours(currentOccupiedStart, event.getOccurredAt(), windowStart, windowEnd);
                    currentOccupiedStart = null;
                }
            }
        }

        // Ongoing open-ended occupancy
        if (currentOccupiedStart != null) {
            totalHours += calculateOverlapHours(currentOccupiedStart, Instant.now(), windowStart, windowEnd);
        }

        return totalHours;
    }

    private double calculateOverlapHours(Instant occStart, Instant occEnd, Instant winStart, Instant winEnd) {
        if (occStart == null) return 0.0;
        Instant effectiveEnd = occEnd != null ? occEnd : Instant.now();

        // Check for non-overlap
        if (!occStart.isBefore(winEnd) || !effectiveEnd.isAfter(winStart)) {
            return 0.0;
        }

        Instant boundedStart = occStart.isBefore(winStart) ? winStart : occStart;
        Instant boundedEnd = effectiveEnd.isAfter(winEnd) ? winEnd : effectiveEnd;

        if (boundedStart.isBefore(boundedEnd)) {
            return Duration.between(boundedStart, boundedEnd).toMinutes() / 60.0;
        }
        return 0.0;
    }

    private double calculateAverageLengthOfStay(Instant start, Instant end) {
        List<BedBooking> bookings = bedBookingRepository.findByAdmittedAtBetween(start, end);
        if (bookings.isEmpty()) {
            return 0.0;
        }

        double totalCompletedStayHours = 0.0;
        long completedStays = 0;

        for (BedBooking b : bookings) {
            // Strictly compute ALOS over completed stays with authoritative dischargedAt
            if (b.getAdmittedAt() != null && b.getDischargedAt() != null) {
                totalCompletedStayHours += Duration.between(b.getAdmittedAt(), b.getDischargedAt()).toMinutes() / 60.0;
                completedStays++;
            }
        }

        return completedStays > 0 ? (totalCompletedStayHours / completedStays) : 0.0;
    }

    private List<DailyOccupancyMetricDTO> generateDailyTimeline(Instant start, Instant end, List<Bed> eligibleBeds, ZoneId zone) {
        List<DailyOccupancyMetricDTO> metrics = new ArrayList<>();
        LocalDate cur = start.atZone(zone).toLocalDate();
        LocalDate endDay = end.atZone(zone).toLocalDate();

        while (!cur.isAfter(endDay)) {
            ZonedDateTime dayStart = cur.atStartOfDay(zone);
            ZonedDateTime dayEnd = dayStart.plusDays(1);
            Instant dStart = dayStart.toInstant().isBefore(start) ? start : dayStart.toInstant();
            Instant dEnd = dayEnd.toInstant().isAfter(end) ? end : dayEnd.toInstant();

            if (dStart.isBefore(dEnd)) {
                double dayHours = Duration.between(dStart, dEnd).toMinutes() / 60.0;
                double availHours = eligibleBeds.size() * dayHours;

                double occHours = 0.0;
                for (Bed bed : eligibleBeds) {
                    List<BedOccupancyEvent> bedEvents = eventRepository.findByBedIdOrderByOccurredAtAsc(bed.getId());
                    occHours += calculateBedOccupiedHoursInWindow(bedEvents, bed, dStart, dEnd);
                }

                double rate = availHours > 0 ? Math.min(100.0, (occHours / availHours) * 100.0) : 0.0;

                List<BedOccupancyEvent> dayEvents = eventRepository.findByOccurredAtBetweenOrderByOccurredAtAsc(dStart, dEnd);
                long admits = dayEvents.stream().filter(e -> "BED_OCCUPIED".equals(e.getEventType()) && (e.getSequenceNumber() == null || e.getSequenceNumber() <= 1)).count();
                long dischs = dayEvents.stream().filter(e -> "DISCHARGE_COMPLETED".equals(e.getEventType())).count();
                long trans = dayEvents.stream().filter(e -> "TRANSFER_COMPLETED".equals(e.getEventType())).count();

                metrics.add(DailyOccupancyMetricDTO.builder()
                        .date(cur.toString())
                        .occupiedBedHours(Math.round(occHours * 100.0) / 100.0)
                        .availableBedHours(Math.round(availHours * 100.0) / 100.0)
                        .occupancyRatePercentage(Math.round(rate * 100.0) / 100.0)
                        .admissions(admits)
                        .discharges(dischs)
                        .transfers(trans)
                        .build());
            }

            cur = cur.plusDays(1);
        }

        return metrics;
    }

    private ZoneId resolveZone(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.isBlank()) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
        try {
            return ZoneId.of(timeZoneId.trim());
        } catch (Exception ex) {
            log.warn("Invalid time zone {}, falling back to {}", timeZoneId, DEFAULT_TIMEZONE);
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
    }
}
