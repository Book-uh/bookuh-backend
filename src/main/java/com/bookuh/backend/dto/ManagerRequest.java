package com.bookuh.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
//앱에서 보내는 내용
public class ManagerRequest {

    @NotBlank
    @Size(max = 128)
    private String operationId; //이번 메시지 요청 번호
    private String isbn; //책을 선택하지 않으면 null
    @NotNull
    private OffsetDateTime clientTime; //메시지 보낸 사용자쪽 시각
    @NotBlank
    private String timeZone; // 사용자의 시간대 아시아/서울
    @NotBlank
    private String message; //사용자 입력 문장
}
