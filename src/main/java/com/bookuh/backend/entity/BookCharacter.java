package com.bookuh.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "book_characters",
        uniqueConstraints = @UniqueConstraint(columnNames = {"isbn", "display_order"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookCharacter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "isbn", nullable = false)
    private Book book;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 500)
    private String intro;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String persona;

    @Column(name = "required_replies", nullable = false)
    private int requiredReplies;

    public BookCharacter(Book book, int displayOrder, String name, String intro,
                         String persona, int requiredReplies) {
        this.book = book;
        this.displayOrder = displayOrder;
        this.name = name;
        this.intro = intro;
        this.persona = persona;
        this.requiredReplies = requiredReplies;
    }
}
