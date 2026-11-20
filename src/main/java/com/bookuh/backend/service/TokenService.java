package com.bookuh.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

@Service
public class TokenService {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKeySpec tokenKey;

    public TokenService(@Value("${auth.token-secret}") String tokenSecret) {
        byte[] secret = Base64.getDecoder().decode(tokenSecret);
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("토큰 비밀키는 32바이트 이상이어야 합니다.");
        }
        this.tokenKey = new SecretKeySpec(secret, HMAC_ALGORITHM);
    }

    // DB에 저장할 검증값을 만든다
    public byte[] hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("검증값을 만들 수 없습니다.", e);
        }
    }

    // 같은 발급 요청 키에는 같은 접속 토큰을 만든다
    public String createAccessToken(String issuanceKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(tokenKey);
            byte[] result = mac.doFinal(issuanceKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(result);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("접속 토큰을 만들 수 없습니다.", e);
        }
    }
}
