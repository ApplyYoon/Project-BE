package com.ithopeschool.projectbe.domain.routine;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.routine.InitialPlan.RoutineDraft;
import com.ithopeschool.projectbe.domain.routine.InitialPlan.SubjectName;
import java.util.List;
import org.junit.jupiter.api.Test;

// 처음 계획으로 루틴 초안을 만드는 규칙(이슈 #85)을 확인합니다.
// feExample 로 시작하는 테스트는 FE 함수(src/lib/initialPlan.ts) 주석의 예시를 그대로 옮긴 것입니다.
class InitialPlanTest {

  private static final List<Integer> WEEKDAYS = List.of(1, 2, 3, 4, 5);

  private static final SubjectName KOREAN = new SubjectName(1L, "국어");
  private static final SubjectName MATH = new SubjectName(2L, "수학");
  private static final SubjectName ENGLISH = new SubjectName(3L, "영어");

  // ─── 요일 ───

  @Test
  void feExampleWeekdaysFor() {
    // FE 예) 3 → 월·수·금 / 5 → 평일 / 6 → 월 ~ 토 / 7 → 매일
    assertThat(InitialPlan.weekdaysFor(3)).containsExactly(1, 3, 5);
    assertThat(InitialPlan.weekdaysFor(5)).containsExactly(1, 2, 3, 4, 5);
    assertThat(InitialPlan.weekdaysFor(6)).containsExactly(1, 2, 3, 4, 5, 6);
    assertThat(InitialPlan.weekdaysFor(7)).containsExactly(0, 1, 2, 3, 4, 5, 6);
  }

  @Test
  void feExampleOtherNumbersMeanWeekdays() {
    // FE 예) 그 밖의 숫자는 평일로 봅니다.
    assertThat(InitialPlan.weekdaysFor(4)).isEqualTo(WEEKDAYS);
    assertThat(InitialPlan.weekdaysFor(0)).isEqualTo(WEEKDAYS);
    assertThat(InitialPlan.weekdaysFor(99)).isEqualTo(WEEKDAYS);
  }

  @Test
  void everyOptionOnTheScreenHasThatManyWeekdays() {
    for (int daysPerWeek : InitialPlan.DAYS_PER_WEEK_OPTIONS) {
      assertThat(InitialPlan.weekdaysFor(daysPerWeek)).hasSize(daysPerWeek);
    }
  }

  // ─── 시간 나누기 ───

  @Test
  void feExampleSplitMinutes() {
    // FE 예) (180, 3) → [60, 60, 60] / (180, 4) → [50, 50, 40, 40] / (120, 3) → [40, 40, 40]
    //        (60, 1) → [60] / (180, 0) → []
    assertThat(InitialPlan.splitMinutes(180, 3)).containsExactly(60, 60, 60);
    assertThat(InitialPlan.splitMinutes(180, 4)).containsExactly(50, 50, 40, 40);
    assertThat(InitialPlan.splitMinutes(120, 3)).containsExactly(40, 40, 40);
    assertThat(InitialPlan.splitMinutes(60, 1)).containsExactly(60);
    assertThat(InitialPlan.splitMinutes(180, 0)).isEmpty();
  }

  @Test
  void leftoverGoesToTheFirstSubjectsTenMinutesEach() {
    // 300분 ÷ 7과목: 30조각을 7로 나누면 4조각씩이고 2조각이 남아서, 앞의 두 과목이 10분씩 더 받습니다.
    assertThat(InitialPlan.splitMinutes(300, 7)).containsExactly(50, 50, 40, 40, 40, 40, 40);
  }

  @Test
  void eachSubjectGetsAtLeastTenMinutes() {
    // 60분을 8과목으로 나누면 6조각뿐이지만, 한 과목은 최소 10분이라 합계(80분)가 하루 목표보다 커집니다.
    assertThat(InitialPlan.splitMinutes(60, 8)).containsExactly(10, 10, 10, 10, 10, 10, 10, 10);
    // 하루 목표가 0분이어도 과목마다 10분입니다.
    assertThat(InitialPlan.splitMinutes(0, 2)).containsExactly(10, 10);
  }

  @Test
  void remainderUnderTenMinutesIsDropped() {
    // 65분은 6조각(60분)으로 봅니다. 남는 5분은 버립니다.
    assertThat(InitialPlan.splitMinutes(65, 2)).containsExactly(30, 30);
    assertThat(InitialPlan.splitMinutes(59, 1)).containsExactly(50);
  }

  @Test
  void everyOptionOnTheScreenSplitsToItsTotal() {
    // 화면에서 고를 수 있는 시간(1 ~ 5시간)을 1 ~ 6과목으로 나누면 합계가 하루 목표와 같습니다.
    for (int hours : InitialPlan.DAILY_HOUR_OPTIONS) {
      for (int count = 1; count <= 6; count++) {
        List<Integer> parts = InitialPlan.splitMinutes(hours * 60, count);
        int sum = 0;
        for (int part : parts) {
          sum += part;
        }
        assertThat(sum).as("%d시간을 %d과목으로", hours, count).isEqualTo(hours * 60);
      }
    }
  }

  // ─── 루틴 초안 ───

  @Test
  void feExampleRoutineDrafts() {
    // FE 예) createRoutineDraft([국어, 수학, 영어], 180, 5, …)
    //        → 평일 · 국어 · '국어 공부' · 60분 / 수학 · '수학 공부' · 60분 / 영어 · '영어 공부' · 60분
    assertThat(InitialPlan.routineDraftsOf(List.of(KOREAN, MATH, ENGLISH), 180, 5))
        .containsExactly(
            new RoutineDraft(WEEKDAYS, 1L, "국어 공부", 60),
            new RoutineDraft(WEEKDAYS, 2L, "수학 공부", 60),
            new RoutineDraft(WEEKDAYS, 3L, "영어 공부", 60));
  }

  @Test
  void feExampleNoSubjectsMeansNoDrafts() {
    // FE 예) createRoutineDraft([], 180, 5, …) → []
    assertThat(InitialPlan.routineDraftsOf(List.of(), 180, 5)).isEmpty();
  }

  @Test
  void draftsFollowTheOrderOfSubjects() {
    // 100분 ÷ 3과목 → 40 · 30 · 30. 먼저 고른 과목이 10분을 더 받습니다. 주 3일이면 월·수·금입니다.
    assertThat(InitialPlan.routineDraftsOf(List.of(MATH, KOREAN, ENGLISH), 100, 3))
        .containsExactly(
            new RoutineDraft(List.of(1, 3, 5), 2L, "수학 공부", 40),
            new RoutineDraft(List.of(1, 3, 5), 1L, "국어 공부", 30),
            new RoutineDraft(List.of(1, 3, 5), 3L, "영어 공부", 30));
  }
}
