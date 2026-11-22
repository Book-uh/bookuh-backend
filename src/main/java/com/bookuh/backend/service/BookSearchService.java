package com.bookuh.backend.service;

import com.bookuh.backend.dto.BookSearchResponse;
import com.bookuh.backend.entity.Book;
import com.bookuh.backend.entity.User;
import com.bookuh.backend.repository.BookshelfItemRepository;
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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BookSearchService {

    private static final String ISBN_13 = "ISBN_13";
    private static final String NOT_ADDED = "NOT_ADDED";
    private static final String ON_SHELF = "ON_SHELF";
    private static final Pattern YEAR_PREFIX = Pattern.compile("^(\\d{4})");

    //구글 북스에 요청을 보낼 도구
    private final RestClient restClient = RestClient.create("https://www.googleapis.com");

    // 설정 파일에서 받아올 Google Books API 키
    private final String apiKey;
    private final BookshelfItemRepository bookshelfItemRepository;

    public BookSearchService(@Value("${google.books.api-key}") String apiKey,
                             BookshelfItemRepository bookshelfItemRepository) {
        this.apiKey = apiKey;
        this.bookshelfItemRepository = bookshelfItemRepository;
    }

    // 검색 결과를 한 페이지씩 돌려주고, 이미 책장에 담은 책을 표시한다
    public BookSearchResponse search(User user, String query, int page, int size) {
        String keyword = query.strip();
        int startIndex = (page - 1) * size;

        //구글 북스에 검색어를 보내고 응답(json)을 일단 통째로 받는다
        JsonNode response = requestVolumes(keyword, startIndex, size);
        if (response == null) {
            return new BookSearchResponse(List.of(), page, false);
        }

        Set<String> shelvedIsbns = Set.copyOf(bookshelfItemRepository.findIsbnsByUser(user));
        List<BookSearchResponse.Item> items = new ArrayList<>();

        //응답 속 책 목록을 한 권씩 꺼내 살펴본다
        for (JsonNode item : response.path("items")) {
            //ISBN-13을 찾고 없으면 버린다
            Optional<BookSearchResponse.Item> book = toSearchItem(item, shelvedIsbns);
            if (book.isEmpty()) {
                continue; //다음 책으로 넘어가기
            }

            //만들어 둔 dto에 담는다.
            items.add(book.get());
        }

        // 다음 페이지를 미리 받아 보고, 담을 수 있는 책이 있을 때만 더 보기를 보여 준다
        boolean hasMore = hasAddableBook(requestVolumes(keyword, startIndex + size, size));
        return new BookSearchResponse(items, page, hasMore);
    }

    // 구글 책 ID로 책 한 권을 찾아 공용 책으로 만든다
    public Optional<Book> findBook(String googleVolumeId) {
        JsonNode volume = requestVolume(googleVolumeId);
        return volume == null ? Optional.empty() : toBook(volume);
    }

    // 구글 북스에서 검색어에 맞는 책 목록을 받아 온다
    private JsonNode requestVolumes(String query, int startIndex, int maxResults) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/books/v1/volumes")
                            .queryParam("q", query)
                            .queryParam("startIndex", startIndex)
                            .queryParam("maxResults", maxResults)
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

    // 구글이 준 책 한 권을 검색 결과 한 줄로 바꾼다. ISBN-13이 없으면 비워 둔다
    private Optional<BookSearchResponse.Item> toSearchItem(JsonNode volume, Set<String> shelvedIsbns) {
        String googleVolumeId = volume.path("id").asText("");
        return toBook(volume).map(book -> new BookSearchResponse.Item(
                book.getIsbn13(),
                googleVolumeId,
                book.getTitle(),
                book.getAuthors(),
                book.getPublisher(),
                book.getPublishedYear(),
                book.getCoverUrl(),
                shelvedIsbns.contains(book.getIsbn13()) ? ON_SHELF : NOT_ADDED
        ));
    }

    // 구글이 준 책 한 권을 공용 책으로 바꾼다. ISBN-13이 없으면 비워 둔다
    private Optional<Book> toBook(JsonNode volume) {
        JsonNode info = volume.path("volumeInfo");

        List<String> authors = new ArrayList<>();
        for (JsonNode name : info.path("authors")) {
            authors.add(name.asText());
        }

        return findIsbn13(info).map(isbn13 -> new Book(
                isbn13,
                info.path("title").asText(""),
                authors,
                blankToNull(info.path("publisher").asText("")),
                toPublishedYear(info.path("publishedDate").asText("")),
                blankToNull(info.path("imageLinks").path("thumbnail").asText(""))
        ));
    }

    // 구글 응답에 담을 수 있는 책(ISBN-13이 있는 책)이 하나라도 있는지 본다
    private boolean hasAddableBook(JsonNode response) {
        if (response == null) {
            return false;
        }
        for (JsonNode volume : response.path("items")) {
            if (findIsbn13(volume.path("volumeInfo")).isPresent()) {
                return true;
            }
        }
        return false;
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

    // 출간일 앞의 네 자리를 출간 연도로 쓴다
    private Short toPublishedYear(String publishedDate) {
        Matcher matcher = YEAR_PREFIX.matcher(publishedDate);
        return matcher.find() ? Short.valueOf(matcher.group(1)) : null;
    }

    // 빈 글자는 값이 없는 것으로 바꾼다
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
