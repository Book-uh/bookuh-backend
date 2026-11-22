package com.bookuh.backend.controller;

import com.bookuh.backend.dto.CharacterResolveRequest;
import com.bookuh.backend.dto.CharacterResponse;
import com.bookuh.backend.entity.User;
import com.bookuh.backend.service.AuthService;
import com.bookuh.backend.service.CharacterService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/books/{isbn}/characters")
public class CharacterController {

    private final AuthService authService;
    private final CharacterService characterService;

    @PostMapping("/resolve")
    public ResponseEntity<CharacterResponse> resolve(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("isbn") @Pattern(regexp = "\\d{13}") String isbn,
            @Valid @RequestBody CharacterResolveRequest request) {
        User user = authService.authenticateBearer(authorization);
        CharacterResponse result = characterService.resolve(user, isbn, request.getMode());
        HttpStatus status = CharacterService.PREPARING.equals(result.getStatus()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }
}
