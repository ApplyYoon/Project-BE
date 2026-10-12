package com.ithopeschool.projectbe.domain.subject.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// "과목 추가" 요청의 모양입니다.
//   { "name": "수학", "color": "blue" }
// color 가 다섯 가지(red·orange·green·blue·purple) 중 하나인지는 Service 에서 검사합니다. (SubjectColor)
public record SubjectCreateRequest(
    @NotBlank(message = "과목 이름을 입력해 주세요") @Size(max = 30, message = "과목 이름은 30자 이하로 적어 주세요")
        String name,
    @NotBlank(message = "색을 골라 주세요") String color) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다. 그래서 위의 검사는 공백을 지운 값으로 돌아갑니다.
  // (" 수학" 과 "수학" 이 서로 다른 과목으로 만들어지지 않게 합니다)
  public SubjectCreateRequest {
    name = name == null ? null : name.trim();
  }
}
