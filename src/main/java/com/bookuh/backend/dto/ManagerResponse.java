package com.bookuh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
//서버가 앱에 돌려주는 결과
public class ManagerResponse {

    private String operationId; //어떤 요청에 대한 결과인지?
    private String clarifyingQuestion; // 정보가 부족할 때 물어볼 질문
    private String assistantMessage; //북어가 사용자에게 보여줄 답변
    private List<ManagerAction> actions; //여러요청이 들어올 수 있으므로 list로 ex)20분 읽었고 다 읽었어.
}