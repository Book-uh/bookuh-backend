package com.bookuh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public class ReadingStatsResponse {
    private final String period;
    private final LocalDate startDate;
    private final LocalDate endDateExclusive;
    private final Long durationMinutes;
    private final Long pagesRead;
    private final long readingDayCount;
    private final long finishedBookCount;
    private final long dayMinutes;
    private final long nightMinutes;
    private final List<Bucket> buckets;

    @Getter
    @AllArgsConstructor
    public static class Bucket {
        private final LocalDate startDate;
        private final LocalDate endDateExclusive;
        private final Long durationMinutes;
    }
}
