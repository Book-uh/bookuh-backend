package com.bookuh.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

public class LetterRequests {

    @Getter
    @Setter
    public static class Send {
        @NotBlank
        @Size(max = 128)
        private String clientLetterId;

        @NotBlank
        @Pattern(regexp = "\\d{13}")
        private String isbn;

        @NotNull
        @Positive
        private Long characterId;

        @NotBlank
        @Size(max = 4000)
        private String message;
    }

    @Getter
    @Setter
    public static class Retry {
        @NotBlank
        @Size(max = 128)
        private String retryId;
    }
}
