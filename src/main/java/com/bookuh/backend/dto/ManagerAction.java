package com.bookuh.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
//해석된 작업 한 개
public class ManagerAction {

    private String actionId; //서버에서 붙이는 개별 작업 번호
    private String type; //독서 기록 추가
    private String isbn; //책
    private Map<String, Object> values; //작업에 필요한 값
}
