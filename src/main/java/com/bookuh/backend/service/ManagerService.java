package com.bookuh.backend.service;

import com.bookuh.backend.dto.ManagerRequest;
import com.bookuh.backend.dto.ManagerResponse;
import com.bookuh.backend.dto.ShelfItemResponse;
import com.bookuh.backend.entity.ApiRequest;
import com.bookuh.backend.entity.ManagerMessage;
import com.bookuh.backend.entity.User;
import com.bookuh.backend.repository.ApiRequestRepository;
import com.bookuh.backend.repository.ManagerMessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManagerService {

    private static final String REQUEST_KIND = "MANAGER_INTERPRET";

    // 북어에게 주는 역할 설명서. 모든 요청이 같은 내용을 쓴다
    private static final String MANAGER_PROMPT = loadPrompt("prompts/manager.txt");

    private final ApiRequestRepository apiRequestRepository;
    private final ManagerMessageRepository managerMessageRepository;
    private final ClaudeService claudeService;
    private final TokenService tokenService;
    private final ObjectMapper objectMapper;
    private final BookshelfService bookshelfService;

    // 북어에게 보낸 메시지를 처리한다
    public ManagerResponse talk(User user, ManagerRequest request) {
        ApiRequest apiRequest = acceptRequest(user, request);

        // 이미 끝난 요청이면 보관해 둔 결과를 그대로 돌려준다
        if (apiRequest.isSucceeded()) {
            return objectMapper.convertValue(apiRequest.getResultPayload(), ManagerResponse.class);
        }

        ManagerResponse response = askManager(user, request, apiRequest);
        saveConversation(user, request, apiRequest, response);

        // 요청을 완료로 바꾸고, 같은 번호표가 다시 오면 돌려줄 결과를 보관한다
        apiRequest.succeed(200, objectMapper.convertValue(response, new TypeReference<Map<String, Object>>() {}));
        apiRequestRepository.save(apiRequest);

        return response;
    }

    // 처음 온 요청은 접수하고, 다시 온 요청은 상태를 확인한다
    private ApiRequest acceptRequest(User user, ManagerRequest request) {
        // 같은 번호표로 이미 받은 요청이 있는지 찾는다
        ApiRequest apiRequest = apiRequestRepository
                .findByUserAndRequestKindAndClientRequestId(user, REQUEST_KIND, request.getOperationId())
                .orElse(null);

        // 보낸 내용을 확인값으로 바꾼다
        byte[] payloadDigest = tokenService.hash(request.getMessage());

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
            apiRequestRepository.save(apiRequest);
        }
        return apiRequest;
    }

    // 지난 대화와 이번 메시지를 북어에게 보내 응답을 받는다
    private ManagerResponse askManager(User user, ManagerRequest request, ApiRequest apiRequest) {
        // 이번 메시지에 현재 시각, 시간대, 선택한 책을 함께 적는다
        String userContent = """
                [현재 시각] %s
                [시간대] %s
                [선택한 책 ISBN] %s
                [내 책장]
                %s
                [사용자 메시지] %s
                """.formatted(request.getClientTime(), request.getTimeZone(),
                request.getIsbn(), describeShelf(user), request.getMessage());

        // 지난 대화를 불러오고, 이번 메시지를 맨 뒤에 붙인다
        List<Map<String, String>> messages = loadHistory(user);
        messages.add(Map.of("role", "user", "content", userContent));

        // 북어 안내문과 대화를 보내 답을 받고, 응답 모양으로 바꾼다
        ManagerResponse response;
        try {
            String answer = claudeService.reply(MANAGER_PROMPT, messages);
            response = parseAnswer(answer);
        } catch (RuntimeException e) {
            // 실패하면 요청을 실패로 표시하고, 오류는 그대로 앱에 알린다
            apiRequest.fail();
            apiRequestRepository.save(apiRequest);
            throw e;
        }

        // 어떤 요청의 결과인지 번호표를 붙이고, 작업마다 번호를 붙인다
        response.setOperationId(request.getOperationId());
        if (response.getActions() == null) {
            response.setActions(new ArrayList<>());
        }
        for (int i = 0; i < response.getActions().size(); i++) {
            response.getActions().get(i).setActionId(request.getOperationId() + ":" + (i + 1));
        }
        return response;
    }

    // 사용자 말과 북어 말을 말풍선으로 저장한다
    private void saveConversation(User user, ManagerRequest request,
                                  ApiRequest apiRequest, ManagerResponse response) {
        managerMessageRepository.saveAll(List.of(
                ManagerMessage.ofUser(user, null, apiRequest, request.getMessage()),
                ManagerMessage.ofAssistant(user, null, apiRequest, response.getAssistantMessage())));
    }

    // 북어가 책 제목으로 ISBN을 찾을 수 있게 책장을 한 줄씩 적는다
    private String describeShelf(User user) {
        List<ShelfItemResponse> shelf = bookshelfService.getShelf(user);
        if (shelf.isEmpty()) {
            return "(비어 있음)";
        }
        return shelf.stream()
                .map(book -> "- %s / %s / %s".formatted(
                        book.getTitle(), String.join(", ", book.getAuthors()), book.getIsbn13()))
                .collect(Collectors.joining("\n"));
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
