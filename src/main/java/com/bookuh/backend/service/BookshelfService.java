package com.bookuh.backend.service;

import com.bookuh.backend.dto.BookSearchResponse;
import com.bookuh.backend.dto.ShelfItemRequest;
import com.bookuh.backend.dto.ShelfItemResponse;
import com.bookuh.backend.dto.ShelfItemResult;
import com.bookuh.backend.entity.Book;
import com.bookuh.backend.entity.BookshelfItem;
import com.bookuh.backend.entity.User;
import com.bookuh.backend.repository.BookRepository;
import com.bookuh.backend.repository.BookshelfItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class BookshelfService {

    private static final Pattern YEAR_PREFIX = Pattern.compile("^(\\d{4})");

    private final BookshelfItemRepository bookshelfItemRepository;
    private final BookRepository bookRepository;
    private final BookSearchService bookSearchService;

    //사용자의 책장에 책을 담는다
    @Transactional
    public ShelfItemResult addToShelf(User user, String isbn13, ShelfItemRequest request) {
        //공용 책 정보가 DB에 없으면 저장하고, 있으면 기존 것을 사용한다.
        Book book = bookRepository.findById(isbn13)
                .orElseGet(() -> bookRepository.save(fetchBook(isbn13, request.getGoogleVolumeId())));

        // 같은 사용자가 같은 책을 이미 담았으면 기존 칸을 그대로 돌려준다
        Optional<BookshelfItem> existing = bookshelfItemRepository.findByUserAndBook(user, book);
        if (existing.isPresent()) {
            return new ShelfItemResult(toResponse(existing.get()), false);
        }

        // 새 책장 칸을 만들어 저장한다
        BookshelfItem saved = bookshelfItemRepository.save(new BookshelfItem(user, book));
        return new ShelfItemResult(toResponse(saved), true);
    }

    // 사용자의 책장을 최근 담은 순으로 조회한다
    @Transactional(readOnly = true)
    public List<ShelfItemResponse> getShelf(User user) {
        return bookshelfItemRepository.findByUserOrderByShelvedAtDesc(user).stream()
                .map(this::toResponse)
                .toList();
    }

    // 구글 북스에서 책 정보를 직접 확인해 공용 책을 만든다
    private Book fetchBook(String isbn13, String googleVolumeId) {
        BookSearchResponse volume = bookSearchService.findVolume(googleVolumeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "책 정보를 찾지 못했습니다."));

        // 주소의 ISBN과 구글이 알려준 ISBN이 다르면 담지 않는다
        if (!isbn13.equals(volume.getIsbn13())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ISBN과 책 정보가 일치하지 않습니다.");
        }
        return new Book(isbn13, volume.getTitle(), volume.getAuthors(),
                blankToNull(volume.getPublisher()), toPublishedYear(volume.getPublishedDate()),
                blankToNull(volume.getThumbnailUrl()));
    }

    // 출간일 앞의 네 자리를 출간 연도로 쓴다
    private Short toPublishedYear(String publishedDate) {
        Matcher matcher = YEAR_PREFIX.matcher(publishedDate);
        return matcher.find() ? Short.valueOf(matcher.group(1)) : null;
    }

    // 빈 글자는 값이 없는 것으로 저장한다
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
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
