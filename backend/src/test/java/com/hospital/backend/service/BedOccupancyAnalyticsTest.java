package com.hospital.backend.service;

import com.hospital.backend.dto.bed.HistoricalOccupancyReportDTO;
import com.hospital.backend.model.Bed;
import com.hospital.backend.model.BedBooking;
import com.hospital.backend.model.BedOccupancyEvent;
import com.hospital.backend.repository.BedBookingRepository;
import com.hospital.backend.repository.BedOccupancyEventRepository;
import com.hospital.backend.repository.BedRepository;
import com.hospital.backend.service.impl.BedOccupancyAnalyticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BedOccupancyAnalyticsTest {

    @Mock
    private BedOccupancyEventRepository eventRepository;

    @Mock
    private BedRepository bedRepository;

    @Mock
    private BedBookingRepository bedBookingRepository;

    private BedOccupancyAnalyticsServiceImpl analyticsService;

    private final ZoneId zone = ZoneId.of("Asia/Colombo");

    @BeforeEach
    void setUp() {
        analyticsService = new BedOccupancyAnalyticsServiceImpl(eventRepository, bedRepository, bedBookingRepository);
    }

    @Test
    @DisplayName("G1: Calculates exact occupied bed-hours across known event intervals")
    void testDailyOccupiedBedHoursCalculation() {
        Instant dayStart = LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant();
        Instant dayEnd = dayStart.plus(24, ChronoUnit.HOURS);

        Bed bed1 = Bed.builder().id("bed-01").bedNumber("B101").type("GENERAL").ward("General Ward").active(true).build();
        when(bedRepository.findAll()).thenReturn(List.of(bed1));

        // Occupied Nov 1 10:00 -> Nov 1 18:00 (8 hours)
        Instant occ = dayStart.plus(10, ChronoUnit.HOURS);
        Instant vac = dayStart.plus(18, ChronoUnit.HOURS);

        BedOccupancyEvent e1 = BedOccupancyEvent.builder().bedId("bed-01").eventType("BED_OCCUPIED").occurredAt(occ).build();
        BedOccupancyEvent e2 = BedOccupancyEvent.builder().bedId("bed-01").eventType("BED_VACATED").occurredAt(vac).build();

        when(eventRepository.findByBedIdOrderByOccurredAtAsc("bed-01")).thenReturn(List.of(e1, e2));
        when(eventRepository.count()).thenReturn(2L);
        when(eventRepository.findAll()).thenReturn(List.of(e1, e2));

        HistoricalOccupancyReportDTO report = analyticsService.getHistoricalOccupancy(dayStart, dayEnd, null, null, "Asia/Colombo");

        assertNotNull(report);
        assertEquals(8.0, report.getTotalOccupiedBedHours(), 0.01);
        assertEquals(24.0, report.getTotalAvailableBedHours(), 0.01);
        assertEquals(33.33, report.getOccupancyRatePercentage(), 0.05);
        assertEquals("COMPLETE", report.getDataCompletenessStatus());
    }

    @Test
    @DisplayName("G4 & G6: Mandatory Test - Patient A admitted, transferred, discharged across day boundaries")
    void testMandatoryScenario_AdmitTransferDischargeIntervals() {
        // Patient A:
        // Admitted Nov 1, 10:00 into Bed 1 (Ward A)
        // Transferred Nov 2, 12:00 into Bed 2 (Ward B)
        // Discharged Nov 3, 15:00
        Instant nov1_10 = LocalDate.of(2026, 11, 1).atStartOfDay(zone).plus(10, ChronoUnit.HOURS).toInstant();
        Instant nov2_12 = LocalDate.of(2026, 11, 2).atStartOfDay(zone).plus(12, ChronoUnit.HOURS).toInstant();
        Instant nov3_15 = LocalDate.of(2026, 11, 3).atStartOfDay(zone).plus(15, ChronoUnit.HOURS).toInstant();

        Bed bed1 = Bed.builder().id("bed-01").bedNumber("B101").type("GENERAL").ward("Ward A").active(true).build();
        Bed bed2 = Bed.builder().id("bed-02").bedNumber("B201").type("ICU").ward("Ward B").active(true).build();
        when(bedRepository.findAll()).thenReturn(List.of(bed1, bed2));

        // Bed 1 events: Occupied Nov 1 10:00 -> Vacated Nov 2 12:00 (26 hours)
        BedOccupancyEvent b1_occ = BedOccupancyEvent.builder().bedId("bed-01").eventType("BED_OCCUPIED").occurredAt(nov1_10).build();
        BedOccupancyEvent b1_vac = BedOccupancyEvent.builder().bedId("bed-01").eventType("BED_VACATED").occurredAt(nov2_12).build();
        when(eventRepository.findByBedIdOrderByOccurredAtAsc("bed-01")).thenReturn(List.of(b1_occ, b1_vac));

        // Bed 2 events: Occupied Nov 2 12:00 -> Vacated Nov 3 15:00 (27 hours)
        BedOccupancyEvent b2_occ = BedOccupancyEvent.builder().bedId("bed-02").eventType("BED_OCCUPIED").occurredAt(nov2_12).build();
        BedOccupancyEvent b2_vac = BedOccupancyEvent.builder().bedId("bed-02").eventType("BED_VACATED").occurredAt(nov3_15).build();
        when(eventRepository.findByBedIdOrderByOccurredAtAsc("bed-02")).thenReturn(List.of(b2_occ, b2_vac));

        when(eventRepository.count()).thenReturn(4L);
        when(eventRepository.findAll()).thenReturn(List.of(b1_occ, b1_vac, b2_occ, b2_vac));

        // Report Window: Nov 1 00:00 to Nov 4 00:00 (72 hours window)
        Instant start = LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant();
        Instant end = LocalDate.of(2026, 11, 4).atStartOfDay(zone).toInstant();

        HistoricalOccupancyReportDTO report = analyticsService.getHistoricalOccupancy(start, end, null, null, "Asia/Colombo");

        assertNotNull(report);
        // Total Occupied = 26 + 27 = 53 hours
        assertEquals(53.0, report.getTotalOccupiedBedHours(), 0.01);
        assertEquals(26.0, report.getWardUtilizationBedHours().get("Ward A"), 0.01);
        assertEquals(27.0, report.getWardUtilizationBedHours().get("Ward B"), 0.01);
        assertEquals(26.0, report.getBedTypeUtilizationBedHours().get("GENERAL"), 0.01);
        assertEquals(27.0, report.getBedTypeUtilizationBedHours().get("ICU"), 0.01);
    }

    @Test
    @DisplayName("G10: Safe zero denominator when hospital has zero registered beds")
    void testZeroDenominatorSafety() {
        Instant start = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant end = Instant.now();

        when(bedRepository.findAll()).thenReturn(Collections.emptyList());
        when(eventRepository.count()).thenReturn(0L);

        HistoricalOccupancyReportDTO report = analyticsService.getHistoricalOccupancy(start, end, null, null, "Asia/Colombo");

        assertNotNull(report);
        assertEquals(0.0, report.getTotalOccupiedBedHours());
        assertEquals(0.0, report.getTotalAvailableBedHours());
        assertEquals(0.0, report.getOccupancyRatePercentage());
        assertEquals("CURRENT_SNAPSHOT_ONLY", report.getDataCompletenessStatus());
    }

    @Test
    @DisplayName("G8: Reports PARTIAL_HISTORY when requested range precedes event logging cutover")
    void testPartialHistoryCompletenessIndicator() {
        Instant eventLoggedAt = Instant.parse("2026-11-20T00:00:00Z");
        Instant requestStart = Instant.parse("2026-11-01T00:00:00Z");
        Instant requestEnd = Instant.parse("2026-11-25T00:00:00Z");

        Bed bed = Bed.builder().id("bed-01").active(true).build();
        when(bedRepository.findAll()).thenReturn(List.of(bed));
        when(eventRepository.count()).thenReturn(1L);

        BedOccupancyEvent e = BedOccupancyEvent.builder().bedId("bed-01").eventType("BED_OCCUPIED").occurredAt(eventLoggedAt).build();
        when(eventRepository.findAll()).thenReturn(List.of(e));

        HistoricalOccupancyReportDTO report = analyticsService.getHistoricalOccupancy(requestStart, requestEnd, null, null, "Asia/Colombo");

        assertEquals("PARTIAL_HISTORY", report.getDataCompletenessStatus());
        assertTrue(report.getCompletenessNotice().contains("commenced at"));
    }
}
