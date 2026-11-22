package com.bookuh.backend.repository;

import com.bookuh.backend.entity.BookCharacter;
import com.bookuh.backend.entity.BookshelfItem;
import com.bookuh.backend.entity.Letter;
import com.bookuh.backend.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LetterRepository extends JpaRepository<Letter, Long> {

    Optional<Letter> findByUserAndClientLetterId(User user, String clientLetterId);

    Optional<Letter> findByUserAndId(User user, Long id);

    @EntityGraph(attributePaths = {"character", "character.book"})
    Optional<Letter> findWithCharacterById(Long id);

    long countByUserAndBookshelfItemAndCharacterAndStatus(
            User user, BookshelfItem bookshelfItem, BookCharacter character, String status);

    Optional<Letter> findFirstByUserAndBookshelfItemAndCharacterAndStatusNotOrderByIdDesc(
            User user, BookshelfItem bookshelfItem, BookCharacter character, String status);

    Optional<Letter> findFirstByUserAndBookshelfItemAndCharacterAndStatusAndReadAtIsNullOrderByIdDesc(
            User user, BookshelfItem bookshelfItem, BookCharacter character, String status);

    boolean existsByBookshelfItemAndCharacterAndMessage(
            BookshelfItem bookshelfItem, BookCharacter character, String message);

    @Query("""
            select l from Letter l
                join fetch l.bookshelfItem s
                join fetch s.book b
                join fetch l.character c
            where l.user = :user and b.isbn13 = :isbn
                and (:characterId is null or c.id = :characterId)
                and (:kept = false or l.keptAt is not null)
            order by l.acceptedAt desc, l.id desc
            """)
    List<Letter> findInbox(@Param("user") User user, @Param("isbn") String isbn,
                           @Param("characterId") Long characterId, @Param("kept") boolean kept);

    @Query("""
            select l.id from Letter l
            where l.status = 'PROCESSING' and l.reply is null
            order by l.acceptedAt, l.id
            """)
    List<Long> findWaitingReplyIds(Pageable page);

    @Modifying
    @Query("""
            update Letter l
            set l.status = 'PUBLISHED', l.publishedAt = :now, l.pendingSlot = null
            where l.status = 'PROCESSING' and l.reply is not null and l.acceptedAt <= :cutoff
            """)
    void publishReady(@Param("now") LocalDateTime now, @Param("cutoff") LocalDateTime cutoff);
}
