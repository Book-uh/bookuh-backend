package com.bookuh.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "bookuh.letters.worker-enabled", havingValue = "true", matchIfMissing = true)
public class LetterWorker {

    private final LetterService letterService;
    private final RoleplayAiService roleplayAiService;

    // 서버가 다시 켜져도 접수된 미완료 답장을 이어서 만든다
    @Scheduled(fixedDelayString = "${bookuh.letters.poll-millis:1000}")
    public void generateReply() {
        LetterService.ReplyJob job = letterService.nextReply();
        if (job == null) {
            return;
        }
        try {
            String reply = roleplayAiService.reply(job.getCharacter(), job.getStage(), job.getScene(),
                    (int) Math.min(job.getReplyCount(), Integer.MAX_VALUE), List.of(), job.getMessage()).getReply();
            if (reply == null || reply.isBlank()) {
                throw new IllegalStateException("편지 답장이 비어 있습니다.");
            }
            letterService.storeReply(job.getId(), reply);
        } catch (RuntimeException exception) {
            log.warn("편지 {}의 답장 생성에 실패했습니다: {}", job.getId(), exception.getClass().getSimpleName());
            letterService.failReply(job.getId());
        }
    }

    // 답장 생성과 별개로 공개 시각이 지난 편지를 확인한다
    @Scheduled(fixedDelayString = "${bookuh.letters.poll-millis:1000}")
    public void publishReady() {
        letterService.publishReady();
    }
}
