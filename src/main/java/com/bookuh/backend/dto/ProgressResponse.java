package com.bookuh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;

//바깥에는 책의 독서 상태와 완독일을 안쪽 Progress에는 현재 읽은 시점을 담는다.
@Getter
@AllArgsConstructor
public class ProgressResponse {
    private final Progress progress;
    private final String readingStatus;
    private final LocalDate finishedOn;

    @Getter
    @AllArgsConstructor
    public static class Progress {
        private final int progressVersion;
        private final String stage;
        private final String scene;
        private final Instant updatedAt;
    }
}