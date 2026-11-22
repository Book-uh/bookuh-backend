package com.bookuh.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "chat_turns",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "client_turn_id"}),
        indexes = @Index(name = "idx_chat_context",
                columnList = "user_id,bookshelf_item_id,character_id,progress_version,id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatTurn {

    public static final String PROCESSING = "PROCESSING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";

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

    @Column(name = "client_turn_id", nullable = false, length = 128,
            columnDefinition = "VARCHAR(128) COLLATE utf8mb4_bin")
    private String clientTurnId;

    @Column(name = "progress_version", nullable = false, columnDefinition = "INT UNSIGNED")
    private int progressVersion;

    @Column(name = "progress_stage", nullable = false, length = 12)
    private String progressStage;

    @Column(name = "progress_scene", columnDefinition = "TEXT")
    private String progressScene;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "TEXT")
    private String reply;

    @Column(nullable = false, length = 12)
    private String status;

    @Column(name = "include_in_future_context", nullable = false)
    private boolean includeInFutureContext;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ChatTurn(User user, BookshelfItem shelf, BookCharacter character, String turnId, String message) {
        this.user = user;
        this.bookshelfItem = shelf;
        this.character = character;
        this.clientTurnId = turnId;
        this.message = message;
        this.progressVersion = shelf.getProgressVersion();
        this.progressStage = shelf.getProgressStage();
        this.progressScene = shelf.getProgressScene();
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        start(createdAt);
    }

    public void start(LocalDateTime now) {
        this.status = PROCESSING;
        this.updatedAt = now;
    }

    public void succeed(String reply, boolean includeInFutureContext) {
        this.reply = reply;
        this.includeInFutureContext = includeInFutureContext;
        this.status = SUCCEEDED;
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void fail() {
        this.status = FAILED;
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
