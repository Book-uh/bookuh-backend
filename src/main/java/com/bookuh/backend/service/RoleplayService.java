package com.bookuh.backend.service;

import com.bookuh.backend.dto.RoleplayRequest;
import com.bookuh.backend.dto.RoleplayResponse;
import com.bookuh.backend.entity.BookCharacter;
import com.bookuh.backend.entity.BookshelfItem;
import com.bookuh.backend.entity.ChatTurn;
import com.bookuh.backend.entity.User;
import com.bookuh.backend.repository.ChatTurnRepository;
import com.bookuh.backend.repository.LetterRepository;
import com.bookuh.backend.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RoleplayService {

    private static final String PUBLISHED = "PUBLISHED";

    private final CharacterService characterService;
    private final ChatTurnRepository chatTurnRepository;
    private final LetterRepository letterRepository;
    private final UserRepository userRepository;
    private final RoleplayAiService ai;
    private final TransactionTemplate transactionTemplate;

    public RoleplayResponse reply(User user, RoleplayRequest request) {
        ReplyWork work = transactionTemplate.execute(transaction -> accept(user, request));
        ChatTurn turn = work.turn;
        if (ChatTurn.SUCCEEDED.equals(turn.getStatus())) {
            return toReply(turn);
        }

        try {
            RoleplayAiService.Reply generated = ai.reply(work.character, turn.getProgressStage(),
                    turn.getProgressScene(), work.replyCount, work.history, turn.getMessage());
            return transactionTemplate.execute(transaction -> {
                ChatTurn stored = chatTurnRepository.findLockedById(turn.getId()).orElseThrow();
                if (sameAttempt(stored, turn.getUpdatedAt())) {
                    stored.succeed(generated.getReply(), generated.isIncludeInFutureContext());
                    return toReply(stored);
                }
                if (ChatTurn.SUCCEEDED.equals(stored.getStatus())) {
                    return toReply(stored);
                }
                throw busy();
            });
        } catch (RuntimeException error) {
            try {
                transactionTemplate.executeWithoutResult(transaction ->
                        chatTurnRepository.findLockedById(turn.getId()).ifPresent(stored -> {
                            if (sameAttempt(stored, turn.getUpdatedAt())) {
                                stored.fail();
                            }
                        }));
            } catch (RuntimeException storageError) {
                error.addSuppressed(storageError);
            }
            throw error;
        }
    }

    @Transactional(readOnly = true)
    public RoleplayResponse.History turns(User user, String isbn, Long characterId) {
        CharacterService.CharacterContext context = characterService.context(user, isbn, characterId);
        List<RoleplayResponse.Item> items = chatTurnRepository
                .findByUserAndBookshelfItemAndCharacterOrderByIdAsc(user, context.getShelf(), context.getCharacter())
                .stream().map(this::toHistoryItem).toList();
        return new RoleplayResponse.History(items);
    }

    private ReplyWork accept(User user, RoleplayRequest request) {
        User lockedUser = userRepository.findLockedById(user.getId()).orElseThrow(() ->
                new CharacterRequestException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "사용자를 확인할 수 없습니다."));
        CharacterService.CharacterContext context = characterService
                .context(lockedUser, request.getIsbn(), request.getCharacter().getCharacterId());
        BookshelfItem shelf = context.getShelf();
        BookCharacter character = context.getCharacter();

        ChatTurn turn = chatTurnRepository.findByUserAndClientTurnId(lockedUser, request.getTurnId()).orElse(null);
        String message = request.getMessage().strip();
        if (turn != null) {
            if (!turn.getBookshelfItem().getId().equals(shelf.getId())
                    || !turn.getCharacter().getId().equals(character.getId())
                    || !turn.getMessage().equals(message)) {
                throw new CharacterRequestException(HttpStatus.CONFLICT, "ACTION_CONFLICT",
                        "같은 대화 요청 번호로 다른 내용이 왔습니다.");
            }
            if (ChatTurn.SUCCEEDED.equals(turn.getStatus())) {
                return new ReplyWork(turn, character, 0, List.of());
            }
        }

        int count = (int) Math.min(Integer.MAX_VALUE, letterRepository.countByUserAndBookshelfItemAndCharacterAndStatus(
                lockedUser, shelf, character, PUBLISHED));
        if (count < character.getRequiredReplies()) {
            throw new CharacterRequestException(HttpStatus.CONFLICT, "CHAT_LOCKED",
                    "편지 답장을 받은 뒤 채팅할 수 있습니다.");
        }
        if (turn == null) {
            turn = chatTurnRepository.save(new ChatTurn(lockedUser, shelf, character, request.getTurnId(), message));
        } else {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
            if (ChatTurn.PROCESSING.equals(turn.getStatus()) && turn.getUpdatedAt().isAfter(now.minusMinutes(3))) {
                throw busy();
            }
            turn.start(now);
        }

        // 현재 요청과 같은 진도에서 안전하다고 표시된 이전 문답만 가져온다
        List<ChatTurn> recent = chatTurnRepository.findRecentContext(
                lockedUser, shelf, character, turn.getProgressVersion(), turn.getId(), PageRequest.of(0, 10));
        List<Map<String, String>> history = new ArrayList<>();
        for (int i = recent.size() - 1; i >= 0; i--) {
            ChatTurn past = recent.get(i);
            history.add(Map.of("role", "user", "content", past.getMessage()));
            history.add(Map.of("role", "assistant", "content", past.getReply()));
        }
        return new ReplyWork(turn, character, count, history);
    }

    private RoleplayResponse toReply(ChatTurn turn) {
        return new RoleplayResponse(turn.getClientTurnId(), turn.getReply(), turn.isIncludeInFutureContext());
    }

    private RoleplayResponse.Item toHistoryItem(ChatTurn turn) {
        return new RoleplayResponse.Item(turn.getClientTurnId(), turn.getStatus(), turn.getMessage(), turn.getReply(),
                turn.getCreatedAt().toInstant(ZoneOffset.UTC));
    }

    private static boolean sameAttempt(ChatTurn turn, LocalDateTime startedAt) {
        return ChatTurn.PROCESSING.equals(turn.getStatus()) && startedAt.equals(turn.getUpdatedAt());
    }

    private static CharacterRequestException busy() {
        return new CharacterRequestException(HttpStatus.CONFLICT, "TURN_PROCESSING", "같은 대화를 처리하고 있습니다.");
    }

    @AllArgsConstructor
    private static class ReplyWork {
        private final ChatTurn turn;
        private final BookCharacter character;
        private final int replyCount;
        private final List<Map<String, String>> history;
    }
}
