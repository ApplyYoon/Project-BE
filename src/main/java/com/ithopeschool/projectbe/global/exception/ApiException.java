package com.ithopeschool.projectbe.global.exception;

import lombok.Getter;

// "사용자에게 알려야 하는 에러" 를 낼 때 던지는 예외입니다. Service 에서 이렇게 씁니다.
//
//   throw new ApiException(ErrorCode.NOT_FOUND, "계획을 찾을 수 없어요");
//
// 던지기만 하면 GlobalExceptionHandler 가 받아서 docs/API.md 3장의 에러 모양으로 돌려줍니다.
// message 는 화면에 그대로 보여도 되는 한국어 문장으로 씁니다.
// 서버 내부 정보(SQL, 예외 이름)나 일기·기분 내용은 넣지 않습니다.
@Getter
public class ApiException extends RuntimeException {

  // 에러 종류. 어떤 HTTP 상태 번호로 답할지도 여기서 정해집니다.
  private final ErrorCode errorCode;

  public ApiException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }
}
