package com.bookuh.backend.repository;

import com.bookuh.backend.entity.BookCharacter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookCharacterRepository extends JpaRepository<BookCharacter, Long> {

    List<BookCharacter> findByBookIsbn13OrderByDisplayOrderAsc(String isbn13);

    Optional<BookCharacter> findByIdAndBookIsbn13(Long id, String isbn13);
}
