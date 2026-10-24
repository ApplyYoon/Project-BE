package com.ithopeschool.projectbe.domain.variant;

import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.util.Locale;

// 학습 항목(틀린 원본 하나)의 상태 (docs/VARIANT-SPEC.md 3-1)
public enum ReviewItemStatus {
  // 매일 변형 문제를 내는 중
  ACTIVE,
  // L1 까지 맞혀 졸업함. 주간 심화를 기다립니다
  GRADUATED,
  // 주간 심화까지 맞힘
  MASTERED;

  // JSON 으로 주고받을 때는 소문자로 씁니다. (docs/API.md, DB 에는 대문자)
  public String toJson() {
    return name().toLowerCase(Locale.ROOT);
  }

  // 받은 소문자 글자를 값으로 바꿉니다. 모르는 글자면 400 입니다.
  public static ReviewItemStatus fromJson(String value) {
    for (ReviewItemStatus candidate : values()) {
      if (candidate.toJson().equals(value)) {
        return candidate;
      }
    }
    throw new ApiException(
        ErrorCode.VALIDATION_ERROR, "status 는 active · graduated · mastered 중 하나예요");
  }
}
