package com.bookuh.backend.repository;

import com.bookuh.backend.entity.BookshelfItem;
import com.bookuh.backend.entity.ReadingActivity;
import com.bookuh.backend.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReadingActivityRepository extends JpaRepository<ReadingActivity, Long> {

    @EntityGraph(attributePaths = {"bookshelfItem", "bookshelfItem.book"})
    @Query("""
            SELECT activity FROM ReadingActivity activity
            WHERE activity.bookshelfItem.user = :user
                AND (:isbn IS NULL OR activity.bookshelfItem.book.isbn13 = :isbn)
                AND (:readingDate IS NULL OR activity.readingDate = :readingDate)
                AND (:timeBand IS NULL OR activity.timeBand = :timeBand)
                AND (:durationMinutes IS NULL OR activity.durationMinutes = :durationMinutes)
                AND (:pagesRead IS NULL OR activity.pagesRead = :pagesRead)
            ORDER BY activity.readingDate DESC, activity.id DESC
            """)
    List<ReadingActivity> findMatching(@Param("user") User user, @Param("isbn") String isbn,
                                      @Param("readingDate") LocalDate readingDate, @Param("timeBand") String timeBand,
                                      @Param("durationMinutes") Integer durationMinutes, @Param("pagesRead") Integer pagesRead);

    Optional<ReadingActivity> findByIdAndBookshelfItemUser(Long id, User user);

    Optional<ReadingActivity> findFirstByBookshelfItemAndReadingDateAndDurationMinutesAndPagesReadOrderByIdDesc(
            BookshelfItem bookshelfItem, LocalDate readingDate, Integer durationMinutes, Integer pagesRead);

    List<ReadingActivity> findByBookshelfItemUserAndReadingDateGreaterThanEqualAndReadingDateLessThan(
            User user, LocalDate start, LocalDate endExclusive);

    @Query("""
            SELECT COUNT(item) FROM BookshelfItem item
            WHERE item.user = :user AND item.finishedOn >= :start AND item.finishedOn < :endExclusive
            """)
    long countFinishedBooks(@Param("user") User user, @Param("start") LocalDate start,
                            @Param("endExclusive") LocalDate endExclusive);
}
