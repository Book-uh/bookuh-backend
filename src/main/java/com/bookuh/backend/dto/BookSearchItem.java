package com.bookuh.backend.dto;

public record BookSearchItem(
        String isbn13, //13자리 기준으로 할 것
        String title,
        String author,
        String publisher,
        String publishedDate,
        String thumbnailUrl
) {
}
