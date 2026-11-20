package com.bookuh.backend.repository;

import com.bookuh.backend.entity.Book;
import com.bookuh.backend.entity.BookshelfItem;
import com.bookuh.backend.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookshelfItemRepository extends JpaRepository<BookshelfItem, Long> {

    // 이 사용자의 책장을 최근 담은 순으로 가져온다
    @EntityGraph(attributePaths = "book")
    List<BookshelfItem> findByUserOrderByShelvedAtDesc(User user);

    // 같은 사용자가 같은 책을 담은 칸을 찾는다
    Optional<BookshelfItem> findByUserAndBook(User user, Book book);
}
