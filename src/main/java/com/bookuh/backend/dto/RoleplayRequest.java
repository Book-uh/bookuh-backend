package com.bookuh.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleplayRequest {

    @NotBlank
    @Size(max = 128)
    private String turnId;

    @NotBlank
    @Pattern(regexp = "\\d{13}")
    private String isbn;

    @NotNull
    @Valid
    private CharacterRef character;

    @NotBlank
    @Size(max = 2000)
    private String message;

    @Getter
    @Setter
    public static class CharacterRef {
        @NotBlank
        @Pattern(regexp = "SHARED_AI_PROFILE")
        private String source;

        @NotNull
        @Positive
        private Long characterId;
    }
}
