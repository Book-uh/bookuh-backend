package com.bookuh.backend.controller;

import com.bookuh.backend.dto.BookSearchResponse;
import com.bookuh.backend.service.BookSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
//Spring이 보관해 둔 bookSearshService를 여기에 건네준다(생성자 주입)
@RequiredArgsConstructor
public class BookSearchController {

    private final BookSearchService bookSearchService;

    //검색어 요청 처리
    @GetMapping("/api/v1/books/search")
    public List<BookSearchResponse> search(@RequestParam String query){
        return bookSearchService.search(query);
    }
}
