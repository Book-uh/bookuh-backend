package com.bookuh.backend.controller;

import com.bookuh.backend.dto.ReadingRecordRequest;
import com.bookuh.backend.dto.ReadingRecordResponse;
import com.bookuh.backend.service.AuthService;
import com.bookuh.backend.service.ReadingRecordService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reading-records")
public class ReadingRecordController {

    private final AuthService authService;
    private final ReadingRecordService readingRecordService;

    @GetMapping
    public Map<String, List<ReadingRecordResponse>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "isbn", required = false) @Pattern(regexp = "\\d{13}") String isbn,
            @RequestParam(value = "readingDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate readingDate,
            @RequestParam(value = "timeBand", required = false) @Pattern(regexp = "DAY|NIGHT") String timeBand,
            @RequestParam(value = "durationMinutes", required = false) @Positive Integer durationMinutes,
            @RequestParam(value = "pagesRead", required = false) @Positive Integer pagesRead) {
        return Map.of("items", readingRecordService.list(authService.authenticateBearer(authorization),
                isbn, readingDate, timeBand, durationMinutes, pagesRead));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> add(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ReadingRecordRequest body) {
        ReadingRecordService.WriteResult result = readingRecordService.add(authService.authenticateBearer(authorization), body);
        return ResponseEntity.status(result.getStatusCode()).body(result.getBody());
    }

    @PostMapping("/pending/{pendingId}/decision")
    public Map<String, Object> decide(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("pendingId") @Positive long pendingId,
            @Valid @RequestBody DecisionRequest body) {
        return readingRecordService.decide(authService.authenticateBearer(authorization), pendingId, body.getDecision());
    }

    @PatchMapping("/{recordId}")
    public ReadingRecordResponse update(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("recordId") @Positive long recordId,
            @RequestBody ObjectNode body) {
        return readingRecordService.update(authService.authenticateBearer(authorization), recordId, body);
    }

    @DeleteMapping("/{recordId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("recordId") @Positive long recordId) {
        readingRecordService.delete(authService.authenticateBearer(authorization), recordId);
    }

    @ExceptionHandler(ReadingRecordService.ActionConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleConflict() {
        return Map.of("code", "ACTION_CONFLICT");
    }

    @ExceptionHandler(ReadingRecordService.RecordNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound() {
        return Map.of("code", "READING_RECORD_NOT_FOUND");
    }

    @Getter
    @Setter
    public static class DecisionRequest {
        @NotBlank
        @Pattern(regexp = "ADD|CANCEL")
        private String decision;
    }
}
