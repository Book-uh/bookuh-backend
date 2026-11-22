package com.bookuh.backend.service;

import com.bookuh.backend.dto.ProgressRequest;
import com.bookuh.backend.dto.ProgressResponse;
import com.bookuh.backend.dto.ReadingStatusRequest;
import com.bookuh.backend.dto.ShelfItemRequest;
import com.bookuh.backend.dto.ShelfItemResponse;
import com.bookuh.backend.dto.ShelfItemResult;
import com.bookuh.backend.entity.ApiRequest;
import com.bookuh.backend.entity.Book;
import com.bookuh.backend.entity.BookshelfItem;
import com.bookuh.backend.entity.User;
import com.bookuh.backend.repository.ApiRequestRepository;
import com.bookuh.backend.repository.BookRepository;
import com.bookuh.backend.repository.BookshelfItemRepository;
import com.bookuh.backend.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookshelfService {

    private static final String PROGRESS_SAVE = "PROGRESS_SAVE";
    private static final String READING_STATUS_SAVE = "READING_STATUS_SAVE";
    private static final int MAX_SCENE_BYTES = 65_535;

    private final BookshelfItemRepository bookshelfItemRepository;
    private final BookRepository bookRepository;
    private final BookSearchService bookSearchService;
    private final UserRepository userRepository;
    private final ApiRequestRepository apiRequestRepository;
    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

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
        return bookshelfItemRepository.findByUserOrderByShelvedAtDescBookIsbn13Asc(user).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProgressResponse getProgress(User user, String isbn13) {
        BookshelfItem item = findShelfItem(user, isbn13);
        return toProgressResponse(item);
    }

    @Transactional
    public Map<String, Object> saveProgress(User user, String isbn13, ProgressRequest request) {
        String stage = request.getStage();
        String scene = validateScene(stage, request.getScene());
        ZoneId timeZone = parseTimeZone(request.getTimeZone());
        byte[] digest = hashInput(Arrays.asList(isbn13, request.getBaseProgressVersion(),
                stage, request.getScene(), request.getTimeZone()));

        // 같은 사용자의 저장 요청을 순서대로 처리한다
        User lockedUser = lockUser(user);
        ApiRequest previous = findPrevious(lockedUser, PROGRESS_SAVE, request.getActionId(), digest);
        if (previous != null) {
            return previous.getResultPayload();
        }

        BookshelfItem item = findShelfItem(lockedUser, isbn13);
        if (item.getProgressVersion() != request.getBaseProgressVersion()
                && !item.matchesProgress(stage, scene)) {
            throw new ProgressConflictException("PROGRESS_CHANGED");
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        item.updateProgress(stage, scene, now, timeZone);

        return saveProgressResult(lockedUser, PROGRESS_SAVE, request.getActionId(), digest, item);
    }

    @Transactional
    public Map<String, Object> saveReadingStatus(User user, String isbn13, ReadingStatusRequest request) {
        ZoneId timeZone = parseTimeZone(request.getTimeZone());
        String status = request.getStatus();
        byte[] digest = hashInput(List.of(isbn13, status, request.getTimeZone()));
        User lockedUser = lockUser(user);
        ApiRequest previous = findPrevious(lockedUser, READING_STATUS_SAVE, request.getActionId(), digest);
        if (previous != null) {
            return previous.getResultPayload();
        }

        BookshelfItem item = findShelfItem(lockedUser, isbn13);
        if ("FINISHED".equals(status)) {
            item.updateProgress("FINISHED", null, Instant.now().truncatedTo(ChronoUnit.MICROS), timeZone);
        } else {
            item.changeReadingStatus(status);
        }
        return saveProgressResult(lockedUser, READING_STATUS_SAVE, request.getActionId(), digest, item);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getBookDetail(User user, String isbn13) {
        BookshelfItem item = findShelfItem(user, isbn13);
        Map<String, Object> detail = objectMapper.convertValue(
                toResponse(item), new TypeReference<Map<String, Object>>() {});
        ProgressResponse progress = toProgressResponse(item);
        detail.put("finishedOn", progress.getFinishedOn());
        detail.put("progress", progress.getProgress());
        detail.put("characterStatus", item.getBook().getCharacterStatus());
        return detail;
    }

    private User lockUser(User user) {
        return userRepository.findLockedById(user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
    }

    private ApiRequest findPrevious(User user, String kind, String actionId, byte[] digest) {
        ApiRequest previous = apiRequestRepository
                .findByUserAndRequestKindAndClientRequestId(user, kind, actionId)
                .orElse(null);
        if (previous != null && !Arrays.equals(previous.getPayloadDigest(), digest)) {
            throw new ProgressConflictException("ACTION_CONFLICT");
        }
        return previous;
    }

    private Map<String, Object> saveProgressResult(
            User user, String kind, String actionId, byte[] digest, BookshelfItem item) {
        Map<String, Object> result = objectMapper.convertValue(
                toProgressResponse(item), new TypeReference<Map<String, Object>>() {});

        // 진도 변경과 재시도에 돌려줄 결과를 한 트랜잭션에 저장한다
        ApiRequest saved = new ApiRequest(user, kind, actionId, digest);
        saved.succeed(HttpStatus.OK.value(), result);
        apiRequestRepository.save(saved);
        return result;
    }

    private BookshelfItem findShelfItem(User user, String isbn13) {
        return bookshelfItemRepository.findByUserAndBookIsbn13(user, isbn13)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "내 책장에 없는 책입니다."));
    }

    private ProgressResponse toProgressResponse(BookshelfItem item) {
        ProgressResponse.Progress progress = null;
        if (item.getProgressStage() != null) {
            progress = new ProgressResponse.Progress(
                    item.getProgressVersion(),
                    item.getProgressStage(),
                    item.getProgressScene(),
                    item.getProgressUpdatedAt().toInstant(ZoneOffset.UTC));
        }

        return new ProgressResponse(progress, item.getReadingStatus(), item.getFinishedOn());
    }

    private String validateScene(String stage, String scene) {
        if (!"SCENE".equals(stage)) {
            if (scene != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "이 단계에는 장면을 입력할 수 없습니다.");
            }
            return null;
        }

        if (scene == null || scene.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "읽은 장면을 입력해 주세요.");
        }

        String trimmed = scene.strip();
        if (trimmed.getBytes(StandardCharsets.UTF_8).length > MAX_SCENE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "읽은 장면이 너무 깁니다.");
        }
        return trimmed;
    }

    private ZoneId parseTimeZone(String value) {
        try {
            return ZoneId.of(value);
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 시간대를 입력해 주세요.");
        }
    }

    private byte[] hashInput(List<?> fields) {
        try {
            return tokenService.hash(objectMapper.writeValueAsString(fields));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("진도 요청 확인값을 만들 수 없습니다.", e);
        }
    }

    public static class ProgressConflictException extends RuntimeException {
        public ProgressConflictException(String code) {
            super(code);
        }
    }

    // 구글 북스에서 책 정보를 직접 확인해 공용 책을 만든다
    private Book fetchBook(String isbn13, String googleVolumeId) {
        Book book = bookSearchService.findBook(googleVolumeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "책 정보를 찾지 못했습니다."));

        // 주소의 ISBN과 구글이 알려준 ISBN이 다르면 담지 않는다
        if (!isbn13.equals(book.getIsbn13())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ISBN과 책 정보가 일치하지 않습니다.");
        }
        return book;
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