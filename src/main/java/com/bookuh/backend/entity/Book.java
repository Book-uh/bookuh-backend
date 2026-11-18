package com.bookuh.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Entity
@Table(name = "book_editions")
@Getter
// JPA가 책을 불러올 때 사용한다
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Book {

    @Id
    @Column(name = "isbn", nullable = false, columnDefinition = "CHAR(13)")
    private String isbn13;

    @Column(name = "google_volume_id", length = 128)
    private String googleVolumeId;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "authors", nullable = false, columnDefinition = "JSON")
    private List<String> authors;

    @Column(name = "publisher", length = 255)
    private String publisher;

    @Column(name = "published_year", columnDefinition = "SMALLINT UNSIGNED")
    private Short publishedYear;

    @Column(name = "cover_url", length = 2048)
    private String coverUrl;

    @Column(name = "character_status", nullable = false, length = 20)
    private String characterStatus;

    @Column(name = "character_status_updated_at", nullable = false)
    private LocalDateTime characterStatusUpdatedAt;

    // 새 책의 정보와 초기 상태를 설정한다
    public Book(String isbn13, String googleVolumeId, String title,
                List<String> authors, String publisher, Short publishedYear,
                String coverUrl) {
        this.isbn13 = isbn13;
        this.googleVolumeId = googleVolumeId;
        this.title = title;
        this.authors = authors;
        this.publisher = publisher;
        this.publishedYear = publishedYear;
        this.coverUrl = coverUrl;
        this.characterStatus = "NOT_STARTED";
        this.characterStatusUpdatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
