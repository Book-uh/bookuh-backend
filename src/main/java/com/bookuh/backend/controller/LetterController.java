package com.bookuh.backend.controller;

import com.bookuh.backend.dto.LetterRequests;
import com.bookuh.backend.dto.LetterResponse;
import com.bookuh.backend.service.AuthService;
import com.bookuh.backend.service.LetterService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/letters")
public class LetterController {

    private final AuthService authService;
    private final LetterService letterService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public LetterResponse send(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody LetterRequests.Send request) {
        return letterService.send(authService.authenticateBearer(authorization), request);
    }

    @GetMapping
    public LetterResponse.Inbox list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("isbn") @Pattern(regexp = "\\d{13}") String isbn,
            @RequestParam(value = "characterId", required = false) @Positive Long characterId,
            @RequestParam(value = "kept", defaultValue = "false") boolean kept) {
        return letterService.list(authService.authenticateBearer(authorization), isbn, characterId, kept);
    }

    @GetMapping("/{letterId}")
    public LetterResponse get(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("letterId") @Positive long letterId) {
        return letterService.get(authService.authenticateBearer(authorization), letterId);
    }

    @PostMapping("/{letterId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public LetterResponse retry(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("letterId") @Positive long letterId,
            @Valid @RequestBody LetterRequests.Retry request) {
        return letterService.retry(authService.authenticateBearer(authorization), letterId, request.getRetryId());
    }

    @PutMapping("/{letterId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("letterId") @Positive long letterId) {
        letterService.read(authService.authenticateBearer(authorization), letterId);
    }

    @PutMapping("/{letterId}/keepsake")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void keep(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("letterId") @Positive long letterId) {
        letterService.keep(authService.authenticateBearer(authorization), letterId);
    }

    @ExceptionHandler(LetterService.LetterConflict.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleConflict(LetterService.LetterConflict exception) {
        return Map.of("code", exception.getMessage());
    }
}
