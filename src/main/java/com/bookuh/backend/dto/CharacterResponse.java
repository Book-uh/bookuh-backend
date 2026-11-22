package com.bookuh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class CharacterResponse {
    private final String status;
    private final List<Item> characters;

    @Getter
    @AllArgsConstructor
    public static class Item {
        private final String source;
        private final String characterId;
        private final String name;
        private final String intro;
        private final int requiredReplies;
        private final long replyCount;
        private final boolean chatUnlocked;
        private final String activeLetterId;
        private final String latestUnreadLetterId;
    }
}
