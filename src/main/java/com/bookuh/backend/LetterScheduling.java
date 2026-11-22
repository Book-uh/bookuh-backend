package com.bookuh.backend;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class LetterScheduling {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // 다른 편지의 응답을 생성하는 동안에도 준비된 편지를 공개한다
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("북어-편지-");
        return scheduler;
    }
}
