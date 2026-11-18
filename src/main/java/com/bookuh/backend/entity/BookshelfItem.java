package com.bookuh.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "bookshelf_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "isbn"}))
@Getter
// JPA가 책장 칸을 불러올 때 사용한다
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookshelfItem {

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

    @Column(name = "reading_status", nullable = false, length = 16)
    private String readingStatus;

    // 새 책장 칸을 만든다
    public BookshelfItem(User user, Book book) {
        this.user = user;
        this.book = book;
        this.shelvedAt = LocalDateTime.now(ZoneOffset.UTC);
        this.readingStatus = "NOT_STARTED";
    }
}
