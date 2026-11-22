package com.bookuh.backend.service;

import com.bookuh.backend.dto.LetterRequests;
import com.bookuh.backend.dto.LetterResponse;
import com.bookuh.backend.entity.ApiRequest;
import com.bookuh.backend.entity.BookCharacter;
import com.bookuh.backend.entity.BookshelfItem;
import com.bookuh.backend.entity.Letter;
import com.bookuh.backend.entity.User;
import com.bookuh.backend.repository.ApiRequestRepository;
import com.bookuh.backend.repository.LetterRepository;
import com.bookuh.backend.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LetterService {

    private static final String RETRY_KIND = "LETTER_RETRY";

    private final CharacterService characterService;
    private final LetterRepository letterRepository;
    private final UserRepository userRepository;
    private final ApiRequestRepository apiRequestRepository;
    private final TokenService tokenService;

    @Value("${bookuh.letters.release-seconds:120}")
    private long releaseSeconds;

    @Transactional
    public LetterResponse send(User user, LetterRequests.Send request) {
        User owner = lockUser(user);
        String message = request.getMessage().strip();
        Letter previous = letterRepository.findByUserAndClientLetterId(owner, request.getClientLetterId())
                .orElse(null);
        if (previous != null) {
            if (!previous.getBookshelfItem().getBook().getIsbn13().equals(request.getIsbn())
                    || !previous.getCharacter().getId().equals(request.getCharacterId())
                    || !previous.getMessage().equals(message)) {
                throw new LetterConflict("ACTION_CONFLICT");
            }
            return toResponse(previous);
        }

        CharacterService.CharacterContext context = characterService.context(
                owner, request.getIsbn(), request.getCharacterId());
        BookshelfItem shelf = context.getShelf();
        BookCharacter character = context.getCharacter();
        if (letterRepository.findFirstByUserAndBookshelfItemAndCharacterAndStatusNotOrderByIdDesc(
                owner, shelf, character, Letter.PUBLISHED).isPresent()) {
            throw new LetterConflict("LETTER_PENDING");
        }
        if (letterRepository.existsByBookshelfItemAndCharacterAndMessage(shelf, character, message)) {
            throw new LetterConflict("LETTER_DUPLICATE");
        }

        return toResponse(letterRepository.save(new Letter(
                shelf, character, request.getClientLetterId(), message, now())));
    }

    @Transactional(readOnly = true)
    public LetterResponse get(User user, long id) {
        return toResponse(ownLetter(user, id));
    }

    @Transactional(readOnly = true)
    public LetterResponse.Inbox list(User user, String isbn, Long characterId, boolean kept) {
        return new LetterResponse.Inbox(letterRepository.findInbox(user, isbn, characterId, kept)
                .stream().map(this::toResponse).toList());
    }

    @Transactional
    public LetterResponse retry(User user, long id, String retryId) {
        User owner = lockUser(user);
        Letter letter = ownLetter(owner, id);
        byte[] digest = tokenService.hash(Long.toString(id));
        ApiRequest previous = apiRequestRepository
                .findByUserAndRequestKindAndClientRequestId(owner, RETRY_KIND, retryId)
                .orElse(null);
        if (previous != null) {
            if (!Arrays.equals(previous.getPayloadDigest(), digest)) {
                throw new LetterConflict("ACTION_CONFLICT");
            }
            return toResponse(letter);
        }
        if (letter.isFailed()) {
            letter.retry();
        }
        ApiRequest accepted = new ApiRequest(owner, RETRY_KIND, retryId, digest);
        accepted.succeed(HttpStatus.ACCEPTED.value(), Map.of("letterId", Long.toString(id)));
        apiRequestRepository.save(accepted);
        return toResponse(letter);
    }

    @Transactional
    public void read(User user, long id) {
        Letter letter = publishedLetter(user, id);
        letter.markRead(now());
    }

    @Transactional
    public void keep(User user, long id) {
        Letter letter = publishedLetter(user, id);
        letter.keep(now());
    }

    @Transactional(readOnly = true)
    public ReplyJob nextReply() {
        List<Long> waiting = letterRepository.findWaitingReplyIds(PageRequest.of(0, 1));
        if (waiting.isEmpty()) {
            return null;
        }
        return letterRepository.findWithCharacterById(waiting.get(0))
                .filter(Letter::isAwaitingReply)
                .map(letter -> new ReplyJob(letter.getId(), letter.getCharacter(),
                        letter.getProgressStage(), letter.getProgressScene(), letter.getMessage(),
                        letterRepository.countByUserAndBookshelfItemAndCharacterAndStatus(
                                letter.getUser(), letter.getBookshelfItem(), letter.getCharacter(), Letter.PUBLISHED)))
                .orElse(null);
    }

    @Transactional
    public void storeReply(long id, String reply) {
        Letter letter = letterRepository.findById(id).orElseThrow();
        if (letter.isAwaitingReply()) {
            letter.prepareReply(reply);
        }
    }

    @Transactional
    public void failReply(long id) {
        letterRepository.findById(id)
                .filter(Letter::isAwaitingReply)
                .ifPresent(Letter::failReply);
    }

    @Transactional
    public void publishReady() {
        LocalDateTime now = now();
        letterRepository.publishReady(now, now.minusSeconds(releaseSeconds));
    }

    private User lockUser(User user) {
        return userRepository.findLockedById(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
    }

    private Letter ownLetter(User user, long id) {
        return letterRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "편지를 찾을 수 없습니다."));
    }

    private Letter publishedLetter(User user, long id) {
        Letter letter = ownLetter(lockUser(user), id);
        if (!letter.isPublished()) {
            throw new LetterConflict("LETTER_NOT_PUBLISHED");
        }
        return letter;
    }

    private LetterResponse toResponse(Letter letter) {
        return new LetterResponse(letter.getId().toString(), letter.getBookshelfItem().getBook().getIsbn13(),
                letter.getCharacter().getId().toString(), letter.getCharacter().getName(),
                letter.getProgressStage(), letter.getProgressScene(), letter.getStatus(),
                asInstant(letter.getAcceptedAt()), asInstant(letter.getPublishedAt()), asInstant(letter.getReadAt()),
                letter.getKeptAt() != null, letter.getMessage(), letter.isPublished() ? letter.getReply() : null);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private Instant asInstant(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC);
    }

    @Getter
    @AllArgsConstructor
    public static class ReplyJob {
        private final long id;
        private final BookCharacter character;
        private final String stage;
        private final String scene;
        private final String message;
        private final long replyCount;
    }

    public static class LetterConflict extends RuntimeException {
        public LetterConflict(String code) {
            super(code);
        }
    }
}
