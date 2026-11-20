package com.bookuh.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "manager_messages",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"api_request_id", "message_order"}),
        indexes = @Index(name = "idx_manager_messages_user_created",
                columnList = "user_id, created_at, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ManagerMessage {

    private static final String ROLE_USER = "USER";
    private static final String ROLE_ASSISTANT = "ASSISTANT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bookshelf_item_id")
    private BookshelfItem bookshelfItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_request_id")
    private ApiRequest apiRequest;

    @Column(name = "message_order", nullable = false,
            columnDefinition = "TINYINT UNSIGNED")
    private Short messageOrder;

    @Column(name = "role", nullable = false, length = 12)
    private String role;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 사용자 또는 북어가 보낸 말풍선을 만든다
    public ManagerMessage(User user, BookshelfItem bookshelfItem,
                          ApiRequest apiRequest, Short messageOrder,
                          String role, String body) {
        this.user = user;
        this.bookshelfItem = bookshelfItem;
        this.apiRequest = apiRequest;
        this.messageOrder = messageOrder;
        this.role = role;
        this.body = body;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    // 사용자가 보낸 말풍선을 만든다
    public static ManagerMessage ofUser(User user, BookshelfItem bookshelfItem,
                                        ApiRequest apiRequest, String body) {
        return new ManagerMessage(user, bookshelfItem, apiRequest, (short) 1, ROLE_USER, body);
    }

    // 북어가 보낸 말풍선을 만든다
    public static ManagerMessage ofAssistant(User user, BookshelfItem bookshelfItem,
                                             ApiRequest apiRequest, String body) {
        return new ManagerMessage(user, bookshelfItem, apiRequest, (short) 2, ROLE_ASSISTANT, body);
    }
}
