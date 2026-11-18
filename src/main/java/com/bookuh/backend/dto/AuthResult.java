package com.bookuh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthResult {

    private AuthResponse response;
    private boolean created;
}
