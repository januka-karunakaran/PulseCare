package com.hospital.backend.service;

import com.hospital.backend.dto.bed.HistoricalOccupancyReportDTO;

import java.time.Instant;
import java.time.LocalDate;

public interface BedOccupancyAnalyticsService {

    HistoricalOccupancyReportDTO getHistoricalOccupancy(Instant start, Instant end, String wardId, String bedType, String timeZoneId);

    HistoricalOccupancyReportDTO getHistoricalOccupancyForPreset(String preset, String wardId, String bedType, String timeZoneId);

    HistoricalOccupancyReportDTO getDailyOccupancy(LocalDate date, String wardId, String bedType, String timeZoneId);
}
