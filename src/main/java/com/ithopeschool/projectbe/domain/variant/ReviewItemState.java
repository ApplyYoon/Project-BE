package com.ithopeschool.projectbe.domain.variant;

import java.time.Instant;
import java.time.LocalDate;

// 학습 항목의 "지금 상태" 만 모은 값입니다. 상태 전이 규칙(ReviewItemRules)이 이 값을 받아 새 값을 돌려줍니다.
// 엔티티(ReviewItem)를 직접 바꾸지 않고 값으로 주고받는 이유: 규칙을 DB 없이 테스트할 수 있게 하려고입니다.
//
//   status         : ACTIVE · GRADUATED · MASTERED
//   level          : ACTIVE 일 때 -1, 0, 1 (그 밖에는 마지막 레벨이 남아 있습니다)
//   wrongStreak    : 지금 레벨에서 연속으로 틀린 수
//   needsAttention : 비계(L-1)에서도 계속 틀리는 항목
//   nextDueDate    : 다음에 낼 날 (ACTIVE 일 때만 있음)
public record ReviewItemState(
    ReviewItemStatus status,
    int level,
    int wrongStreak,
    boolean needsAttention,
    LocalDate nextDueDate,
    Instant graduatedAt,
    Instant masteredAt) {

  // 틀린 원본으로 새로 만든 항목: ACTIVE, 레벨 0, 다음 날부터
  public static ReviewItemState newDailyWrong(LocalDate solvedDate) {
    return new ReviewItemState(
        ReviewItemStatus.ACTIVE, 0, 0, false, solvedDate.plusDays(1), null, null);
  }

  // 주간 (A) 를 틀려서 만든 항목: ACTIVE, 레벨 1, 다음 날부터 (원본을 맞혔으므로 L0 을 건너뜁니다)
  public static ReviewItemState newWeeklyCorrectFail(LocalDate answeredDate) {
    return new ReviewItemState(
        ReviewItemStatus.ACTIVE, 1, 0, false, answeredDate.plusDays(1), null, null);
  }
}
