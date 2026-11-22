package com.bookuh.backend.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Getter
public class CharacterRequestException extends ResponseStatusException {

    private final String code;

    public CharacterRequestException(HttpStatus status, String code, String message) {
        super(status, message);
        this.code = code;
    }
}
