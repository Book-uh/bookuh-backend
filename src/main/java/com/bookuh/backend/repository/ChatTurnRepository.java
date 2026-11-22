package com.bookuh.backend.repository;

import com.bookuh.backend.entity.BookCharacter;
import com.bookuh.backend.entity.BookshelfItem;
import com.bookuh.backend.entity.ChatTurn;
import com.bookuh.backend.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatTurnRepository extends JpaRepository<ChatTurn, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ChatTurn> findLockedById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ChatTurn> findByUserAndClientTurnId(User user, String clientTurnId);

    List<ChatTurn> findByUserAndBookshelfItemAndCharacterOrderByIdAsc(
            User user, BookshelfItem shelf, BookCharacter character);

    @Query("""
            select t from ChatTurn t
            where t.user = :user and t.bookshelfItem = :shelf and t.character = :character
                and t.status = 'SUCCEEDED' and t.progressVersion = :version
                and t.includeInFutureContext = true and t.id < :id
            order by t.id desc
            """)
    List<ChatTurn> findRecentContext(@Param("user") User user, @Param("shelf") BookshelfItem shelf,
                                     @Param("character") BookCharacter character, @Param("version") int version,
                                     @Param("id") Long id, Pageable page);
}
