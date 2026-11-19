package com.bookuh.backend.service;

import com.bookuh.backend.dto.ShelfItemRequest;
import com.bookuh.backend.dto.ShelfItemResponse;
import com.bookuh.backend.dto.ShelfItemResult;
import com.bookuh.backend.entity.Book;
import com.bookuh.backend.entity.BookshelfItem;
import com.bookuh.backend.entity.User;
import com.bookuh.backend.repository.BookRepository;
import com.bookuh.backend.repository.BookshelfItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookshelfService {

    private final BookshelfItemRepository bookshelfItemRepository;
    private final BookRepository bookRepository;

    //사용자의 책장에 책을 담는다
    @Transactional
    public ShelfItemResult addToShelf(User user, String isbn13, ShelfItemRequest request) {
        //공용 책 정보가 DB에 없으면 저장하고, 있으면 기존 것을 사용한다.
        Book book = bookRepository.findById(isbn13).orElse(null);
        if (book == null) {
            Book incomingBook = new Book(
                    isbn13,
                    null,
                    request.getTitle(),
                    request.getAuthors(),
                    request.getPublisher(),
                    request.getPublishedYear(),
                    request.getCoverUrl()
            );
            book = bookRepository.save(incomingBook);
        }
        // 같은 사용자가 같은 책을 이미 담았으면 기존 칸을 그대로 돌려준다
        BookshelfItem existing = bookshelfItemRepository.findByUserAndBook(user, book).orElse(null);
        if (existing != null) {
            return new ShelfItemResult(toResponse(existing), false);
        }

        // 새 책장 칸을 만들어 저장한다
        BookshelfItem item = new BookshelfItem(user, book);
        BookshelfItem saved = bookshelfItemRepository.save(item);
        return new ShelfItemResult(toResponse(saved), true);
    }

    // 사용자의 책장을 최근 담은 순으로 조회한다
    @Transactional(readOnly = true)
    public List<ShelfItemResponse> getShelf(User user) {
        List<BookshelfItem> items = bookshelfItemRepository.findByUserOrderByShelvedAtDesc(user);
        List<ShelfItemResponse> responses = new ArrayList<>();

        for (BookshelfItem item : items) {
            responses.add(toResponse(item));
        }
        return responses;
    }

    // 책장 정보를 앱에 보낼 응답으로 바꾼다
    private ShelfItemResponse toResponse(BookshelfItem item) {
        Book book = item.getBook();
        return new ShelfItemResponse(
                book.getIsbn13(),
                book.getTitle(),
                book.getAuthors(),
                book.getPublisher(),
                book.getCoverUrl(),
                item.getReadingStatus(),
                item.getShelvedAt()
        );
    }
}