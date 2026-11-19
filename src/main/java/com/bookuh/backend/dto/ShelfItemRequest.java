package com.bookuh.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ShelfItemRequest {

    private String title;
    private List<String> authors;
    private String publisher;
    private Short publishedYear;
    private String coverUrl;
}
