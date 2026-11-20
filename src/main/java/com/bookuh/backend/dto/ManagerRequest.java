package com.bookuh.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
//앱에서 보내는 내용
public class ManagerRequest {

    private String operationId; //이번 메시지 요청 번호
    private String isbn; //책을 선택하지 않으면 null
    private OffsetDateTime clientTime; //메시지 보낸 사용자쪽 시각
    private String timeZone; // 사용자의 시간대 아시아/서울
    private String message; //사용자 입력 문장
}