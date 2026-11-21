package com.bookuh.backend.repository;

import com.bookuh.backend.entity.ManagerMessage;
import com.bookuh.backend.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ManagerMessageRepository extends JpaRepository<ManagerMessage, Long> {

    // 사용자의 북어 대화를 정해진 개수와 순서로 가져온다
    List<ManagerMessage> findByUser(User user, Pageable pageable);
    // 이전 페이지의 기준 메시지가 내 것인지 찾는다
    Optional<ManagerMessage> findByUserAndId(User user, Long id);

    // 기준 메시지보다 먼저 작성된 내 대화를 가져온다
    @Query("""
            select m from ManagerMessage m
            where m.user = :user
              and (m.createdAt < :createdAt
                   or (m.createdAt = :createdAt and m.id < :id))
            """)
    List<ManagerMessage> findOlder(
            @Param("user") User user,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("id") Long id,
            Pageable pageable);
}
