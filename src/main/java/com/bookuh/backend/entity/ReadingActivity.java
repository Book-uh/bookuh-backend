package com.bookuh.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "reading_activities", indexes = @Index(
        name = "idx_reading_activities_bookshelf_date", columnList = "bookshelf_item_id, reading_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadingActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "bookshelf_item_id", nullable = false)
    private BookshelfItem bookshelfItem;

    @Column(name = "reading_date", nullable = false)
    private LocalDate readingDate;

    @Column(name = "time_band", length = 5)
    private String timeBand;

    @Column(name = "duration_minutes", columnDefinition = "INT UNSIGNED")
    private Integer durationMinutes;

    @Column(name = "pages_read", columnDefinition = "INT UNSIGNED")
    private Integer pagesRead;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ReadingActivity(BookshelfItem bookshelfItem, LocalDate readingDate, String timeBand,
                           Integer durationMinutes, Integer pagesRead) {
        this.bookshelfItem = bookshelfItem;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        update(readingDate, timeBand, durationMinutes, pagesRead);
    }

    public void update(LocalDate readingDate, String timeBand, Integer durationMinutes, Integer pagesRead) {
        this.readingDate = readingDate;
        this.timeBand = timeBand;
        this.durationMinutes = durationMinutes;
        this.pagesRead = pagesRead;
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
