package com.ithopeschool.projectbe.domain.variant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 학습 항목 상태 전이 규칙 단위 테스트입니다. (docs/VARIANT-SPEC.md 3-2 · 3-4, VAR-11)
class ReviewItemRulesTest {

  private static final VariantSettings SETTINGS = VariantSettings.defaults();
  private static final LocalDate DAY = LocalDate.parse("2026-10-25");
  private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

  private static ReviewItemState active(int level, int streak, boolean attention) {
    return new ReviewItemState(ReviewItemStatus.ACTIVE, level, streak, attention, DAY, null, null);
  }

  private static ReviewItemState answer(ReviewItemState state, boolean correct) {
    return ReviewItemRules.applyDaily(state, correct, DAY, NOW, SETTINGS);
  }

  @Test
  @DisplayName("표 1행: L0 정답 → L1, 다음 날")
  void l0Correct() {
    ReviewItemState next = answer(active(0, 1, false), true);

    assertThat(next.level()).isEqualTo(1);
    assertThat(next.wrongStreak()).isZero();
    assertThat(next.nextDueDate()).isEqualTo(DAY.plusDays(1));
  }

  @Test
  @DisplayName("표 2행: L0 오답 → 연속 오답 +1, 2번째면 L-1")
  void l0Wrong() {
    ReviewItemState once = answer(active(0, 0, false), false);
    assertThat(once.level()).isZero();
    assertThat(once.wrongStreak()).isEqualTo(1);

    ReviewItemState twice = answer(once, false);
    assertThat(twice.level()).isEqualTo(-1);
    assertThat(twice.wrongStreak()).isZero();
  }

  @Test
  @DisplayName("표 3행: L1 정답 → 졸업, 다음 날짜 없음, 졸업 시각 기록")
  void l1Correct() {
    ReviewItemState next = answer(active(1, 0, false), true);

    assertThat(next.status()).isEqualTo(ReviewItemStatus.GRADUATED);
    assertThat(next.nextDueDate()).isNull();
    assertThat(next.graduatedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("표 4행: L1 오답 → L0")
  void l1Wrong() {
    ReviewItemState next = answer(active(1, 0, false), false);

    assertThat(next.level()).isZero();
    assertThat(next.wrongStreak()).isZero();
    assertThat(next.status()).isEqualTo(ReviewItemStatus.ACTIVE);
  }

  @Test
  @DisplayName("표 5행: L-1 정답 → L0, 주의 표시 해제")
  void floorCorrect() {
    ReviewItemState next = answer(active(-1, 4, true), true);

    assertThat(next.level()).isZero();
    assertThat(next.needsAttention()).isFalse();
    assertThat(next.wrongStreak()).isZero();
  }

  @Test
  @DisplayName("표 6행: L-1 오답 → 연속 오답 +1, 3번 이상이면 주의 표시 (레벨 유지)")
  void floorWrong() {
    ReviewItemState state = active(-1, 0, false);
    state = answer(state, false);
    state = answer(state, false);
    assertThat(state.needsAttention()).isFalse();

    state = answer(state, false);
    assertThat(state.level()).isEqualTo(-1);
    assertThat(state.wrongStreak()).isEqualTo(3);
    assertThat(state.needsAttention()).isTrue();
  }

  @Test
  @DisplayName("시나리오: L0✗ L0✗ → L-1 → L-1✓ → L0 → L0✓ → L1 → L1✓ → 졸업")
  void fullPathToGraduation() {
    ReviewItemState state = ReviewItemState.newDailyWrong(DAY.minusDays(1));
    assertThat(state.level()).isZero();
    assertThat(state.nextDueDate()).isEqualTo(DAY);

    state = answer(state, false);
    state = answer(state, false);
    assertThat(state.level()).isEqualTo(-1);
    state = answer(state, true);
    assertThat(state.level()).isZero();
    state = answer(state, true);
    assertThat(state.level()).isEqualTo(1);
    state = answer(state, true);
    assertThat(state.status()).isEqualTo(ReviewItemStatus.GRADUATED);
  }

  @Test
  @DisplayName("시나리오: L0✓ L1✗ → L0 (졸업하려면 다시 L0 · L1 을 맞혀야 함)")
  void l1FailGoesBack() {
    ReviewItemState state = answer(active(0, 0, false), true);
    state = answer(state, false);

    assertThat(state.level()).isZero();
    assertThat(state.status()).isEqualTo(ReviewItemStatus.ACTIVE);
  }

  @Test
  @DisplayName("주간 (B): 정답 → MASTERED, 오답 → ACTIVE L0 다음 날")
  void weeklyDeep() {
    ReviewItemState graduated =
        new ReviewItemState(ReviewItemStatus.GRADUATED, 1, 0, false, null, NOW, null);

    ReviewItemState mastered = ReviewItemRules.applyWeeklyDeep(graduated, true, DAY, NOW);
    assertThat(mastered.status()).isEqualTo(ReviewItemStatus.MASTERED);
    assertThat(mastered.masteredAt()).isEqualTo(NOW);
    assertThat(mastered.nextDueDate()).isNull();

    ReviewItemState back = ReviewItemRules.applyWeeklyDeep(graduated, false, DAY, NOW);
    assertThat(back.status()).isEqualTo(ReviewItemStatus.ACTIVE);
    assertThat(back.level()).isZero();
    assertThat(back.nextDueDate()).isEqualTo(DAY.plusDays(1));
  }

  @Test
  @DisplayName("주간 (A) 오답으로 만든 항목은 L1 에서 시작한다")
  void weeklyCorrectFailStartsAtL1() {
    ReviewItemState state = ReviewItemState.newWeeklyCorrectFail(DAY);

    assertThat(state.level()).isEqualTo(1);
    assertThat(state.nextDueDate()).isEqualTo(DAY.plusDays(1));
  }

  @Test
  @DisplayName("ACTIVE 가 아닌 항목에 일일 결과를 반영하면 에러 (규칙을 잘못 부른 것)")
  void rejectsWrongStatus() {
    ReviewItemState graduated =
        new ReviewItemState(ReviewItemStatus.GRADUATED, 1, 0, false, null, NOW, null);

    assertThatThrownBy(() -> answer(graduated, true)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ReviewItemRules.applyWeeklyDeep(active(0, 0, false), true, DAY, NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("불변식: 무작위 정답 · 오답 1,000번 동안 ACTIVE 면 다음 날짜가 있고 레벨은 -1~1, 졸업하면 다음 날짜가 없다")
  void invariantsHoldForRandomSequences() {
    Random random = new Random(42);
    for (int run = 0; run < 1000; run++) {
      ReviewItemState state = ReviewItemState.newDailyWrong(DAY);
      LocalDate day = DAY.plusDays(1);
      for (int step = 0; step < 30 && state.status() == ReviewItemStatus.ACTIVE; step++) {
        state = ReviewItemRules.applyDaily(state, random.nextBoolean(), day, NOW, SETTINGS);
        if (state.status() == ReviewItemStatus.ACTIVE) {
          assertThat(state.nextDueDate()).isEqualTo(day.plusDays(1));
          assertThat(state.level()).isBetween(-1, 1);
          assertThat(state.wrongStreak()).isNotNegative();
        } else {
          assertThat(state.status()).isEqualTo(ReviewItemStatus.GRADUATED);
          assertThat(state.nextDueDate()).isNull();
        }
        day = day.plusDays(1);
      }
    }
  }
}
