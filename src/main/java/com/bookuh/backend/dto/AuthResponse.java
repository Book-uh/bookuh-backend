package com.bookuh.backend.dto;

public class AuthResponse {

    private String userId;
    private String accessToken;
    private String tokenType;

    public AuthResponse(String userId, String accessToken, String tokenType) {
        this.userId = userId;
        this.accessToken = accessToken;
        this.tokenType = tokenType;
    }

    public String getUserId() {
        return userId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }
}
