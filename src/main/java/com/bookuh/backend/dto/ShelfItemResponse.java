package com.bookuh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class ShelfItemResponse {
    private String isbn;
    private String title;
    private List<String> authors;
    private String publisher;
    private String coverUrl;
    private String readingStatus;
    private LocalDateTime shelvedAt;
}
