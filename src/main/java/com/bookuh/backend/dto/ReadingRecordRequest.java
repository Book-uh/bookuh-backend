package com.bookuh.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ReadingRecordRequest {
    @NotBlank
    @Size(max = 128)
    private String actionId;

    @NotBlank
    @Pattern(regexp = "\\d{13}")
    private String isbn;

    @NotNull
    private LocalDate readingDate;

    @Pattern(regexp = "DAY|NIGHT")
    private String timeBand;

    @Positive
    private Integer durationMinutes;

    @Positive
    private Integer pagesRead;

    @Pattern(regexp = "UNSPECIFIED|EXPLICIT_ADDITIONAL")
    private String additionIntent;
}
