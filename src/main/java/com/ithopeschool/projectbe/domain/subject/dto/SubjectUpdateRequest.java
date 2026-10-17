package com.ithopeschool.projectbe.domain.subject.dto;

import jakarta.validation.constraints.Size;

// "과목 이름·색 바꾸기" 요청의 모양입니다. 바꿀 것만 보냅니다. (PATCH)
//   { "name": "국어" }   { "color": "red" }   { "name": "국어", "color": "red" }
// 보내지 않은(null) 값은 그대로 둡니다. 둘 다 보내지 않은 요청은 Service 에서 400 으로 돌려보냅니다.
public record SubjectUpdateRequest(
    @Size(min = 1, max = 30, message = "과목 이름은 1자 이상 30자 이하로 적어 주세요") String name, String color) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다. "   " 처럼 공백뿐인 이름은 빈 글자("")가 되어 위의 검사에 걸립니다.
  public SubjectUpdateRequest {
    name = name == null ? null : name.trim();
  }
}
