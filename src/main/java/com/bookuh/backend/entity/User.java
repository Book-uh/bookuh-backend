package com.bookuh.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "users")
//jpa가 저장된 사용자를 만든다
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name ="id", columnDefinition = "BIGINT UNSIGNED")
    @Getter
    private Long id;

    @Column(name = "issuance_key_digest",nullable = false,
    unique = true, columnDefinition = "BINARY(32)")
    private byte[] issuanceKeyDigest;

    @Column(name = "access_token_digest", nullable = false,
            unique = true, columnDefinition = "BINARY(32)")
    private byte[] accessTokenDigest;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Getter
    private LocalDateTime createdAt;

    @Column(name ="revoked_at")
    @Getter
    private LocalDateTime revokedAt;

    //새로운 익명 사용자를 만든다.
    public User(byte[] issuanceKeyDigest, byte[] accessTokenDigest){
        this.issuanceKeyDigest = issuanceKeyDigest;
        this.accessTokenDigest = accessTokenDigest;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
