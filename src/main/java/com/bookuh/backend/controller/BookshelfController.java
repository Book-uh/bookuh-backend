package com.bookuh.backend.controller;

import com.bookuh.backend.dto.ShelfItemRequest;
import com.bookuh.backend.dto.ShelfItemResponse;
import com.bookuh.backend.dto.ShelfItemResult;
import com.bookuh.backend.entity.User;
import com.bookuh.backend.service.AuthService;
import com.bookuh.backend.service.BookshelfService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/bookshelf")
public class BookshelfController {

    private final AuthService authService;
    private final BookshelfService bookshelfService;

    //접속한 사용자의 책장을 조회한다
    @GetMapping
    public List<ShelfItemResponse> getShelf(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = authService.authenticateBearer(authorization);
        return bookshelfService.getShelf(user);
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
