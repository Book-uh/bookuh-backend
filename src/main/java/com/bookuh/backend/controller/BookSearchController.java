package com.bookuh.backend.controller;

import com.bookuh.backend.dto.BookSearchResponse;
import com.bookuh.backend.entity.User;
import com.bookuh.backend.service.AuthService;
import com.bookuh.backend.service.BookSearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
//Spring이 보관해 둔 bookSearshService를 여기에 건네준다(생성자 주입)
@RequiredArgsConstructor
@RequestMapping("/api/v1/books")
public class BookSearchController {

    private final AuthService authService;
    private final BookSearchService bookSearchService;

    //검색어 요청 처리
    @GetMapping("/search")
    public BookSearchResponse search(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam @NotBlank String query,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) int size) {
        User user = authService.authenticateBearer(authorization);
        return bookSearchService.search(user, query, page, size);
    }
}
