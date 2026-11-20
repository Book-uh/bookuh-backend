package com.bookuh.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ClaudeService {

    private static final String BASE_URL = "https://api.anthropic.com";
    private static final String API_VERSION = "2026-10-21";
    private static final int MAX_TOKENS = 2048;

    private final RestClient restClient;
    private final String model;

    // 설정값으로 Claude에 요청할 도구를 만든다
    public ClaudeService(@Value("${anthropic.api-key}") String apiKey,
                         @Value("${anthropic.model}") String model) {
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("anthropic-version", API_VERSION)
                .build();
    }

    // 역할 설정과 대화 목록을 보내고 답변을 받는다
    public String reply(String systemPrompt, List<Map<String, String>> messages) {
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", MAX_TOKENS,
                "system", systemPrompt,
                "messages", messages,
                "thinking", Map.of("type", "adaptive"),
                "output_config", Map.of("effort", "medium")
        );

        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/v1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI 응답을 불러오지 못했습니다.", exception);
        }

        if (response == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 응답이 비어 있습니다.");
        }

        log.info("Claude 사용량: 입력={}, 출력={}",
                response.path("usage").path("input_tokens").asInt(),
                response.path("usage").path("output_tokens").asInt());

        if (response.path("stop_reason").asText().equals("max_tokens")) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 답변이 완성되지 않았습니다.");
        }

        StringBuilder answer = new StringBuilder();
        for (JsonNode block : response.path("content")) {
            if (block.path("type").asText().equals("text")) {
                if (!answer.isEmpty()) {
                    answer.append("\n");
                }
                answer.append(block.path("text").asText());
            }
        }
        if (answer.toString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 답변을 읽지 못했습니다.");
        }
        return answer.toString().strip();
    }
}
