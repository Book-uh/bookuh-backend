package com.bookuh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthResponse {

    private String userId;
    private String accessToken;
    private String tokenType;
}
