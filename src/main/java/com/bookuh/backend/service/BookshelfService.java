package com.bookuh.backend.service;

import com.bookuh.backend.entity.Book;
import com.bookuh.backend.entity.BookshelfItem;
import com.bookuh.backend.entity.User;
import com.bookuh.backend.repository.BookRepository;
import com.bookuh.backend.repository.BookshelfItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookshelfService {

    private final BookshelfItemRepository bookshelfItemRepository;
    private final BookRepository bookRepository;

    //사용자의 책장에 책을 담는다
    public BookshelfItem addToShelf(User user, Book incomingBook){
        //공용 책 정보가 DB에 없으면 저장하고, 있으면 기존 것을 사용한다.
        Book book = bookRepository.findById(incomingBook.getIsbn13()).orElse(null);
        if(book == null) {
            book = bookRepository.save(incomingBook);
        }
        // 같은 사용자가 같은 책을 이미 담았으면 기존 칸을 그대로 돌려준다
        BookshelfItem existing = bookshelfItemRepository.findByUserAndBook(user, book).orElse(null);
        if (existing != null) {
            return existing;
        }

        // 새 책장 칸을 만들어 저장한다
        BookshelfItem item = new BookshelfItem(user, book);
        return bookshelfItemRepository.save(item);
    }

    // 사용자의 책장을 최근 담은 순으로 조회한다
    public List<BookshelfItem> getShelf(User user) {
        return bookshelfItemRepository.findByUserOrderByShelvedAtDesc(user);
    }
}
