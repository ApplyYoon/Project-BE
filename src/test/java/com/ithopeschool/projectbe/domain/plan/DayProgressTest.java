package com.ithopeschool.projectbe.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.plan.DayProgress.PlanItem;
import com.ithopeschool.projectbe.domain.plan.DayProgress.PlayProgress;
import com.ithopeschool.projectbe.domain.plan.PlanProgress.PlanSession;
import java.util.List;
import org.junit.jupiter.api.Test;

// 하루치 계획의 진행 상황 계산 규칙(이슈 #31 의 재료)을 확인합니다.
// feExample 로 시작하는 4개는 FE 함수(getDayProgress · getPlayProgress) 주석의 예시를 그대로 옮긴 것입니다.
class DayProgressTest {

  private static final long MINUTE = 60_000;

  // 계획 번호
  private static final long MATH = 1;
  private static final long ENGLISH = 2;
  private static final long KOREAN = 3;
  private static final long SCIENCE = 4;
  private static final long GAME = 5;
  private static final long VIDEO = 6;

  private static PlanSession session(long planId, int minutes) {
    return new PlanSession(planId, minutes * MINUTE);
  }

  // ─── FE 예시 ───

  @Test
  void feExample1DayWithFourStudyPlans() {
    // FE 예) 수학 60분(60분 함, 끝냄) · 영어 30분(30분 함, 끝냄) · 국어 50분(12분 함) · 게임 60분(놀기) · 탐구 100분(안 함)
    //        → { totalCount: 4, doneCount: 2, actualMinutes: 102, targetMinutes: 240, percent: 42 }
    // 서버에서는 노는 계획(게임)을 부르는 쪽이 빼고 넘깁니다. 게임 기록이 섞여 있어도 세지 않습니다.
    List<PlanItem> studyPlans =
        List.of(
            new PlanItem(MATH, 60, true),
            new PlanItem(ENGLISH, 30, true),
            new PlanItem(KOREAN, 50, false),
            new PlanItem(SCIENCE, 100, false));
    List<PlanSession> sessions =
        List.of(session(MATH, 60), session(ENGLISH, 30), session(KOREAN, 12), session(GAME, 95));

    assertThat(DayProgress.of(studyPlans, sessions)).isEqualTo(new DayProgress(4, 2, 102, 240, 42));
  }

  @Test
  void feExample2DayWithoutPlans() {
    // FE 예) 계획이 없는 날 → { totalCount: 0, doneCount: 0, actualMinutes: 0, targetMinutes: 0, percent:
    // 0 }
    assertThat(DayProgress.of(List.of(), List.of())).isEqualTo(new DayProgress(0, 0, 0, 0, 0));
  }

  @Test
  void feExample3PlayPlans() {
    // FE 예) 게임 60분(95분 놂) · 영상 30분(안 놂) · 수학 60분(공부 계획)
    //        → { count: 2, targetMinutes: 90, actualMinutes: 95 }
    List<PlanItem> playPlans =
        List.of(new PlanItem(GAME, 60, false), new PlanItem(VIDEO, 30, false));
    List<PlanSession> sessions = List.of(session(GAME, 95), session(MATH, 60));

    assertThat(DayProgress.playOf(playPlans, sessions)).isEqualTo(new PlayProgress(2, 90, 95));
  }

  @Test
  void feExample4DayWithoutPlayPlans() {
    // FE 예) 노는 계획이 없는 날 → { count: 0, targetMinutes: 0, actualMinutes: 0 }
    assertThat(DayProgress.playOf(List.of(), List.of(session(MATH, 60))))
        .isEqualTo(new PlayProgress(0, 0, 0));
  }

  // ─── 공부 계획 ───

  @Test
  void timersWithoutAPlanAreNotCounted() {
    // 계획 없이 켠 타이머는 "오늘 계획" 의 실제 시간에 들어가지 않습니다. 계획 줄의 숫자를 더한 값과 같아야 하기 때문입니다.
    List<PlanItem> studyPlans = List.of(new PlanItem(MATH, 60, false));
    List<PlanSession> sessions = List.of(session(MATH, 20), new PlanSession(null, 90 * MINUTE));

    assertThat(DayProgress.of(studyPlans, sessions).actualMinutes()).isEqualTo(20);
  }

  @Test
  void percentStopsAtHundredEvenWhenStudiedMore() {
    // 목표보다 더 했어도 진행률은 100 에서 멈춥니다. 실제 시간은 그대로 보여줍니다.
    List<PlanItem> studyPlans = List.of(new PlanItem(MATH, 60, true));

    DayProgress progress = DayProgress.of(studyPlans, List.of(session(MATH, 90)));

    assertThat(progress.actualMinutes()).isEqualTo(90);
    assertThat(progress.percent()).isEqualTo(100);
  }

  @Test
  void overStudyingOnePlanCanCoverAnother() {
    // 하루 진행률은 합계로 봅니다. 수학을 60분 더 하고 영어를 안 했어도, 합계가 목표와 같으면 100% 입니다.
    // (계획마다 채웠는지는 PlanProgress 가 따로 봅니다)
    List<PlanItem> studyPlans =
        List.of(new PlanItem(MATH, 60, true), new PlanItem(ENGLISH, 60, false));

    DayProgress progress = DayProgress.of(studyPlans, List.of(session(MATH, 120)));

    assertThat(progress).isEqualTo(new DayProgress(2, 1, 120, 120, 100));
  }

  @Test
  void doneIsCountedByTheCheckNotByTheTime() {
    // "끝냄" 은 시간을 채웠는지가 아니라 체크했는지로 셉니다. 시간을 다 채우고도 체크를 안 했으면 끝낸 것이 아닙니다.
    List<PlanItem> studyPlans =
        List.of(new PlanItem(MATH, 60, false), new PlanItem(ENGLISH, 30, true));

    DayProgress progress = DayProgress.of(studyPlans, List.of(session(MATH, 60)));

    assertThat(progress.doneCount()).isEqualTo(1);
  }

  @Test
  void zeroTargetGivesZeroPercent() {
    // 목표 시간의 합이 0 이면 나눌 수 없어서 진행률은 0% 입니다.
    List<PlanItem> studyPlans = List.of(new PlanItem(MATH, 0, false));

    assertThat(DayProgress.of(studyPlans, List.of(session(MATH, 30))))
        .isEqualTo(new DayProgress(1, 0, 30, 0, 0));
  }

  @Test
  void minutesAreAddedPerPlan() {
    // 실제 시간은 "계획 줄에 보이는 분" 을 더한 값입니다. 계획마다 1분이 안 되는 나머지를 버린 뒤에 더합니다.
    // 수학 20분 40초 + 영어 20분 40초 → 20 + 20 = 40분 (41분 20초를 한꺼번에 버린 41분이 아닙니다)
    long each = 20 * MINUTE + 40_000;
    List<PlanItem> studyPlans =
        List.of(new PlanItem(MATH, 30, false), new PlanItem(ENGLISH, 30, false));
    List<PlanSession> sessions =
        List.of(new PlanSession(MATH, each), new PlanSession(ENGLISH, each));

    assertThat(DayProgress.of(studyPlans, sessions).actualMinutes()).isEqualTo(40);
  }

  @Test
  void resultTellsWhetherTodaysGoalIsDone() {
    // 실제로 쓰는 흐름: 이벤트 메시지의 "오늘 목표 달성" 은 실제 시간이 목표 이상인지로 봅니다.
    List<PlanItem> studyPlans =
        List.of(new PlanItem(MATH, 60, true), new PlanItem(ENGLISH, 60, true));

    DayProgress notYet =
        DayProgress.of(studyPlans, List.of(session(MATH, 60), session(ENGLISH, 59)));
    DayProgress done = DayProgress.of(studyPlans, List.of(session(MATH, 60), session(ENGLISH, 60)));

    assertThat(notYet.actualMinutes() >= notYet.targetMinutes()).isFalse();
    assertThat(done.actualMinutes() >= done.targetMinutes()).isTrue();
  }

  // ─── 노는 계획 ───

  @Test
  void playTimeIsNotCappedAtThePlan() {
    // 노는 시간은 계획을 넘겨도 자르지 않습니다. 계획보다 얼마나 더 놀았는지 보여 주려는 숫자입니다.
    List<PlanItem> playPlans = List.of(new PlanItem(GAME, 30, false));

    assertThat(DayProgress.playOf(playPlans, List.of(session(GAME, 120))))
        .isEqualTo(new PlayProgress(1, 30, 120));
  }
}
