package com.bookuh.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CharacterResolveRequest {

    @NotBlank
    @Pattern(regexp = "PREPARE|SELECT")
    private String mode;
}
