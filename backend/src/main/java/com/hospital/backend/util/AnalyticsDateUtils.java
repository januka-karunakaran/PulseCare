package com.hospital.backend.util;

import com.hospital.backend.enums.ReportingDateRange;

import java.time.*;
import java.time.temporal.TemporalAdjusters;

public class AnalyticsDateUtils {

    public record DateBoundaries(Instant startInclusive, Instant endExclusive, LocalDate startDate, LocalDate endDate, ZoneId zoneId) {}

    public static DateBoundaries resolveBoundaries(ReportingDateRange dateRange, LocalDate customStart, LocalDate customEnd, String timezoneStr) {
        ZoneId zoneId;
        try {
            zoneId = (timezoneStr != null && !timezoneStr.isBlank()) ? ZoneId.of(timezoneStr) : ZoneId.of("Asia/Colombo");
        } catch (Exception e) {
            zoneId = ZoneId.of("Asia/Colombo");
        }

        ZonedDateTime now = ZonedDateTime.now(zoneId);
        LocalDate today = now.toLocalDate();

        LocalDate start;
        LocalDate end;

        if (dateRange == null) {
            dateRange = ReportingDateRange.LAST_30_DAYS;
        }

        switch (dateRange) {
            case TODAY -> {
                start = today;
                end = today;
            }
            case YESTERDAY -> {
                start = today.minusDays(1);
                end = today.minusDays(1);
            }
            case LAST_7_DAYS -> {
                start = today.minusDays(6);
                end = today;
            }
            case LAST_30_DAYS -> {
                start = today.minusDays(29);
                end = today;
            }
            case THIS_MONTH -> {
                start = today.with(TemporalAdjusters.firstDayOfMonth());
                end = today;
            }
            case LAST_MONTH -> {
                LocalDate lastMonthDay = today.minusMonths(1);
                start = lastMonthDay.with(TemporalAdjusters.firstDayOfMonth());
                end = lastMonthDay.with(TemporalAdjusters.lastDayOfMonth());
            }
            case CUSTOM_RANGE -> {
                start = (customStart != null) ? customStart : today.minusDays(29);
                end = (customEnd != null) ? customEnd : today;
                if (start.isAfter(end)) {
                    LocalDate tmp = start;
                    start = end;
                    end = tmp;
                }
            }
            default -> {
                start = today.minusDays(29);
                end = today;
            }
        }

        Instant startInclusive = start.atStartOfDay(zoneId).toInstant();
        Instant endExclusive = end.plusDays(1).atStartOfDay(zoneId).toInstant();

        return new DateBoundaries(startInclusive, endExclusive, start, end, zoneId);
    }
}
