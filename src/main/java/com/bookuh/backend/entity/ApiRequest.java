package com.bookuh.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Entity
@Table(name = "api_requests",
        uniqueConstraints = @UniqueConstraint( //같은 사용자,요청 종류, 요청 번호 조합의 중복 저장 방지
                columnNames = {"user_id", "request_kind", "client_request_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiRequest {

    private static final String PROCESSING = "PROCESSING";
    private static final String SUCCEEDED = "SUCCEEDED";
    private static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    //북어 해석 요청, 독서 기록 저장 같은 요청
    @Column(name = "request_kind", nullable = false, length = 40)
    private String requestKind;

    //북어 대화에서 명세 operationId
    @Column(name = "client_request_id", nullable = false, length = 128,
            columnDefinition = "VARCHAR(128) COLLATE utf8mb4_bin")
    private String clientRequestId;

    //같은 번호로 입력 내용까지 동일하게 보냈는지 비교할 확인값
    @Column(name = "payload_digest", nullable = false, columnDefinition = "BINARY(32)")
    private byte[] payloadDigest;

    //처리상태
    @Column(name = "status", nullable = false, length = 24)
    private String status;

    //사용자 확인을 기다리는 작업 내용
    @JdbcTypeCode(SqlTypes.JSON) //map을 mysql의 json형태로 저장하기 위한 설정
    @Column(name = "pending_payload", columnDefinition = "JSON")
    private Map<String, Object> pendingPayload;

    //Http 응답 코드와 처리 결과
    @Column(name = "response_code", columnDefinition = "SMALLINT UNSIGNED")
    private Short responseCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_payload", columnDefinition = "JSON")
    private Map<String, Object> resultPayload;

    //접수 시각과 마지막 변경 시작
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 새 요청의 종류와 입력 확인값을 저장한다
    public ApiRequest(User user, String requestKind,
                      String clientRequestId, byte[] payloadDigest) {
        this.user = user;
        this.requestKind = requestKind;
        this.clientRequestId = clientRequestId;
        this.payloadDigest = payloadDigest;
        this.status = PROCESSING;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        this.updatedAt = this.createdAt;
    }

    // 성공한 요청의 응답 코드와 결과를 저장한다
    public void succeed(int responseCode, Map<String, Object> resultPayload) {
        this.status = SUCCEEDED;
        this.responseCode = (short) responseCode;
        this.resultPayload = resultPayload;
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    // 요청을 실패 상태로 바꾼다
    public void fail() {
        this.status = FAILED;
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    // 실패한 요청을 다시 처리할 준비를 한다
    public void retry() {
        this.status = PROCESSING;
        this.responseCode = null;
        this.resultPayload = null;
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    // 처리를 마친 요청인지 확인한다
    public boolean isSucceeded() {
        return SUCCEEDED.equals(status);
    }

    // 아직 처리 중인 요청인지 확인한다
    public boolean isProcessing() {
        return PROCESSING.equals(status);
    }

    // 처리에 실패한 요청인지 확인한다
    public boolean isFailed() {
        return FAILED.equals(status);
    }
}
