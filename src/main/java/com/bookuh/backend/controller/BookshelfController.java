package com.bookuh.backend.controller;

import com.bookuh.backend.dto.ShelfItemRequest;
import com.bookuh.backend.dto.ShelfItemResponse;
import com.bookuh.backend.dto.ShelfItemResult;
import com.bookuh.backend.entity.User;
import com.bookuh.backend.service.AuthService;
import com.bookuh.backend.service.BookshelfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
        User user = authenticateUser(authorization);
        return bookshelfService.getShelf(user);
    }

    //접속한 사용자의 책장에 책을 담는다
    @PutMapping("/{isbn13}")
    public ResponseEntity<ShelfItemResponse> addToShelf(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("isbn13") String isbn13,
            @RequestBody ShelfItemRequest request) {
        User user = authenticateUser(authorization);

        if(!isbn13.matches("[0-9]{13}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ISBN은 숫자 13자리여야 합니다.");
        }
        if(request.getTitle() == null || request.getTitle().isBlank()
        ||request.getTitle().length() > 512 || request.getAuthors() ==null){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"책 제목과 저자 목록을 확인해 주세요.");
        }
        ShelfItemResult result = bookshelfService.addToShelf(user, isbn13, request);
        if(result.isCreated()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(result.getResponse());
        }
        return ResponseEntity.ok(result.getResponse());
    }
    // 요청 헤더의 접속 토큰으로 사용자를 확인한다
    private User authenticateUser(String authorization) {
        if (authorization == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "접속 토큰이 필요합니다.");
        }

        String[] parts = authorization.trim().split("\\s+", 2);
        if (parts.length != 2 || !parts[0].equalsIgnoreCase("Bearer")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "접속 토큰 형식이 올바르지 않습니다.");
        }
        return authService.authenticate(parts[1].trim());
    }
}
