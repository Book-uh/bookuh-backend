package com.bookuh.backend.service;

import com.bookuh.backend.dto.BookSearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class BookSearchService {

    private static final String ISBN_13 = "ISBN_13";
    private static final int MAX_RESULTS = 10;

    //구글 북스에 요청을 보낼 도구
    private final RestClient restClient = RestClient.create("https://www.googleapis.com");

    // 설정 파일에서 받아올 Google Books API 키
    private final String apiKey;

    public BookSearchService(@Value("${google.books.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    public List<BookSearchResponse> search(String query) {
        //구글 북스에 검색어를 보내고 응답(json)을 일단 통째로 받는다
        JsonNode response = requestVolumes(query);
        if (response == null) {
            return List.of();
        }

        List<BookSearchResponse> results = new ArrayList<>();

        //응답 속 책 목록을 한 권씩 꺼내 살펴본다
        for (JsonNode item : response.path("items")) {
            //ISBN-13을 찾고 없으면 버린다
            Optional<BookSearchResponse> book = toSearchResponse(item);
            if (book.isEmpty()) {
                continue; //다음 책으로 넘어가기
            }

            //만들어 둔 dto에 담는다.
            results.add(book.get());
        }
        return results;
    }

    // 구글 책 ID로 책 한 권의 정보를 가져온다
    public Optional<BookSearchResponse> findVolume(String googleVolumeId) {
        JsonNode volume = requestVolume(googleVolumeId);
        return volume == null ? Optional.empty() : toSearchResponse(volume);
    }

    // 구글 북스에서 검색어에 맞는 책 목록을 받아 온다
    private JsonNode requestVolumes(String query) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/books/v1/volumes")
                            .queryParam("q", query)
                            .queryParam("maxResults", MAX_RESULTS)
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "책 검색을 할 수 없습니다.", e);
        }
    }

    // 구글 북스에서 책 한 권을 받아 온다. 없는 책이면 비워 둔다
    private JsonNode requestVolume(String googleVolumeId) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/books/v1/volumes/{volumeId}")
                            .queryParam("key", apiKey)
                            .build(googleVolumeId))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "책 정보를 불러올 수 없습니다.", e);
        }
    }

    // 구글이 준 책 한 권을 검색 결과 모양으로 바꾼다. ISBN-13이 없으면 비워 둔다
    private Optional<BookSearchResponse> toSearchResponse(JsonNode item) {
        JsonNode info = item.path("volumeInfo");

        //저자가 여러 명이면 쉼표로 한 문장으로 만들기
        List<String> authors = new ArrayList<>();
        for (JsonNode name : info.path("authors")) {
            authors.add(name.asText());
        }
        String author = String.join(", ", authors);

        return findIsbn13(info).map(isbn13 -> new BookSearchResponse(
                item.path("id").asText(""),
                isbn13,
                info.path("title").asText(""),
                author,
                authors,
                info.path("publisher").asText(""),
                info.path("publishedDate").asText(""),
                info.path("imageLinks").path("thumbnail").asText("")
        ));
    }

    // 책 식별 번호 중 ISBN-13을 찾는다
    private Optional<String> findIsbn13(JsonNode info) {
        for (JsonNode identifier : info.path("industryIdentifiers")) {
            if (ISBN_13.equals(identifier.path("type").asText())) {
                return Optional.of(identifier.path("identifier").asText());
            }
        }
        return Optional.empty();
    }
}
