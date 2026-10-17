package com.ithopeschool.projectbe.domain.cardday;

import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 사후 입력 마감 계산기입니다. (최종 기획서 「4.4 타이머」 사후 입력, 이슈 #225)
// "그날, 다음 통화 전까지" 만 사후 입력을 받습니다. 값만 계산하는 순수 함수라서 DB 를 모릅니다.
//
// 서버가 정한 뜻 (기획서의 "다음 통화" 를 이렇게 읽었습니다)
//   1) 지금이 그 카드의 날(새벽 4시 기준, DayRule)이어야 합니다. 날이 바뀌면(다음 날 04:00) 마감입니다.
//   2) 그날 카드 예정 시작 뒤에 시작한 AI 통화가 있으면 마감입니다. (통화에서 그날을 이미 정리했기 때문)
//      시작 시각이 없는 옛 카드는 그날 04:00 를 시작으로 봅니다. → 그날 통화가 한 번이라도 있으면 마감
//
//   (카드 10-12 19:00, 지금 10-12 20:30, 통화 없음)              → true
//   (카드 10-12 19:00, 지금 10-12 22:00, 통화 10-12 21:00 시작)   → false (통화 뒤)
//   (카드 10-12 19:00, 지금 10-12 22:00, 통화 10-12 08:00 시작)   → true  (카드 전의 통화는 상관없음)
//   (카드 10-12 19:00, 지금 10-13 03:30, 통화 없음)              → true  (아직 10-12 의 밤)
//   (카드 10-12 19:00, 지금 10-13 04:00, 통화 없음)              → false (날이 바뀜)
//   (카드 10-12 19:00, 지금 10-12 18:00)                         → false (아직 카드 시간 전 — 사후가 아님)
public final class LateEntryRule {

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private LateEntryRule() {}

  // 지금 그 카드 · 그 날짜에 사후 입력을 받을 수 있으면 true 입니다.
  // date: 카드를 하는 날짜 / cardStartAt: 그날 카드 예정 시작 순간 (시작 시각이 없으면 그날 04:00)
  // now: 지금 / callStartedAts: 그날 시작한 AI 통화들의 시작 순간
  public static boolean isOpen(
      LocalDate date, Instant cardStartAt, Instant now, List<Instant> callStartedAts) {
    // 1) 그날이 아니면 마감 (지난날 · 아직 안 온 날 모두)
    if (!DayRule.dateOf(now).equals(date)) {
      return false;
    }
    // 카드 시간이 오기 전에는 "사후" 가 아니라서 받지 않습니다.
    if (now.isBefore(cardStartAt)) {
      return false;
    }
    // 2) 카드가 시작된 뒤에 통화가 있었으면 마감
    for (Instant callStartedAt : callStartedAts) {
      if (!callStartedAt.isBefore(cardStartAt)) {
        return false;
      }
    }
    return true;
  }
}
