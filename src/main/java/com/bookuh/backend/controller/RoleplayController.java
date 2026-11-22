package com.bookuh.backend.controller;

import com.bookuh.backend.dto.RoleplayRequest;
import com.bookuh.backend.dto.RoleplayResponse;
import com.bookuh.backend.entity.User;
import com.bookuh.backend.service.AuthService;
import com.bookuh.backend.service.RoleplayService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/roleplay")
public class RoleplayController {

    private final AuthService authService;
    private final RoleplayService roleplayService;

    @PostMapping("/replies")
    public RoleplayResponse reply(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody RoleplayRequest request) {
        User user = authService.authenticateBearer(authorization);
        return roleplayService.reply(user, request);
    }

    @GetMapping("/turns")
    public RoleplayResponse.History turns(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("isbn") @Pattern(regexp = "\\d{13}") String isbn,
            @RequestParam("characterId") @Positive Long characterId) {
        User user = authService.authenticateBearer(authorization);
        return roleplayService.turns(user, isbn, characterId);
    }
}
