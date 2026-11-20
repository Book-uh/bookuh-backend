package com.bookuh.backend.service;

import com.bookuh.backend.dto.ManagerRequest;
import com.bookuh.backend.dto.ManagerResponse;
import com.bookuh.backend.entity.ApiRequest;
import com.bookuh.backend.entity.ManagerMessage;
import com.bookuh.backend.entity.User;
import com.bookuh.backend.repository.ApiRequestRepository;
import com.bookuh.backend.repository.ManagerMessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ManagerService {

    private final ApiRequestRepository apiRequestRepository;
    private final ManagerMessageRepository managerMessageRepository;
    private final ClaudeService claudeService;
    private final TokenService tokenService;
    private final ObjectMapper objectMapper;


    // 북어에게 주는 역할 설명서. 모든 요청이 같은 내용을 쓴다
    private static final String MANAGER_PROMPT = """
            너는 독서 앱 '북어'의 독서 매니저 북어다.
            사용자의 책장, 읽은 장면(진도), 독서 시간과 페이지, 독서 통계를 관리한다.
            소설 속 인물을 연기하지 않는다. 인물과의 대화는 다른 기능이 맡는다.
            말투는 친근하고 짧게, 한두 문장으로 한다.
            
            반드시 아래 모양의 JSON 하나만 출력한다. 다른 설명이나 코드 블록 표시는 붙이지 않는다.
            {"clarifyingQuestion": 문자열 또는 null, "assistantMessage": 문자열, "actions": [작업들]}
            
            작업 하나의 모양: {"type": 작업 유형, "isbn": 문자열 또는 null, "values": {값들}}
            작업 번호(actionId)는 넣지 않는다. 서버가 붙인다.
            isbn은 요청에 선택한 책이 있으면 그 값을 쓰고, 없으면 null로 둔다.
            
            작업 유형과 values:
            - SET_PROGRESS: stage(START, SCENE, FINISHED), scene(SCENE일 때 사용자가 말한 장면, 아니면 null)
            - ADD_READING_RECORD: readingDate(YYYY-MM-DD), timeBand(DAY, NIGHT, null), durationMinutes, pagesRead, additionIntent(EXPLICIT_ADDITIONAL 또는 UNSPECIFIED)
            - UPDATE_READING_RECORD: match(찾을 날짜와 기존 값), changes(바꿀 값)
            - DELETE_READING_RECORD: match(찾을 날짜와 독서량)
            - SET_READING_STATUS: status(NOT_STARTED, READING, FINISHED)
            - SEARCH_BOOK: query(제목이나 저자)
            - RECOMMEND_BOOK: title, author, reason
            - QUERY_STATS: period(WEEK, MONTH)
            
            규칙:
            - 한 문장에 요청이 여러 개면 작업을 각각 만든다.
            - 사용자가 말하지 않은 책, 날짜, 시간, 페이지를 지어내지 않는다. 필요한 값이 모호하면 clarifyingQuestion으로 한 가지만 묻는다.
            - "어제", "어젯밤"은 요청에 적힌 현재 시각과 시간대로 실제 날짜를 계산한다.
            - 장면만 말하면 진도만 기록하고, 시간이나 페이지를 추정하지 않는다.
            - "20분 더 읽었어"처럼 새로 읽은 것이 분명하면 additionIntent를 EXPLICIT_ADDITIONAL로, 아니면 UNSPECIFIED로 둔다.
            - 저장을 마쳤다고 말하지 않는다. 저장 완료는 서버가 확인한 뒤에 앱이 알린다.
            - assistantMessage에는 북어가 사용자에게 할 말을 항상 한두 문장으로 쓴다. 확인 질문이 있으면 그 질문도 assistantMessage에 함께 쓴다.
            - 짧은 잡담에는 가볍게 공감하고 actions는 빈 배열로 둔다.
            - 사용자가 기분이 좋지 않다고 하면 짧게 공감하고 "책 추천해줄까?"라고만 묻는다. 바로 추천하지 않는다.
            - 사용자가 추천을 직접 요청하거나 추천 제안에 동의했을 때만 RECOMMEND_BOOK을 문학 작품 한 권으로 만든다.
            """;

    public ManagerService(ApiRequestRepository apiRequestRepository,
                          ManagerMessageRepository managerMessageRepository,
                          ClaudeService claudeService,
                          TokenService tokenService,
                          ObjectMapper objectMapper) {
        this.apiRequestRepository = apiRequestRepository;
        this.managerMessageRepository = managerMessageRepository;
        this.claudeService = claudeService;
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    // 북어에게 보낸 메시지를 처리한다
    public ManagerResponse talk(User user, ManagerRequest request) {

        // 같은 번호표로 이미 받은 요청이 있는지 찾는다
        ApiRequest apiRequest = apiRequestRepository
                .findByUserAndRequestKindAndClientRequestId(
                        user, "MANAGER_INTERPRET", request.getOperationId())
                .orElse(null);

        // 처음 온 요청이면 새로 접수한다
        if (apiRequest == null) {
            byte[] payloadDigest = tokenService.hash(request.getMessage());
            apiRequest = apiRequestRepository.save(new ApiRequest(
                    user, "MANAGER_INTERPRET", request.getOperationId(), payloadDigest));
        }

        // 이번 메시지에 현재 시각, 시간대, 선택한 책을 함께 적는다
        String userContent = """
                [현재 시각] %s
                [시간대] %s
                [선택한 책 ISBN] %s
                [사용자 메시지] %s
                """.formatted(request.getClientTime(), request.getTimeZone(),
                request.getIsbn(), request.getMessage());

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

        // 사용자 말과 북어 말을 말풍선으로 저장한다
        managerMessageRepository.save(new ManagerMessage(
                user, null, apiRequest, (short) 1, "USER", request.getMessage()));
        managerMessageRepository.save(new ManagerMessage(
                user, null, apiRequest, (short) 2, "ASSISTANT", response.getAssistantMessage()));

        // 요청을 완료로 바꾸고, 같은 번호표가 다시 오면 돌려줄 결과를 보관한다
        apiRequest.succeed(200, objectMapper.convertValue(response, new TypeReference<Map<String, Object>>() {}));
        apiRequestRepository.save(apiRequest);

        return response;
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
}
