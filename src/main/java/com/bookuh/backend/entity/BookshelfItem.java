package com.bookuh.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

@Entity
@Table(name = "bookshelf_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "isbn"}))
@Getter
// JPA가 책장 칸을 불러올 때 사용한다
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookshelfItem {

    private static final String NOT_STARTED = "NOT_STARTED";
    private static final String READING = "READING";
    private static final String FINISHED = "FINISHED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    // 이 책장 칸이 누구 것인지 (users 테이블과 연결)
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 어떤 책인지 (book_editions 테이블과 연결)
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "isbn", nullable = false)
    private Book book;

    @Column(name = "shelved_at", nullable = false, updatable = false)
    private LocalDateTime shelvedAt;

    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    @Column(name = "reading_status", nullable = false, length = 16)
    private String readingStatus;

    @Column(name = "progress_version", nullable = false, columnDefinition = "INT UNSIGNED DEFAULT 0")
    private int progressVersion;

    @Column(name = "progress_stage", length = 12)
    private String progressStage;

    @Column(name = "progress_scene", columnDefinition = "TEXT")
    private String progressScene;

    @Column(name = "progress_updated_at")
    private LocalDateTime progressUpdatedAt;

    @Column(name = "finished_on")
    private LocalDate finishedOn;

    // 새 책장 칸을 만든다
    public BookshelfItem(User user, Book book) {
        this.user = user;
        this.book = book;
        this.shelvedAt = LocalDateTime.now(ZoneOffset.UTC);
        this.readingStatus = NOT_STARTED;
    }

    // 기록이 참조하는 책장 칸을 유지하고 목록에서만 뺀다
    public void removeFromShelf() {
        if (removedAt == null) {
            removedAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    public void restoreToShelf() {
        removedAt = null;
    }

    public void changeReadingStatus(String status) {
        this.readingStatus = status;
        this.finishedOn = null;
    }

    public boolean matchesProgress(String stage, String scene) {
        return Objects.equals(progressStage, stage) && Objects.equals(progressScene, scene);
    }

    public void updateProgress(String stage, String scene, Instant now, ZoneId timeZone) {
        if (!matchesProgress(stage, scene)) {
            this.progressStage = stage;
            this.progressScene = scene;
            this.progressVersion++;
            this.progressUpdatedAt = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        }

        if (FINISHED.equals(stage)) {
            this.readingStatus = FINISHED;
            if (this.finishedOn == null) {
                this.finishedOn = now.atZone(timeZone).toLocalDate();
            }
        } else if (NOT_STARTED.equals(this.readingStatus)
                && ("START".equals(stage) || "SCENE".equals(stage))) {
            this.readingStatus = READING;
        }
    }
}
