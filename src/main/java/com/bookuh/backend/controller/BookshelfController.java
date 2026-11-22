package com.bookuh.backend.controller;

import com.bookuh.backend.dto.*;
import com.bookuh.backend.entity.User;
import com.bookuh.backend.service.AuthService;
import com.bookuh.backend.service.BookshelfService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/bookshelf")
public class BookshelfController {

    private final AuthService authService;
    private final BookshelfService bookshelfService;

    //접속한 사용자의 책장을 조회한다
    @GetMapping
    public BookshelfResponse getShelf(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = authService.authenticateBearer(authorization);
        return new BookshelfResponse(bookshelfService.getShelf(user));
    }

    @GetMapping("/{isbn13}")
    public Map<String, Object> getDetails(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("isbn13") @Pattern(regexp = "\\d{13}") String isbn13) {
        return bookshelfService.getBookDetail(authService.authenticateBearer(authorization), isbn13);
    }

    @PutMapping("/{isbn13}/reading-status")
    public Map<String, Object> saveReadingStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("isbn13") @Pattern(regexp = "\\d{13}") String isbn13,
            @Valid @RequestBody ReadingStatusRequest request) {
        return bookshelfService.saveReadingStatus(authService.authenticateBearer(authorization), isbn13, request);
    }

    @GetMapping("/{isbn13}/progress")
    public ProgressResponse getProgress(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("isbn13") @Pattern(regexp = "\\d{13}") String isbn13) {
        User user = authService.authenticateBearer(authorization);
        return bookshelfService.getProgress(user, isbn13);
    }

    @PutMapping("/{isbn13}/progress")
    public Map<String, Object> saveProgress(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("isbn13") @Pattern(regexp = "\\d{13}") String isbn13,
            @Valid @RequestBody ProgressRequest request) {
        User user = authService.authenticateBearer(authorization);
        return bookshelfService.saveProgress(user, isbn13, request);
    }

    @ExceptionHandler(BookshelfService.ProgressConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleProgressConflict(BookshelfService.ProgressConflictException exception) {
        return Map.of("code", exception.getMessage());
    }

    @DeleteMapping("/{isbn13}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFromShelf(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("isbn13") @Pattern(regexp = "\\d{13}") String isbn13) {
        User user = authService.authenticateBearer(authorization);
        bookshelfService.removeFromShelf(user, isbn13);
    }

    //접속한 사용자의 책장에 책을 담는다
    @PutMapping("/{isbn13}")
    public ResponseEntity<ShelfItemResponse> addToShelf(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("isbn13") @Pattern(regexp = "\\d{13}") String isbn13,
            @Valid @RequestBody ShelfItemRequest request) {
        User user = authService.authenticateBearer(authorization);
        ShelfItemResult result = bookshelfService.addToShelf(user, isbn13, request);
        HttpStatus status = result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.getResponse());
    }
}
