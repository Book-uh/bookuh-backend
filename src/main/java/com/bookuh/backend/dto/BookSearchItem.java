package com.bookuh.backend.dto;

public class BookSearchItem {

    private String isbn13; //13자리 기준으로 할 것
    private String title;
    private String author;
    private String publisher;
    private String publishedDate;
    private String thumbnailUrl;

    public BookSearchItem(
            String isbn13,
            String title,
            String author,
            String publisher,
            String publishedDate,
            String thumbnailUrl
    ) {
        this.isbn13 = isbn13;
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.publishedDate = publishedDate;
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getIsbn13() {
        return isbn13;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getPublisher() {
        return publisher;
    }

    public String getPublishedDate() {
        return publishedDate;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }
}