package com.bookuh.backend.controller;

import com.bookuh.backend.dto.AuthResponse;
import com.bookuh.backend.dto.AuthResult;
import com.bookuh.backend.service.AuthService;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    //익명 사용자와 접속 토큰을 발급
    @PostMapping("/anonymous")
    public ResponseEntity<AuthResponse> issue(
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9_-]{32,128}") String issuanceKey) {
        AuthResult result = authService.issue(issuanceKey);
        HttpStatus status = result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.getResponse());
    }
}
