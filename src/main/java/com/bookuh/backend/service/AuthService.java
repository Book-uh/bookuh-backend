package com.bookuh.backend.service;

import com.bookuh.backend.dto.AuthResponse;
import com.bookuh.backend.dto.AuthResult;
import com.bookuh.backend.entity.User;
import com.bookuh.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String BEARER = "Bearer";

    private final UserRepository userRepository;
    private final TokenService tokenService;

    // 처음 요청이면 사용자를 만들고 같은 요청이면 기존 사용자를 반환한다
    public synchronized AuthResult issue(String issuanceKey) {
        byte[] issuanceKeyDigest = tokenService.hash(issuanceKey);
        String accessToken = tokenService.createAccessToken(issuanceKey);

        Optional<User> foundUser = userRepository.findByIssuanceKeyDigest(issuanceKeyDigest);
        boolean created = foundUser.isEmpty();
        User user = foundUser.orElseGet(() ->
                userRepository.save(new User(issuanceKeyDigest, tokenService.hash(accessToken))));
        ensureActive(user);

        AuthResponse response = new AuthResponse(user.getId().toString(), accessToken, BEARER);
        return new AuthResult(response, created);
    }

    // 접속 토큰으로 사용자를 확인한다
    public User authenticate(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw unauthorized("접속 토큰이 필요합니다.");
        }

        User user = userRepository.findByAccessTokenDigest(tokenService.hash(accessToken))
                .orElseThrow(() -> unauthorized("올바르지 않은 접속 토큰입니다."));
        ensureActive(user);
        return user;
    }

    // 요청 헤더의 접속 토큰으로 사용자를 확인한다
    public User authenticateBearer(String authorization) {
        if (authorization == null) {
            throw unauthorized("접속 토큰이 필요합니다.");
        }

        String[] parts = authorization.trim().split("\\s+", 2);
        if (parts.length != 2 || !parts[0].equalsIgnoreCase(BEARER)) {
            throw unauthorized("접속 토큰 형식이 올바르지 않습니다.");
        }
        return authenticate(parts[1].trim());
    }

    // 폐기된 사용자면 더 이상 쓸 수 없다
    private void ensureActive(User user) {
        if (user.isRevoked()) {
            throw unauthorized("사용할 수 없는 사용자입니다.");
        }
    }

    // 인증 실패를 알리는 예외를 만든다
    private ResponseStatusException unauthorized(String reason) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, reason);
    }
}
