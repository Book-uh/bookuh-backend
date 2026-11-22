package com.bookuh.backend.service;

import com.bookuh.backend.dto.CharacterResponse;
import com.bookuh.backend.entity.Book;
import com.bookuh.backend.entity.BookCharacter;
import com.bookuh.backend.entity.BookshelfItem;
import com.bookuh.backend.entity.Letter;
import com.bookuh.backend.entity.User;
import com.bookuh.backend.repository.BookCharacterRepository;
import com.bookuh.backend.repository.BookRepository;
import com.bookuh.backend.repository.BookshelfItemRepository;
import com.bookuh.backend.repository.LetterRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CharacterService {

    public static final String PREPARING = "PREPARING";
    private static final String READY = "READY";
    private static final String NO_CANDIDATES = "NO_CANDIDATES";
    private static final String FAILED = "FAILED";
    private static final String PUBLISHED = "PUBLISHED";
    private static final int MAX_CANDIDATES = 4;

    private final BookshelfItemRepository bookshelfItemRepository;
    private final BookRepository bookRepository;
    private final BookCharacterRepository bookCharacterRepository;
    private final LetterRepository letterRepository;
    private final RoleplayAiService ai;
    private final TransactionTemplate transactionTemplate;

    public CharacterResponse resolve(User user, String isbn, String mode) {
        Preparation preparation = transactionTemplate.execute(transaction -> claimPreparation(user, isbn, mode));

        if (preparation.startedAt != null) {
            try {
                JsonNode candidates = ai.prepare(preparation.book);
                transactionTemplate.executeWithoutResult(transaction ->
                        completePreparation(isbn, preparation.startedAt, candidates));
            } catch (RuntimeException error) {
                try {
                    transactionTemplate.executeWithoutResult(transaction -> {
                        Book book = lockedBook(isbn);
                        if (samePreparation(book, preparation.startedAt)) {
                            book.updateCharacterStatus(FAILED, now());
                        }
                    });
                } catch (RuntimeException storageError) {
                    error.addSuppressed(storageError);
                }
                CharacterRequestException failure = new CharacterRequestException(HttpStatus.BAD_GATEWAY,
                        "CHARACTER_PREPARATION_FAILED", "인물을 준비하지 못했습니다. 다시 시도해 주세요.");
                failure.addSuppressed(error);
                throw failure;
            }
        }

        return transactionTemplate.execute(transaction -> response(user, isbn, mode));
    }

    @Transactional(readOnly = true)
    public CharacterContext context(User user, String isbn, Long id) {
        BookshelfItem shelf = findShelf(user, isbn);
        requireProgress(shelf);
        BookCharacter character = bookCharacterRepository.findByIdAndBookIsbn13(id, isbn)
                .orElseThrow(() -> new CharacterRequestException(HttpStatus.NOT_FOUND, "CHARACTER_NOT_FOUND",
                        "이 책에서 해당 인물을 찾지 못했습니다."));
        return new CharacterContext(shelf, character);
    }

    private Preparation claimPreparation(User user, String isbn, String mode) {
        BookshelfItem shelf = findShelf(user, isbn);
        if ("SELECT".equals(mode)) {
            requireProgress(shelf);
        }

        Book book = lockedBook(isbn);
        String status = book.getCharacterStatus();
        LocalDateTime now = now();
        if (READY.equals(status) || NO_CANDIDATES.equals(status)
                || (PREPARING.equals(status) && book.getCharacterStatusUpdatedAt().isAfter(now.minusMinutes(3)))) {
            return new Preparation(book, null);
        }

        // 인물 준비 중임을 먼저 기록하고 잠금을 푼 뒤 외부 API를 호출한다
        book.updateCharacterStatus(PREPARING, now);
        return new Preparation(book, now);
    }

    private void completePreparation(String isbn, LocalDateTime startedAt, JsonNode candidates) {
        Book book = lockedBook(isbn);
        if (!samePreparation(book, startedAt)) {
            return;
        }

        List<BookCharacter> profiles = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (JsonNode candidate : candidates) {
            if (profiles.size() == MAX_CANDIDATES) {
                break;
            }
            if (!candidate.path("confident").isBoolean() || !candidate.path("confident").asBoolean()) {
                continue;
            }
            String name = field(candidate, "name", 120);
            String intro = field(candidate, "intro", 500);
            String persona = field(candidate, "persona", 1600);
            JsonNode required = candidate.path("requiredReplies");
            if (name == null || intro == null || persona == null
                    || !required.isIntegralNumber() || !required.canConvertToInt()
                    || required.asInt() < 1 || required.asInt() > 3
                    || !names.add(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            profiles.add(new BookCharacter(book, profiles.size() + 1, name, intro, persona, required.asInt()));
        }
        bookCharacterRepository.saveAll(profiles);
        book.updateCharacterStatus(profiles.isEmpty() ? NO_CANDIDATES : READY, now());
    }

    private CharacterResponse response(User user, String isbn, String mode) {
        BookshelfItem shelf = findShelf(user, isbn);
        String status = shelf.getBook().getCharacterStatus();
        List<CharacterResponse.Item> characters = List.of();
        if (READY.equals(status) && "SELECT".equals(mode)) {
            characters = bookCharacterRepository.findByBookIsbn13OrderByDisplayOrderAsc(isbn).stream()
                    .map(character -> toItem(user, shelf, character))
                    .toList();
        }
        return new CharacterResponse(status, characters);
    }

    private CharacterResponse.Item toItem(User user, BookshelfItem shelf, BookCharacter character) {
        long count = letterRepository.countByUserAndBookshelfItemAndCharacterAndStatus(
                user, shelf, character, PUBLISHED);
        String active = letterRepository.findFirstByUserAndBookshelfItemAndCharacterAndStatusNotOrderByIdDesc(
                user, shelf, character, PUBLISHED).map(Letter::getId).map(Object::toString).orElse(null);
        String unread = letterRepository
                .findFirstByUserAndBookshelfItemAndCharacterAndStatusAndReadAtIsNullOrderByIdDesc(
                        user, shelf, character, PUBLISHED).map(Letter::getId).map(Object::toString).orElse(null);
        return new CharacterResponse.Item("SHARED_AI_PROFILE", character.getId().toString(),
                character.getName(), character.getIntro(), character.getRequiredReplies(), count,
                count >= character.getRequiredReplies(), active, unread);
    }

    private BookshelfItem findShelf(User user, String isbn) {
        return bookshelfItemRepository.findByUserAndBookIsbn13(user, isbn)
                .orElseThrow(() -> new CharacterRequestException(HttpStatus.NOT_FOUND, "BOOK_NOT_ON_SHELF",
                        "내 책장에 없는 책입니다."));
    }

    private Book lockedBook(String isbn) {
        return bookRepository.findLockedByIsbn13(isbn)
                .orElseThrow(() -> new CharacterRequestException(HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND",
                        "해당 판본을 찾지 못했습니다."));
    }

    private static void requireProgress(BookshelfItem shelf) {
        if (shelf.getProgressStage() == null) {
            throw new CharacterRequestException(HttpStatus.CONFLICT, "PROGRESS_REQUIRED", "읽은 지점을 먼저 남겨 주세요.");
        }
    }

    private static boolean samePreparation(Book book, LocalDateTime startedAt) {
        return PREPARING.equals(book.getCharacterStatus()) && startedAt.equals(book.getCharacterStatusUpdatedAt());
    }

    private static String field(JsonNode node, String key, int maxLength) {
        JsonNode value = node.path(key);
        if (!value.isTextual()) {
            return null;
        }
        String text = value.asText().strip();
        return text.isBlank() || text.length() > maxLength ? null : text;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    @Getter
    @AllArgsConstructor
    public static class CharacterContext {
        private final BookshelfItem shelf;
        private final BookCharacter character;
    }

    @AllArgsConstructor
    private static class Preparation {
        private final Book book;
        private final LocalDateTime startedAt;
    }
}
