package com.bookuh.backend.service;

import com.bookuh.backend.dto.BookSearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Service
public class BookSearchService {

    //구글 북스에 요청을 보낼 도구
    private final RestClient restClient = RestClient.create("https://www.googleapis.com");

    // 설정 파일에서 받아올 Google Books API 키
    private final String apiKey;

    public BookSearchService(@Value("${google.books.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }
    public List<BookSearchResponse> search(String query) {

        //구글 북스에 검색어를 보내고 응답(json)을 일단 통째로 받는다
        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/books/v1/volumes")
                        .queryParam("q", query)
                        .queryParam("maxResults", 10)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        List<BookSearchResponse> results = new ArrayList<>();

        //응답 속 책 목록을 한 권씩 꺼내 살펴본다
        for (JsonNode item : response.path("items")) {
            JsonNode info = item.path("volumeInfo");

            //ISBN-13을 찾고 없으면 버린다
            String isbn13 = null;
            for (JsonNode id : info.path("industryIdentifiers")) {
                if (id.path("type").asText().equals("ISBN_13")) {
                    isbn13 = id.path("identifier").asText();
                }
            }
            if (isbn13 == null) {
                continue; //다음 책으로 넘어가기
            }

            //저자가 여러 명이면 쉼표로 한 문장으로 만들기
            List<String> authors = new ArrayList<>();
            for (JsonNode a : info.path("authors")) {
                authors.add(a.asText());
            }
            String author = String.join(", ", authors);

            //만들어 둔 dto에 담는다.
            results.add(new BookSearchResponse(
                    isbn13,
                    info.path("title").asText(""),
                    author,
                    info.path("publisher").asText(""),
                    info.path("publishedDate").asText(""),
                    info.path("imageLinks").path("thumbnail").asText("")
            ));
        }
        return results;
    }
}
