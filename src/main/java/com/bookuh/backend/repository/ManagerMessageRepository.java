package com.bookuh.backend.repository;

import com.bookuh.backend.entity.ManagerMessage;
import com.bookuh.backend.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ManagerMessageRepository extends JpaRepository<ManagerMessage, Long> {

    // 사용자의 북어 대화를 정해진 개수와 순서로 가져온다
    List<ManagerMessage> findByUser(User user, Pageable pageable);
}