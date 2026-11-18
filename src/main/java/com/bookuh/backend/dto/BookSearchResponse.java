package com.bookuh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BookSearchResponse {

    private String isbn13; //13자리 기준으로 할 것
    private String title;
    private String author;
    private String publisher;
    private String publishedDate;
    private String thumbnailUrl;
}
