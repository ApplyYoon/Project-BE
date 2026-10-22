package com.ithopeschool.projectbe.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

// 에러 종류 목록입니다. docs/API.md 3장의 "에러 코드 목록" 표와 똑같이 맞춥니다.
// FE 는 이 이름(예: NOT_FOUND)을 보고 화면을 나눕니다.
// 새 종류가 필요하면 문서의 표에 먼저 추가하고, 여기에 한 줄을 더합니다.
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  // 값이 비었거나 모양이 틀림 (@Valid 검사 실패 등)
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "보낸 값을 다시 확인해 주세요"),
  // 로그인 안 함 / 로그인이 만료됨
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요"),
  // 없는 번호, 또는 다른 사람의 데이터 (다른 사람의 데이터도 "없다" 고 답합니다)
  NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없어요"),
  // 그 주소에 없는 요청 방법으로 부름 (GET 만 되는 주소를 POST 로 부름 등)
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방법이에요"),
  // 이미 있는 것을 또 만들려고 함, 또는 쓰고 있는 것을 지우려고 함 (계획이 있는 과목 삭제 등)
  CONFLICT(HttpStatus.CONFLICT, "이미 있는 값이에요"),
  // 로그인은 했지만 최신 동의 문구에 동의하지 않아서 데이터를 저장할 수 없음 (이슈 #71)
  // ⚠️ 서버 코드가 직접 던질 때만 나갑니다. Spring 이 던지는 일반 403 에는 이 이름을 쓰지 않습니다. (fromStatus 참고)
  CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "동의 문구에 동의한 뒤에 저장할 수 있어요"),
  // 오늘 공부를 하나 끝내야 열리는 확인 통화를 공부 전에 걸려고 함 (2026-10-18, CheckCall)
  // 2026-10-22 에 통화의 공부 조건을 빼서 지금은 서버가 던지지 않습니다. FE 가 이 이름을 알고 있어서 남겨 두었습니다.
  // ⚠️ 서버 코드가 직접 던질 때만 나갑니다. (CONSENT_REQUIRED 와 같은 이유로 fromStatus 가 건너뜁니다)
  CALL_LOCKED(HttpStatus.FORBIDDEN, "오늘 공부 하나를 끝내면 확인 통화가 열려요"),
  // 정해 둔 횟수를 넘게 부름 (하루 통화 횟수 등)
  TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "오늘은 더 할 수 없어요"),
  // 서버가 예상 못 한 문제
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해 주세요"),
  // 서버는 멀쩡한데 그 기능을 지금 쓸 수 없음 (AI 가 꺼져 있음, AI 회사 쪽 문제 등)
  SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "지금은 쓸 수 없어요");

  // 이 에러일 때 돌려줄 HTTP 상태 번호 (400, 404 ...)
  private final HttpStatus status;

  // 따로 정한 문장이 없을 때 쓰는 기본 문장
  private final String defaultMessage;

  // HTTP 상태 번호에 맞는 에러 종류를 찾습니다.
  // 위 목록에 없는 번호는 4xx(요청이 잘못됨)면 VALIDATION_ERROR, 그 밖에는 INTERNAL_ERROR 로 봅니다.
  // CONSENT_REQUIRED · CALL_LOCKED 는 건너뜁니다. 403 은 여러 이유로 날 수 있어서,
  // 이유를 모르는 403 에 "동의 문구에 동의해 주세요" 같은 엉뚱한 안내를 붙이지 않기 위해서입니다.
  public static ErrorCode fromStatus(int statusNumber) {
    for (ErrorCode code : values()) {
      boolean serverOnly = code == CONSENT_REQUIRED || code == CALL_LOCKED;
      if (!serverOnly && code.status.value() == statusNumber) {
        return code;
      }
    }
    return statusNumber >= 400 && statusNumber < 500 ? VALIDATION_ERROR : INTERNAL_ERROR;
  }
}
