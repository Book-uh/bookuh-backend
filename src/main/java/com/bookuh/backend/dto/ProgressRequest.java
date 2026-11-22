package com.bookuh.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProgressRequest {
    @NotBlank
    @Size(max = 128)
    private String actionId;

    @NotNull
    @PositiveOrZero
    private Integer baseProgressVersion;

    @NotBlank
    @Pattern(regexp = "START|SCENE|FINISHED")
    private String stage;

    private String scene;

    @NotBlank
    private String timeZone;
}