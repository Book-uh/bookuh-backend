package com.bookuh.backend.service;

import com.bookuh.backend.entity.Book;
import com.bookuh.backend.entity.BookCharacter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleplayAiService {

    private static final String CHARACTER_PROMPT = readPrompt("character-preparation.txt");
    private static final String ROLEPLAY_PROMPT = readPrompt("roleplay.txt");
    private static final Map<String, Object> ROLEPLAY_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "reply", Map.of("type", "string"),
                    "withinBoundary", Map.of("type", "boolean"),
                    "includeInFutureContext", Map.of("type", "boolean")
            ),
            "required", List.of("reply", "withinBoundary", "includeInFutureContext"),
            "additionalProperties", false
    );

    private final ClaudeService claudeService;
    private final ObjectMapper objectMapper;

    public JsonNode prepare(Book book) {
        String data = json(Map.of("title", book.getTitle(), "authors", book.getAuthors(), "isbn", book.getIsbn13()));
        JsonNode candidates = parseObject(claudeService.reply(CHARACTER_PROMPT, List.of(message("user", data))))
                .path("characters");
        if (!candidates.isArray()) {
            throw invalidResponse();
        }
        return candidates;
    }

    public Reply reply(BookCharacter character, String stage, String scene, int replyCount,
                       List<Map<String, String>> history, String message) {
        Map<String, Object> context = new LinkedHashMap<>();
        Book book = character.getBook();
        context.put("title", book.getTitle());
        context.put("authors", book.getAuthors());
        context.put("character", character.getName());
        context.put("persona", character.getPersona());
        context.put("readingStage", stage);
        context.put("readerScene", scene);
        context.put("publishedReplyCount", replyCount);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("user", "이 대화에서 참조할 맥락 자료: " + json(context)));
        messages.addAll(history);
        messages.add(message("user", message));

        JsonNode answer = parseObject(claudeService.replyWithSchema(ROLEPLAY_PROMPT, messages, ROLEPLAY_SCHEMA));

        // 정보 범위를 지켰다는 판정이 빠졌을 때는 사건에 대한 답을 제공하지 않는다
        if (!answer.path("withinBoundary").isBoolean() || !answer.path("withinBoundary").asBoolean()) {
            return new Reply("그 이야기는 내가 아는 데까지만 들려줄 수 있어. 어느 부분이 궁금해?", false);
        }
        JsonNode text = answer.path("reply");
        if (!text.isTextual() || text.asText().isBlank() || text.asText().length() > 4000) {
            log.warn("AI 역할극 응답의 대사 필드를 읽지 못했습니다.");
            throw invalidResponse();
        }
        return new Reply(text.asText().strip(),
                answer.path("includeInFutureContext").isBoolean()
                        && answer.path("includeInFutureContext").asBoolean());
    }

    private JsonNode parseObject(String rawResponse) {
        String response = rawResponse.strip();
        if (response.startsWith("```") && response.endsWith("```")) {
            int firstLine = response.indexOf('\n');
            if (firstLine >= 0) {
                response = response.substring(firstLine + 1, response.length() - 3).strip();
            }
        }
        try {
            JsonNode node = objectMapper.readTree(response);
            if (node == null || !node.isObject()) {
                log.warn("AI 인물 응답이 JSON 객체가 아닙니다.");
                throw invalidResponse();
            }
            return node;
        } catch (JsonProcessingException e) {
            log.warn("AI 인물 응답의 JSON 형식을 읽지 못했습니다.");
            throw invalidResponse();
        }
    }

    private String json(Map<String, ?> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("인물에게 전달할 정보를 준비하지 못했습니다.", e);
        }
    }

    private static Map<String, String> message(String role, String content) {
        return Map.of("role", role, "content", content);
    }

    private static ResponseStatusException invalidResponse() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "인물의 AI 응답을 확인하지 못했습니다.");
    }

    private static String readPrompt(String fileName) {
        try {
            return new ClassPathResource("prompts/" + fileName).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class Reply {
        private final String reply;
        private final boolean includeInFutureContext;
    }
}
