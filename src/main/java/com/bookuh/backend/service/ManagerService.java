package com.bookuh.backend.service;

import com.bookuh.backend.dto.ManagerAction;
import com.bookuh.backend.dto.ManagerRequest;
import com.bookuh.backend.dto.ManagerResponse;
import com.bookuh.backend.dto.ShelfItemResponse;
import com.bookuh.backend.entity.ApiRequest;
import com.bookuh.backend.entity.BookshelfItem;
import com.bookuh.backend.entity.ManagerMessage;
import com.bookuh.backend.entity.User;
import com.bookuh.backend.repository.ApiRequestRepository;
import com.bookuh.backend.repository.BookshelfItemRepository;
import com.bookuh.backend.repository.ManagerMessageRepository;
import com.bookuh.backend.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.bookuh.backend.dto.ManagerMessagesResponse;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class ManagerService {

    private static final String REQUEST_KIND = "MANAGER_INTERPRET";
    private static final String REMOVE_SHELF_BOOK = "REMOVE_SHELF_BOOK";
    private static final Pattern SELECTED_BOOK_REFERENCE = Pattern.compile(
            "(?:^|\\s)(?:이\\s*책|선택한\\s*책)(?=$|\\s|[을를은는이가도])");
    private static final Pattern BULK_SHELF_REMOVAL = Pattern.compile(
            "책장(?:을|도)?\\s*비우|(?:모든|전체)\\s*(?:책|도서)"
                    + "|(?:책|도서)(?:을|를|들|들은|들을)?\\s*(?:전부|몽땅|모두|다)\\s*(?:빼|삭제|제거|꺼내|지워)");

    private static final Pattern TITLE_SUFFIX = Pattern.compile("\\s*(?:\\([^()]*\\)|\\[[^\\[\\]]*\\])$");
    private static final Pattern EDITION_LABEL = Pattern.compile(
            "개정판|초판본|양장본|문고판|보급판|특별판|한정판|기념판|증보판|전자책|리커버|hardcover|paperback|edition",
            Pattern.CASE_INSENSITIVE);

    // 북어에게 주는 역할 설명서. 모든 요청이 같은 내용을 쓴다
    private static final String MANAGER_PROMPT = loadPrompt("prompts/manager.txt");

    private final ApiRequestRepository apiRequestRepository;
    private final ManagerMessageRepository managerMessageRepository;
    private final ClaudeService claudeService;
    private final TokenService tokenService;
    private final ObjectMapper objectMapper;
    private final BookshelfService bookshelfService;
    private final BookshelfItemRepository bookshelfItemRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;

    // 북어에게 보낸 메시지를 처리한다
    public ManagerResponse talk(User user, ManagerRequest request) {
        byte[] payloadDigest = hashRequest(request);
        ApiRequest apiRequest = transactionTemplate.execute(transaction -> {
            User lockedUser = userRepository.findLockedById(user.getId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED, "사용자를 확인할 수 없습니다."));
            return acceptRequest(lockedUser, request, payloadDigest);
        });

        // 이미 끝난 요청이면 보관해 둔 결과를 그대로 돌려준다
        if (apiRequest.isSucceeded()) {
            return objectMapper.convertValue(apiRequest.getResultPayload(), ManagerResponse.class);
        }

        try {
            BookshelfItem selectedBook = request.getIsbn() == null ? null
                    : bookshelfItemRepository.findByUserAndBookIsbn13(user, request.getIsbn())
                            .orElseThrow(() -> new ResponseStatusException(
                                    HttpStatus.NOT_FOUND, "내 책장에 없는 책입니다."));
            ManagerResponse response = askManager(user, request);
            transactionTemplate.executeWithoutResult(transaction -> {
                ApiRequest stored = apiRequestRepository.findById(apiRequest.getId()).orElseThrow();
                saveConversation(user, selectedBook, request, stored, response);

                // 요청을 완료로 바꾸고, 같은 번호표가 다시 오면 돌려줄 결과를 보관한다
                stored.succeed(200, objectMapper.convertValue(
                        response, new TypeReference<Map<String, Object>>() {}));
            });
            return response;
        } catch (RuntimeException e) {
            // 실패하면 요청을 실패로 표시하고, 오류는 그대로 앱에 알린다
            markFailed(apiRequest.getId(), e);
            throw e;
        }
    }

    // 보낸 내용을 확인값으로 바꾼다
    private byte[] hashRequest(ManagerRequest request) {
        List<String> fields = Arrays.asList(request.getMessage(), request.getIsbn(),
                request.getClientTime().toString(), request.getTimeZone());
        try {
            return tokenService.hash(objectMapper.writeValueAsString(fields));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("요청 확인값을 만들 수 없습니다.", e);
        }
    }

    private void markFailed(Long requestId, RuntimeException originalError) {
        try {
            transactionTemplate.executeWithoutResult(transaction -> {
                ApiRequest stored = apiRequestRepository.findById(requestId).orElse(null);
                if (stored != null && stored.isProcessing()) {
                    stored.fail();
                }
            });
        } catch (RuntimeException storageError) {
            originalError.addSuppressed(storageError);
        }
    }

    // 처음 온 요청은 접수하고, 다시 온 요청은 상태를 확인한다
    private ApiRequest acceptRequest(User user, ManagerRequest request, byte[] payloadDigest) {
        // 같은 번호표로 이미 받은 요청이 있는지 찾는다
        ApiRequest apiRequest = apiRequestRepository
                .findByUserAndRequestKindAndClientRequestId(user, REQUEST_KIND, request.getOperationId())
                .orElse(null);

        if (apiRequest == null) {
            // 처음 온 요청이면 새로 접수한다
            return apiRequestRepository.save(new ApiRequest(
                    user, REQUEST_KIND, request.getOperationId(), payloadDigest));
        }

        // 같은 번호표인데 내용이 다르면 잘못된 요청이다
        if (!Arrays.equals(apiRequest.getPayloadDigest(), payloadDigest)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 요청 번호로 다른 내용이 왔습니다.");
        }
        // 아직 처리 중인 요청이면 끝날 때까지 기다리게 한다
        if (apiRequest.isProcessing()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 요청을 처리하고 있습니다.");
        }
        // 실패했던 요청이면 다시 처리할 준비를 한다
        if (apiRequest.isFailed()) {
            apiRequest.retry();
        }
        return apiRequest;
    }

    // 지난 대화와 이번 메시지를 북어에게 보내 응답을 받는다
    private ManagerResponse askManager(User user, ManagerRequest request) {
        List<ShelfItemResponse> shelf = bookshelfService.getShelf(user);
        // 이번 메시지에 현재 시각, 시간대, 선택한 책을 함께 적는다
        String userContent = """
                [현재 시각] %s
                [시간대] %s
                [선택한 책 ISBN] %s
                [내 책장]
                %s
                [사용자 메시지] %s
                """.formatted(request.getClientTime(), request.getTimeZone(),
                request.getIsbn(), describeShelf(shelf), request.getMessage());

        // 지난 대화를 불러오고, 이번 메시지를 맨 뒤에 붙인다
        List<Map<String, String>> messages = loadHistory(user);
        messages.add(Map.of("role", "user", "content", userContent));

        // 북어 안내문과 대화를 보내 답을 받고, 응답 모양으로 바꾼다
        String answer = claudeService.reply(MANAGER_PROMPT, messages);
        ManagerResponse response = parseAnswer(answer);

        // 어떤 요청의 결과인지 번호표를 붙이고, 작업마다 번호를 붙인다
        response.setOperationId(request.getOperationId());
        if (response.getActions() == null) {
            response.setActions(new ArrayList<>());
        }
        checkShelfRemoval(request, shelf, response);
        for (int i = 0; i < response.getActions().size(); i++) {
            response.getActions().get(i).setActionId(request.getOperationId() + ":" + (i + 1));
        }
        return response;
    }

    // 책장에서 뺄 대상을 확인하고 앱이 다시 물어볼 수 있게 책 정보를 돌려준다
    private void checkShelfRemoval(ManagerRequest request, List<ShelfItemResponse> shelf,
                                   ManagerResponse response) {
        List<ManagerAction> removals = response.getActions().stream()
                .filter(action -> REMOVE_SHELF_BOOK.equals(action.getType()))
                .toList();
        if (removals.isEmpty()) {
            return;
        }
        if (removals.size() > 1 || BULK_SHELF_REMOVAL.matcher(request.getMessage()).find()) {
            askAboutShelfRemoval(response, "책장에서 뺄 책을 한 권씩 말해 주세요.");
            return;
        }

        ManagerAction removal = removals.get(0);
        List<ShelfItemResponse> matches = findShelfRemovalCandidates(request, shelf, removal);
        if (matches.isEmpty()) {
            askAboutShelfRemoval(response, "책 제목을 확인해 다시 말하거나, 책 선택에서 고른 뒤 ‘이 책 책장에서 빼줘’라고 말해 주세요.");
            return;
        }
        if (matches.size() > 1) {
            askAboutShelfRemoval(response, "같은 제목의 책이 여러 권 있어요. 책 선택에서 판본을 고른 뒤 ‘이 책 책장에서 빼줘’라고 말해 주세요.");
            return;
        }

        ShelfItemResponse book = matches.get(0);
        removal.setIsbn(book.getIsbn());
        removal.setValues(Map.of("title", book.getTitle()));
        String confirmation = "아래에서 책장에서 뺄 책을 확인해 주세요.";
        String clarification = response.getClarifyingQuestion();
        response.setAssistantMessage(clarification == null ? confirmation : clarification + " " + confirmation);
    }

    private List<ShelfItemResponse> findShelfRemovalCandidates(ManagerRequest request,
                                                               List<ShelfItemResponse> shelf,
                                                               ManagerAction removal) {
        Object title = removal.getValues() == null ? null : removal.getValues().get("title");
        if (title == null && SELECTED_BOOK_REFERENCE.matcher(request.getMessage()).find()) {
            return shelf.stream().filter(book -> book.getIsbn().equals(request.getIsbn())).toList();
        }
        if (!(title instanceof String titleText)) {
            return List.of();
        }
        String normalizedTitle = normalizeTitle(titleText);
        String normalizedMessage = normalizeTitle(request.getMessage());
        if (normalizedTitle.isEmpty() || !normalizedMessage.contains(normalizedTitle)) {
            return List.of();
        }

        List<ShelfItemResponse> matches = shelf.stream()
                .filter(book -> normalizeTitle(book.getTitle()).equals(normalizedTitle)
                        || normalizeBaseTitle(book.getTitle()).equals(normalizedTitle))
                .toList();
        List<ShelfItemResponse> namedEditions = matches.stream()
                .filter(book -> !normalizeTitle(book.getTitle()).equals(normalizedTitle)
                        && normalizedMessage.contains(normalizeTitle(book.getTitle())))
                .toList();
        return namedEditions.isEmpty() ? matches : namedEditions;
    }

    private String normalizeTitle(String title) {
        return Normalizer.normalize(title, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private String normalizeBaseTitle(String title) {
        String editionTitle = Normalizer.normalize(title, Normalizer.Form.NFKC).strip();
        Matcher suffix = TITLE_SUFFIX.matcher(editionTitle);
        if (suffix.find() && EDITION_LABEL.matcher(suffix.group()).find()) {
            editionTitle = editionTitle.substring(0, suffix.start());
        }
        return normalizeTitle(editionTitle);
    }

    private void askAboutShelfRemoval(ManagerResponse response, String question) {
        response.getActions().removeIf(action -> REMOVE_SHELF_BOOK.equals(action.getType()));
        response.setClarifyingQuestion(question);
        response.setAssistantMessage(question);
    }

    // 사용자 말과 북어 말을 말풍선으로 저장한다
    private void saveConversation(User user, BookshelfItem selectedBook, ManagerRequest request,
                                  ApiRequest apiRequest, ManagerResponse response) {
        managerMessageRepository.saveAll(List.of(
                ManagerMessage.ofUser(user, selectedBook, apiRequest, request.getMessage()),
                ManagerMessage.ofAssistant(user, selectedBook, apiRequest, response.getAssistantMessage())));
    }

    // 북어가 책 제목으로 ISBN을 찾을 수 있게 책장을 한 줄씩 적는다
    private String describeShelf(List<ShelfItemResponse> shelf) {
        if (shelf.isEmpty()) {
            return "(비어 있음)";
        }
        return shelf.stream()
                .map(book -> "- %s / %s / %s / %s".formatted(
                        book.getTitle(), String.join(", ", book.getAuthors()),
                        book.getPublisher() == null ? "출판사 미상" : book.getPublisher(), book.getIsbn()))
                .collect(Collectors.joining("\n"));
    }

    // 앱에 보여줄 북어 대화를 한 페이지씩 가져온다
    @Transactional(readOnly = true)
    public ManagerMessagesResponse getMessages(User user, String before) {
        int pageSize = 20;
        PageRequest page = PageRequest.of(0, pageSize + 1,
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));

        List<ManagerMessage> recent;
        if (before == null) {
            recent = managerMessageRepository.findByUser(user, page);
        } else {
            long cursorId;
            try {
                cursorId = Long.parseLong(before);
            } catch (NumberFormatException e) {
                throw invalidMessageCursor();
            }
            if (cursorId < 1) {
                throw invalidMessageCursor();
            }

            ManagerMessage cursor = managerMessageRepository.findByUserAndId(user, cursorId)
                    .orElseThrow(this::invalidMessageCursor);
            recent = managerMessageRepository.findOlder(
                    user, cursor.getCreatedAt(), cursor.getId(), page);
        }

        int count = Math.min(pageSize, recent.size());
        String nextCursor = recent.size() > pageSize
                ? recent.get(count - 1).getId().toString()
                : null;

        List<ManagerMessagesResponse.Item> items = new ArrayList<>();
        for (int i = count - 1; i >= 0; i--) {
            ManagerMessage message = recent.get(i);
            String isbn = message.getBookshelfItem() == null
                    ? null
                    : message.getBookshelfItem().getBook().getIsbn13();
            items.add(new ManagerMessagesResponse.Item(
                    message.getId().toString(), message.getRole(), isbn, message.getBody(),
                    message.getCreatedAt().toInstant(ZoneOffset.UTC)));
        }
        return new ManagerMessagesResponse(items, nextCursor);
    }

    private ResponseStatusException invalidMessageCursor() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "이전 대화 기준이 올바르지 않습니다.");
    }
    // 이 사용자와 북어가 나눈 최근 대화를 오래된 순서로 가져온다
    private List<Map<String, String>> loadHistory(User user) {

        // 최근 말풍선 20개를 최신순으로 가져온다
        List<ManagerMessage> recent = managerMessageRepository.findByUser(
                user, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id")));

        // 최신순으로 받았으니 뒤에서부터 꺼내 오래된 순서로 담는다
        List<Map<String, String>> history = new ArrayList<>();
        for (int i = recent.size() - 1; i >= 0; i--) {
            ManagerMessage message = recent.get(i);
            history.add(Map.of(
                    "role", message.getRole().toLowerCase(),
                    "content", message.getBody()));
        }

        // 북어 말로 시작하면 빼서 사용자 말부터 시작하게 한다
        if (!history.isEmpty() && history.get(0).get("role").equals("assistant")) {
            history.remove(0);
        }
        return history;
    }

    // Claude가 보낸 JSON 글자를 ManagerResponse로 바꾼다
    private ManagerResponse parseAnswer(String answer) {
        String json = answer.strip();

        // 가끔 ```json ... ``` 으로 감싸서 오면 앞뒤 껍데기를 벗긴다
        if (json.startsWith("```")) {
            int start = json.indexOf('\n');
            int end = json.lastIndexOf("```");
            if (start >= 0 && end > start) {
                json = json.substring(start + 1, end).strip();
            }
        }

        ManagerResponse response;
        try {
            response = objectMapper.readValue(json, ManagerResponse.class);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 응답을 읽지 못했습니다.");
        }

        // 북어가 할 말이 비어 있으면 쓸 수 없는 응답으로 본다
        if (response == null || response.getAssistantMessage() == null
                || response.getAssistantMessage().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 답변이 비어 있습니다.");
        }
        return response;
    }

    // resources 폴더의 안내문 파일을 읽는다
    private static String loadPrompt(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("안내문 파일을 읽지 못했습니다: " + path, e);
        }
    }
}
