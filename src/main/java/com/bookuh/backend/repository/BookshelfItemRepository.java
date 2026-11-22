package com.bookuh.backend.repository;

import com.bookuh.backend.entity.Book;
import com.bookuh.backend.entity.BookshelfItem;
import com.bookuh.backend.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookshelfItemRepository extends JpaRepository<BookshelfItem, Long> {

    // 이 사용자의 책장을 최근 담은 순으로 가져온다
    @EntityGraph(attributePaths = "book")
    List<BookshelfItem> findByUserOrderByShelvedAtDescBookIsbn13Asc(User user);

    // 같은 사용자가 같은 책을 담은 칸을 찾는다
    Optional<BookshelfItem> findByUserAndBook(User user, Book book);
    Optional<BookshelfItem> findByUserAndBookIsbn13(User user, String isbn13);

    // 이 사용자가 책장에 담은 책의 ISBN을 모두 가져온다
    @Query("select i.book.isbn13 from BookshelfItem i where i.user = :user")
    List<String> findIsbnsByUser(@Param("user") User user);
}
