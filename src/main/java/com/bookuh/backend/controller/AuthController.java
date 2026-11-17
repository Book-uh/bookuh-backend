package com.bookuh.backend.controller;

import com.bookuh.backend.dto.AuthResponse;
import com.bookuh.backend.dto.AuthResult;
import com.bookuh.backend.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this. authService = authService;
    }

    //익명 사용자와 접속 토큰을 발급
    @PostMapping("/api/v1/auth/anonymous")
    public ResponseEntity<AuthResponse> issue(@RequestHeader("Idempotency-Key") String issuanceKey) {
        AuthResult result = authService.issue(issuanceKey);

        if(result.isCreated()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(result.getResponse());

        }
        return ResponseEntity.ok(result.getResponse());
    }
}
