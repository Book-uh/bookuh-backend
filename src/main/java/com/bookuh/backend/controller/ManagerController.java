package com.bookuh.backend.controller;

import com.bookuh.backend.dto.ManagerMessagesResponse;
import com.bookuh.backend.dto.ManagerRequest;
import com.bookuh.backend.dto.ManagerResponse;
import com.bookuh.backend.entity.User;
import com.bookuh.backend.service.AuthService;
import com.bookuh.backend.service.ManagerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/manager")
public class ManagerController {

    private final AuthService authService;
    private final ManagerService managerService;

    //북어에게 보낸 메시지를 해석하고 답을 돌려준다.
    @PostMapping("/interpret")
    public ManagerResponse interpret(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            // 꼭 필요한 값이비어 있으면 처리하지 않는다
            @Valid @RequestBody ManagerRequest request) {
        User user = authService.authenticateBearer(authorization);
        return managerService.talk(user, request);
    }
    // 앱에서 저장된 북어 대화를 조회한다
    @GetMapping("/messages")
    public ManagerMessagesResponse messages(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "before", required = false) String before) {
        User user = authService.authenticateBearer(authorization);
        return managerService.getMessages(user, before);
    }
}
