package com.bookuh.backend.controller;

import com.bookuh.backend.service.CharacterRequestException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class CharacterErrorHandler {

    @ExceptionHandler(CharacterRequestException.class)
    public ResponseEntity<Map<String, String>> respond(CharacterRequestException error) {
        return ResponseEntity.status(error.getStatusCode())
                .body(Map.of("code", error.getCode(), "message", error.getReason()));
    }
}
