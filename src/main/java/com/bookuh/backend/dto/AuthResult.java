package com.bookuh.backend.dto;

public class AuthResult {

    private AuthResponse response;
    private boolean created;

    public AuthResult(AuthResponse response, boolean created) {
        this.response = response;
        this.created = created;
    }

    public AuthResponse getResponse() {
        return response;
    }

    public boolean isCreated() {
        return created;
    }
}
