package com.ithopeschool.projectbe.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.plan.PlanProgress.PlanSession;
import java.util.List;
import org.junit.jupiter.api.Test;

// 계획 하나의 진행 상황 계산 규칙(이슈 #31 의 재료)을 확인합니다.
// feExample 로 시작하는 5개는 FE 함수(getPlanProgress) 주석의 예시를 그대로 옮긴 것입니다. (모두 50분 계획)
class PlanProgressTest {

  private static final long MINUTE = 60_000;

  // ─── FE 예시 ───

  @Test
  void feExample1NoRecord() {
    // FE 예) 기록 없음 → { actualMinutes: 0, percent: 0, isReached: false, remainingMinutes: 50 }
    assertThat(PlanProgress.of(50, 0)).isEqualTo(new PlanProgress(0, 0, false, 50));
  }

  @Test
  void feExample2PartlyDone() {
    // FE 예) 32분 공부 → { actualMinutes: 32, percent: 64, isReached: false, remainingMinutes: 18 }
    assertThat(PlanProgress.of(50, 32 * MINUTE)).isEqualTo(new PlanProgress(32, 64, false, 18));
  }

  @Test
  void feExample3TwoSessionsReachTheTarget() {
    // FE 예) 20분 + 30분 두 번 공부 → { actualMinutes: 50, percent: 100, isReached: true,
    // remainingMinutes: 0 }
    List<PlanSession> sessions =
        List.of(new PlanSession(7L, 20 * MINUTE), new PlanSession(7L, 30 * MINUTE));

    assertThat(PlanProgress.of(50, PlanProgress.millisOf(7L, sessions)))
        .isEqualTo(new PlanProgress(50, 100, true, 0));
  }

  @Test
  void feExample4OneSecondShortIsNotReached() {
    // FE 예) 49분 59초 공부 → { actualMinutes: 49, percent: 99, isReached: false, remainingMinutes: 1 }
    // 채웠는지는 분이 아니라 밀리초로 비교합니다. 1초만 남아도 "1분 남음" 입니다.
    assertThat(PlanProgress.of(50, 50 * MINUTE - 1_000))
        .isEqualTo(new PlanProgress(49, 99, false, 1));
  }

  @Test
  void feExample5OverTheTargetStaysAtHundred() {
    // FE 예) 70분 공부 (목표를 넘김) → { actualMinutes: 70, percent: 100, isReached: true, remainingMinutes:
    // 0 }
    // 실제 시간은 그대로 70분이고, 진행률만 100 에서 멈춥니다.
    assertThat(PlanProgress.of(50, 70 * MINUTE)).isEqualTo(new PlanProgress(70, 100, true, 0));
  }

  // ─── 경계값 ───

  @Test
  void exactlyTheTargetIsReached() {
    // 경계값: 딱 50분이면 채운 것이고, 1밀리초라도 모자라면 아직입니다.
    assertThat(PlanProgress.of(50, 50 * MINUTE).isReached()).isTrue();
    assertThat(PlanProgress.of(50, 50 * MINUTE - 1).isReached()).isFalse();
  }

  @Test
  void remainingMinutesAreRoundedUp() {
    // 남은 시간은 올림합니다. 31분 30초를 했으면 18분 30초가 남았고 → 19분으로 보여줍니다.
    PlanProgress progress = PlanProgress.of(50, 31 * MINUTE + 30_000);

    assertThat(progress.actualMinutes()).isEqualTo(31);
    assertThat(progress.remainingMinutes()).isEqualTo(19);
  }

  @Test
  void planWithoutTargetCannotHaveProgress() {
    // 목표 시간이 0 이하인 계획은 진행률을 계산할 수 없어서 0%, "못 채움", 남은 시간 0 입니다.
    // 실제 한 시간은 그대로 보여줍니다.
    assertThat(PlanProgress.of(0, 20 * MINUTE)).isEqualTo(new PlanProgress(20, 0, false, 0));
    assertThat(PlanProgress.of(-10, 20 * MINUTE)).isEqualTo(new PlanProgress(20, 0, false, 0));
  }

  // ─── millisOf: 그 계획의 기록만 더하기 ───

  @Test
  void onlySessionsStartedFromThatPlanAreCounted() {
    // 계획 번호가 같은 기록만 더합니다. 다른 계획의 기록과, 계획 없이 켠 타이머(planId 가 null)는 세지 않습니다.
    List<PlanSession> sessions =
        List.of(
            new PlanSession(1L, 10 * MINUTE),
            new PlanSession(2L, 100 * MINUTE),
            new PlanSession(null, 100 * MINUTE),
            new PlanSession(1L, 5 * MINUTE));

    assertThat(PlanProgress.millisOf(1L, sessions)).isEqualTo(15 * MINUTE);
    assertThat(PlanProgress.millisOf(3L, sessions)).isEqualTo(0);
    assertThat(PlanProgress.millisOf(1L, List.of())).isEqualTo(0);
  }

  @Test
  void secondsAreAddedBeforeTurningIntoMinutes() {
    // 기록마다 분으로 바꾸지 않고, 밀리초로 모두 더한 뒤에 분으로 바꿉니다.
    // 20분 40초 두 번 = 41분 20초 → 41분 (기록마다 버리면 20 + 20 = 40분이 됩니다)
    long each = 20 * MINUTE + 40_000;
    List<PlanSession> sessions = List.of(new PlanSession(1L, each), new PlanSession(1L, each));

    assertThat(PlanProgress.of(50, PlanProgress.millisOf(1L, sessions)).actualMinutes())
        .isEqualTo(41);
  }
}
