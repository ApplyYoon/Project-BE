package com.ithopeschool.projectbe.global.exception;

// 실패했을 때의 응답 모양입니다. 어떤 에러든 이 모양으로 나갑니다. (docs/API.md 3장)
//   { "error": { "code": "NOT_FOUND", "message": "계획을 찾을 수 없어요" } }
public record ApiErrorResponse(Error error) {

  // code: 에러 종류(대문자 + 밑줄) / message: 사람이 읽을 한국어 문장
  public record Error(String code, String message) {}

  public static ApiErrorResponse of(ErrorCode code, String message) {
    return new ApiErrorResponse(new Error(code.name(), message));
  }
}
