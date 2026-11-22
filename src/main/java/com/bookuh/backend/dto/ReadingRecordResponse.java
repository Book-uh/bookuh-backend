package com.bookuh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class ReadingRecordResponse {
    private final String recordId;
    private final String isbn;
    private final String title;
    private final String readingDate;
    private final String timeBand;
    private final Integer durationMinutes;
    private final Integer pagesRead;
    private final Instant createdAt;
}
