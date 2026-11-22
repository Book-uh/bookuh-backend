package com.bookuh.backend.repository;

import com.bookuh.backend.entity.Book;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Book> findLockedByIsbn13(String isbn13);
}
