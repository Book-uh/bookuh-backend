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

    private final byte[] tokenSecret;

    public TokenService(@Value("${auth.token-secret}") String tokenSecret) {
        this.tokenSecret = Base64.getDecoder().decode(tokenSecret);
        if(this.tokenSecret.length < 32) {
            throw new IllegalArgumentException("토큰 비밀키는 32바이트 이상이어야함");
        }
    }

        // DB에 저장할 검증값을 만든다
        public byte[] hash(String value) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return digest.digest(value.getBytes(StandardCharsets.UTF_8));
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("검증값을 만들 수 없습니다.", e);
            }
        }

        // 같은 발급 요청 키에는 같은 접속 토큰을 만든다
        public String createAccessToken(String issuanceKey) {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                SecretKeySpec key = new SecretKeySpec(tokenSecret, "HmacSHA256");
                mac.init(key);

                byte[] result = mac.doFinal(issuanceKey.getBytes(StandardCharsets.UTF_8));
                return Base64.getUrlEncoder().withoutPadding().encodeToString(result);
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("접속 토큰을 만들 수 없습니다.", e);
            }
    }
}
