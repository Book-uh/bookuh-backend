package com.bookuh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@AllArgsConstructor
public class ManagerMessagesResponse {

    private final List<Item> items;
    private final String nextCursor;

    @Getter
    @AllArgsConstructor
    public static class Item {
        private final String messageId;
        private final String role;
        private final String isbn;
        private final String text;
        private final Instant createdAt;
    }
}