package com.bookuh.backend.repository;

import com.bookuh.backend.entity.ApiRequest;
import com.bookuh.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiRequestRepository extends JpaRepository<ApiRequest, Long> {

    //같은 사용자가 보낸 기존 요청을 찾는다
    //조회결과가 없을 수도 있으므로, 메서드 이름을 읽고 조회 기능을 만들어줌
    Optional<ApiRequest> findByUserAndRequestKindAndClientRequestId(
            User user, String requestKind, String clientRequestId);
}
