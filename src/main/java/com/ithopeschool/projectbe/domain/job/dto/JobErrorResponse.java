package com.ithopeschool.projectbe.domain.job.dto;

// 실패했을 때의 응답 모양입니다. docs/API.md 3장의 에러 모양과 같습니다.
//   { "error": { "code": "NOT_FOUND", "message": "없는 작업이에요" } }
// (공통 에러 처리가 만들어지면 그쪽으로 옮깁니다.)
public record JobErrorResponse(Error error) {

  // code: 에러 종류(대문자 + 밑줄) / message: 사람이 읽을 한국어 문장. 서버 내부 정보는 넣지 않습니다.
  public record Error(String code, String message) {}

  public static JobErrorResponse of(String code, String message) {
    return new JobErrorResponse(new Error(code, message));
  }
}
