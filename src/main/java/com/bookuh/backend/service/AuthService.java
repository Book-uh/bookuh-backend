package com.bookuh.backend.service;

import com.bookuh.backend.dto.AuthResponse;
import com.bookuh.backend.dto.AuthResult;
import com.bookuh.backend.entity.User;
import com.bookuh.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final TokenService tokenService;

    public AuthService(UserRepository userRepository, TokenService tokenService) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
    }

    // 처음 요청이면 사용자를 만들고 같은 요청이면 기존 사용자를 반환한다
    public synchronized AuthResult issue(String issuanceKey) {
        if (issuanceKey == null || !issuanceKey.matches("[A-Za-z0-9_-]{32,128}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "발급 요청 키 형식이 올바르지 않습니다.");
        }

        byte[] issuanceKeyDigest = tokenService.hash(issuanceKey);
        String accessToken = tokenService.createAccessToken(issuanceKey);
        Optional<User> foundUser = userRepository.findByIssuanceKeyDigest(issuanceKeyDigest);

        User user;
        boolean created = false;

        if (foundUser.isPresent()) {
            user = foundUser.get();
            if (user.getRevokedAt() != null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용할 수 없는 사용자입니다.");
            }
        } else {
            byte[] accessTokenDigest = tokenService.hash(accessToken);
            user = new User(issuanceKeyDigest, accessTokenDigest);
            user = userRepository.save(user);
            created = true;
        }

        AuthResponse response = new AuthResponse(user.getId().toString(), accessToken, "Bearer");
        return new AuthResult(response, created);
    }

    // 접속 토큰으로 사용자를 확인한다
    public User authenticate(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "접속 토큰이 필요합니다.");
        }

        byte[] accessTokenDigest = tokenService.hash(accessToken);
        Optional<User> foundUser = userRepository.findByAccessTokenDigest(accessTokenDigest);

        if (foundUser.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "올바르지 않은 접속 토큰입니다.");
        }

        User user = foundUser.get();
        if (user.getRevokedAt() != null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용할 수 없는 사용자입니다.");
        }

        return user;
    }
}