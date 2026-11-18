package com.bookuh.backend.dto;
import java.time.LocalDateTime;
import java.util.List;

public class ShelfItemResponse {
    private String isbn13;
    private String title;
    private List<String> authors;
    private String publisher;
    private String coverUrl;
    private String readingStatus;
    private LocalDateTime shelvedAt;

    public ShelfItemResponse(String isbn13, String title, List<String> authors,
                             String publisher, String coverUrl,
                             String readingStatus, LocalDateTime shelvedAt) {
        this.isbn13 = isbn13;
        this.title = title;
        this.authors = authors;
        this.publisher = publisher;
        this.coverUrl = coverUrl;
        this.readingStatus = readingStatus;
        this.shelvedAt = shelvedAt;
    }

    public String getPublisher() {
        return publisher;
    }

    public String getIsbn13() {
        return isbn13;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getAuthors() {
        return authors;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public String getReadingStatus() {
        return readingStatus;
    }

    public LocalDateTime getShelvedAt() {
        return shelvedAt;
    }
}
