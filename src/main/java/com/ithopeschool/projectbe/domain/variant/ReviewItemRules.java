package com.ithopeschool.projectbe.domain.variant;

import java.time.Instant;
import java.time.LocalDate;

// 학습 항목의 상태 전이 규칙입니다. (docs/VARIANT-SPEC.md 3-2 · 3-4)
// 규칙은 **이 파일 한 곳에만** 둡니다. API 와 예약 작업은 이 함수를 부르기만 합니다.
// 값만 계산하는 도구라서(DB · 시계를 쓰지 않음) 객체를 만들 일이 없습니다.
//
// 일일 결과 전이표
//   L0  정답 → L1
//   L0  오답 → 연속 오답 +1, l0WrongStreakToDrop(2)번이 되면 L-1
//   L1  정답 → GRADUATED (졸업), 다음 날짜 없음
//   L1  오답 → L0
//   L-1 정답 → L0, 주의 표시 해제
//   L-1 오답 → 연속 오답 +1, floorWrongStreakToFlag(3)번 이상이면 주의 표시 (레벨 유지)
// ACTIVE 로 남으면 다음 날짜 = 답한 날 다음 날. 레벨이 바뀌면 연속 오답은 0.
public final class ReviewItemRules {

  private ReviewItemRules() {}

  // 일일 과제의 결과를 반영합니다. ACTIVE 항목에만 씁니다.
  //   answeredDate : 답한 날 (새벽 4시 기준)
  //   now          : 졸업 시각을 적을 때 씁니다
  public static ReviewItemState applyDaily(
      ReviewItemState state,
      boolean correct,
      LocalDate answeredDate,
      Instant now,
      VariantSettings settings) {
    if (state.status() != ReviewItemStatus.ACTIVE) {
      throw new IllegalArgumentException("일일 결과는 ACTIVE 항목에만 반영해요: " + state.status());
    }
    LocalDate tomorrow = answeredDate.plusDays(1);
    int level = state.level();

    if (level == 0) {
      if (correct) {
        return active(1, 0, state.needsAttention(), tomorrow);
      }
      int streak = state.wrongStreak() + 1;
      if (streak >= settings.l0WrongStreakToDrop()) {
        // 두 번 연속 틀리면 비계(L-1)로 내립니다. 레벨이 바뀌어 연속 오답은 0 부터 다시 셉니다.
        return active(-1, 0, state.needsAttention(), tomorrow);
      }
      return active(0, streak, state.needsAttention(), tomorrow);
    }

    if (level == 1) {
      if (correct) {
        // L0 정답 다음 L1 정답 = 졸업. 한 항목은 하루에 한 번만 나가서 두 정답은 저절로 서로 다른 날입니다.
        return new ReviewItemState(ReviewItemStatus.GRADUATED, 1, 0, false, null, now, null);
      }
      return active(0, 0, state.needsAttention(), tomorrow);
    }

    if (level == -1) {
      if (correct) {
        return active(0, 0, false, tomorrow);
      }
      int streak = state.wrongStreak() + 1;
      boolean flag = state.needsAttention() || streak >= settings.floorWrongStreakToFlag();
      return active(-1, streak, flag, tomorrow);
    }

    throw new IllegalArgumentException("ACTIVE 항목의 레벨은 -1, 0, 1 중 하나예요: " + level);
  }

  // 주간 (B) "극복한 오답 심화" 의 결과를 반영합니다. GRADUATED 항목에만 씁니다.
  //   정답 → MASTERED
  //   오답 → ACTIVE 로 복귀, 레벨 0, 다음 날부터
  public static ReviewItemState applyWeeklyDeep(
      ReviewItemState state, boolean correct, LocalDate answeredDate, Instant now) {
    if (state.status() != ReviewItemStatus.GRADUATED) {
      throw new IllegalArgumentException("주간 심화 결과는 GRADUATED 항목에만 반영해요: " + state.status());
    }
    if (correct) {
      return new ReviewItemState(
          ReviewItemStatus.MASTERED, state.level(), 0, false, null, state.graduatedAt(), now);
    }
    return active(0, 0, false, answeredDate.plusDays(1));
  }

  private static ReviewItemState active(
      int level, int wrongStreak, boolean needsAttention, LocalDate nextDueDate) {
    return new ReviewItemState(
        ReviewItemStatus.ACTIVE, level, wrongStreak, needsAttention, nextDueDate, null, null);
  }
}
