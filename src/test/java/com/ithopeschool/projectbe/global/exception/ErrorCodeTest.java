package com.ithopeschool.projectbe.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

// 에러 종류 목록과, HTTP 상태 번호로 에러 종류를 찾는 규칙(fromStatus)을 확인합니다.
class ErrorCodeTest {

  @Test
  @DisplayName("동의가 필요한 에러(CONSENT_REQUIRED)는 403 이다")
  void consentRequiredIs403() {
    assertThat(ErrorCode.CONSENT_REQUIRED.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("이유를 모르는 일반 403 에 '동의 문구에 동의해 주세요' 를 잘못 붙이지 않는다 (VALIDATION_ERROR 로 본다)")
  void plainForbiddenIsNotConsentRequired() {
    assertThat(ErrorCode.fromStatus(403)).isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  @DisplayName("다른 상태 번호는 전과 똑같이 찾는다")
  void otherStatusesStillMapAsBefore() {
    assertThat(ErrorCode.fromStatus(400)).isEqualTo(ErrorCode.VALIDATION_ERROR);
    assertThat(ErrorCode.fromStatus(401)).isEqualTo(ErrorCode.UNAUTHORIZED);
    assertThat(ErrorCode.fromStatus(404)).isEqualTo(ErrorCode.NOT_FOUND);
    assertThat(ErrorCode.fromStatus(405)).isEqualTo(ErrorCode.METHOD_NOT_ALLOWED);
    assertThat(ErrorCode.fromStatus(409)).isEqualTo(ErrorCode.CONFLICT);
    assertThat(ErrorCode.fromStatus(429)).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
    assertThat(ErrorCode.fromStatus(500)).isEqualTo(ErrorCode.INTERNAL_ERROR);
    assertThat(ErrorCode.fromStatus(503)).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    // 목록에 없는 번호: 4xx 는 VALIDATION_ERROR, 그 밖은 INTERNAL_ERROR
    assertThat(ErrorCode.fromStatus(415)).isEqualTo(ErrorCode.VALIDATION_ERROR);
    assertThat(ErrorCode.fromStatus(502)).isEqualTo(ErrorCode.INTERNAL_ERROR);
  }
}
