package com.bookuh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@AllArgsConstructor
public class LetterResponse {

    private final String letterId;
    private final String isbn;
    private final String characterId;
    private final String characterName;
    private final String progressStage;
    private final String progressScene;
    private final String status;
    private final Instant acceptedAt;
    private final Instant publishedAt;
    private final Instant readAt;
    private final boolean kept;
    private final String message;
    private final String reply;

    @Getter
    @AllArgsConstructor
    public static class Inbox {
        private final List<LetterResponse> items;
    }
}
