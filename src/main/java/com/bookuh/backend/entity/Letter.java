package com.bookuh.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "letters",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "client_letter_id"}),
                @UniqueConstraint(columnNames = {"bookshelf_item_id", "character_id", "pending_slot"})
        },
        indexes = @Index(name = "idx_letters_status_accepted", columnList = "status, accepted_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Letter {

    public static final String PROCESSING = "PROCESSING";
    public static final String FAILED = "FAILED";
    public static final String PUBLISHED = "PUBLISHED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookshelf_item_id", nullable = false)
    private BookshelfItem bookshelfItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private BookCharacter character;

    @Column(name = "client_letter_id", nullable = false, updatable = false,
            columnDefinition = "VARCHAR(128) COLLATE utf8mb4_bin")
    private String clientLetterId;

    @Column(name = "progress_stage", nullable = false, updatable = false, length = 12)
    private String progressStage;

    @Column(name = "progress_scene", updatable = false, columnDefinition = "TEXT")
    private String progressScene;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "TEXT")
    private String reply;

    @Column(nullable = false, length = 12)
    private String status;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private LocalDateTime acceptedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "kept_at")
    private LocalDateTime keptAt;

    @Column(name = "pending_slot", columnDefinition = "TINYINT UNSIGNED")
    private Byte pendingSlot;

    public Letter(BookshelfItem bookshelfItem, BookCharacter character,
                  String clientLetterId, String message, LocalDateTime acceptedAt) {
        this.user = bookshelfItem.getUser();
        this.bookshelfItem = bookshelfItem;
        this.character = character;
        this.clientLetterId = clientLetterId;
        this.progressStage = bookshelfItem.getProgressStage();
        this.progressScene = bookshelfItem.getProgressScene();
        this.message = message;
        this.acceptedAt = acceptedAt;
        this.status = PROCESSING;
        this.pendingSlot = 1;
    }

    public boolean isPublished() {
        return PUBLISHED.equals(status);
    }

    public boolean isFailed() {
        return FAILED.equals(status);
    }

    public boolean isAwaitingReply() {
        return PROCESSING.equals(status) && reply == null;
    }

    public void retry() {
        this.status = PROCESSING;
    }

    public void prepareReply(String reply) {
        this.reply = reply;
    }

    public void failReply() {
        this.status = FAILED;
    }

    public void markRead(LocalDateTime now) {
        if (this.readAt == null) {
            this.readAt = now;
        }
    }

    public void keep(LocalDateTime now) {
        if (this.keptAt == null) {
            this.keptAt = now;
        }
    }
}
