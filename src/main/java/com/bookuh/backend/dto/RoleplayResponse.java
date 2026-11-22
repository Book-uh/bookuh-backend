package com.bookuh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@AllArgsConstructor
public class RoleplayResponse {
    private final String turnId;
    private final String reply;
    private final boolean includeInFutureContext;

    @Getter
    @AllArgsConstructor
    public static class History {
        private final List<Item> items;
    }

    @Getter
    @AllArgsConstructor
    public static class Item {
        private final String turnId;
        private final String status;
        private final String message;
        private final String reply;
        private final Instant createdAt;
    }
}
