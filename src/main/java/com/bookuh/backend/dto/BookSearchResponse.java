package com.bookuh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class BookSearchResponse {

    private List<Item> items;
    private int page;
    private boolean hasMore;

    // 검색 결과의 책 한 권
    @Getter
    @AllArgsConstructor
    public static class Item {

        private String isbn; //13자리 기준으로 할 것
        private String googleVolumeId;
        private String title;
        private List<String> authors;
        private String publisher;
        private Short publishedYear;
        private String coverUrl;
        private String shelfState;
    }
}
