package com.bookuh.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReadingStatusRequest {
    @NotBlank
    @Size(max = 128)
    private String actionId;

    @NotBlank
    @Pattern(regexp = "NOT_STARTED|READING|FINISHED")
    private String status;

    @NotBlank
    private String timeZone;
}
