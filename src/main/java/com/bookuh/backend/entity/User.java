package com.bookuh.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name ="id", columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "issuance_key_digest",nullable = false,
    unique = true, columnDefinition = "BINARY(32)")
    private byte[] issuanceKeyDigest;

    @Column(name = "access_token_digest", nullable = false,
            unique = true, columnDefinition = "BINARY(32)")
    private byte[] accessTokenDigest;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name ="revoked_at")
    private LocalDateTime revokedAt;

    //jpa가 저장된 사용자를 만든다
    protected User() {
    }

    //새로운 익명 사용자를 만든다.
    public User(byte[] issuanceKeyDigest, byte[] accessTokenDigest){
        this.issuanceKeyDigest = issuanceKeyDigest;
        this.accessTokenDigest = accessTokenDigest;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getCreatedAt(){
        return createdAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }


}
