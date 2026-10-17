package com.ithopeschool.projectbe.domain.cardday;

import java.util.Locale;

// 카드 하루치에 사람이 직접 남긴 표시. (이슈 #225) FE 의 CardDayMark('postponed' | 'failed' | 'rest') 중 앞의 둘입니다.
// 휴식(rest)은 카드가 아니라 하루 전체에 붙어서 rest_days 테이블에 따로 둡니다.
//   POSTPONED: 미루기 — 이유 100자 이상 + 새 시각
//   FAILED:    실패로 기록 — 원인 태그 필수
//
// DB 에는 이름 그대로(POSTPONED) 저장하고, FE 와 주고받을 때는 소문자(postponed)로 바꿉니다.
public enum CardDayMarkType {
  POSTPONED,
  FAILED;

  // FE 와 주고받는 글자로 바꿉니다. (항상 소문자)
  public String toValue() {
    return name().toLowerCase(Locale.ROOT);
  }
}
