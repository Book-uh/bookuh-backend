package com.bookuh.backend.controller;

import com.bookuh.backend.dto.ReadingStatsResponse;
import com.bookuh.backend.service.AuthService;
import com.bookuh.backend.service.ReadingStatsService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reading-stats")
public class ReadingStatsController {

    private final AuthService authService;
    private final ReadingStatsService readingStatsService;

    @GetMapping
    public ReadingStatsResponse get(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("period") @Pattern(regexp = "WEEK|MONTH") String period,
            @RequestParam("timeZone") @NotBlank String timeZone) {
        return readingStatsService.get(authService.authenticateBearer(authorization), period, timeZone);
    }
}
