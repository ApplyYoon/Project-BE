package com.ithopeschool.projectbe.domain.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.stats.ExpectationGap.Day;
import com.ithopeschool.projectbe.domain.stats.ExpectationGap.DayStudy;
import com.ithopeschool.projectbe.domain.stats.ExpectationGap.Direction;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// 예상 순공과 실제 순공의 차이(이슈 #40)를 계산하는 규칙을 확인합니다.
// FE 에 같은 함수가 없어서, 기대값은 주석에 적은 대로 손으로 계산한 값입니다.
class ExpectationGapTest {

  private static LocalDate october(int day) {
    return LocalDate.of(2026, 10, day);
  }

  // 체크인에 예상을 적지 않은 날: 계획 plannedMinutes 분, 실제 studyMinutes 분
  private static Day planned(int day, int plannedMinutes, int studyMinutes) {
    return new Day(october(day), null, plannedMinutes, studyMinutes);
  }

  // 10월 from 일부터 to 일까지 매일 같은 계획과 같은 공부 시간을 만듭니다.
  private static List<Day> everyDay(int from, int to, int plannedMinutes, int studyMinutes) {
    List<Day> days = new ArrayList<>();
    for (int day = from; day <= to; day++) {
      days.add(planned(day, plannedMinutes, studyMinutes));
    }
    return days;
  }

  // ─── 평균 차이와 방향 ───

  @Test
  void expectingMoreThanActualIsOver() {
    // 이슈의 예시: 5일 동안 매일 180분을 예상하고 100분을 공부 → "평균 1시간 20분 높게 예상"
    ExpectationGap gap = ExpectationGap.of(everyDay(1, 5, 180, 100));

    assertThat(gap)
        .isEqualTo(
            new ExpectationGap(
                80,
                Direction.OVER,
                List.of(october(1), october(2), october(3), october(4), october(5)),
                false));
  }

  @Test
  void expectingLessThanActualIsUnder() {
    ExpectationGap gap = ExpectationGap.of(everyDay(1, 5, 60, 90));

    assertThat(gap.averageDiffMinutes()).isEqualTo(-30);
    assertThat(gap.direction()).isEqualTo(Direction.UNDER);
  }

  @Test
  void sameOnAverageIsSame() {
    // 하루는 30분 더, 하루는 30분 덜 → 평균 0분
    ExpectationGap gap = ExpectationGap.of(List.of(planned(1, 120, 90), planned(2, 120, 150)));

    assertThat(gap.averageDiffMinutes()).isZero();
    assertThat(gap.direction()).isEqualTo(Direction.SAME);
  }

  @Test
  void averageIsRoundedOnceAtTheEnd() {
    // 차이: 10분 · 10분 · 15분 → 35 ÷ 3 = 11.66… → 12분
    ExpectationGap gap =
        ExpectationGap.of(List.of(planned(1, 60, 50), planned(2, 60, 50), planned(3, 60, 45)));

    assertThat(gap.averageDiffMinutes()).isEqualTo(12);
  }

  @Test
  void dayWithoutStudyCountsAsZeroMinutes() {
    // 계획 120분인데 공부를 안 한 날도 넣습니다. (120 + 20) ÷ 2 = 70분
    ExpectationGap gap = ExpectationGap.of(List.of(planned(1, 120, 0), planned(2, 120, 100)));

    assertThat(gap.averageDiffMinutes()).isEqualTo(70);
    assertThat(gap.dates()).containsExactly(october(1), october(2));
  }

  // ─── 그날의 예상 ───

  @Test
  void checkInExpectationComesBeforeThePlan() {
    // 체크인 예상 120분, 그날 계획 240분 → 예상은 120분
    assertThat(ExpectationGap.expectedMinutesOf(120, 240)).isEqualTo(120);
    // 체크인에 안 적었으면 그날 공부 계획의 목표 시간 합
    assertThat(ExpectationGap.expectedMinutesOf(null, 240)).isEqualTo(240);
    // 체크인의 0분은 "안 적은 것" 으로 봅니다. (1탭 체크인은 계획이 없으면 0 을 넣습니다)
    assertThat(ExpectationGap.expectedMinutesOf(0, 240)).isEqualTo(240);
    // 둘 다 없으면 예상이 없는 날
    assertThat(ExpectationGap.expectedMinutesOf(null, 0)).isNull();
    assertThat(ExpectationGap.expectedMinutesOf(0, 0)).isNull();
  }

  @Test
  void checkInExpectationIsUsedInTheGap() {
    // 10/1: 체크인 120분(계획 240분), 실제 100분 → 20분 / 10/2: 체크인 없음, 계획 240분, 실제 100분 → 140분
    ExpectationGap gap =
        ExpectationGap.of(
            List.of(new Day(october(1), 120, 240, 100), new Day(october(2), null, 240, 100)));

    assertThat(gap.averageDiffMinutes()).isEqualTo(80);
  }

  @Test
  void daysWithoutAnyExpectationAreLeftOut() {
    // 10/2 는 체크인도 계획도 없이 공부만 했습니다. 예상이 없으니 계산과 근거 날짜에서 빠집니다.
    ExpectationGap gap =
        ExpectationGap.of(List.of(planned(1, 100, 60), planned(2, 0, 300), planned(3, 100, 80)));

    assertThat(gap.averageDiffMinutes()).isEqualTo(30);
    assertThat(gap.dates()).containsExactly(october(1), october(3));
  }

  @Test
  void noGapWithoutAnyExpectation() {
    assertThat(ExpectationGap.of(List.of())).isNull();
    assertThat(ExpectationGap.of(List.of(planned(1, 0, 60), planned(2, 0, 0)))).isNull();
  }

  // ─── 근거 날짜와 가설 ───

  @Test
  void fewerThanFiveDaysIsAHypothesis() {
    assertThat(ExpectationGap.of(everyDay(1, 4, 180, 100)).isHypothesis()).isTrue();
    assertThat(ExpectationGap.of(everyDay(1, 5, 180, 100)).isHypothesis()).isFalse();
    assertThat(ExpectationGap.of(everyDay(1, 1, 180, 100)).isHypothesis()).isTrue();
  }

  @Test
  void onlyDaysWithExpectationCountTowardFiveDays() {
    // 6일 치를 넘겼지만 예상이 있는 날은 4일뿐이라 가설입니다.
    List<Day> days = new ArrayList<>(everyDay(1, 4, 180, 100));
    days.add(planned(5, 0, 100));
    days.add(planned(6, 0, 100));

    assertThat(ExpectationGap.of(days).isHypothesis()).isTrue();
  }

  @Test
  void datesAreSortedFromOldest() {
    ExpectationGap gap =
        ExpectationGap.of(List.of(planned(5, 60, 30), planned(1, 60, 30), planned(3, 60, 30)));

    assertThat(gap.dates()).containsExactly(october(1), october(3), october(5));
  }

  // ─── 처음 계획 대비 지금 ───

  @Test
  void initialPlanComparedWithRecentSevenDays() {
    // 처음 약속 하루 180분. 최근 7일: 120 · 0 · 150 · 100 · 0 · 130 · 200 = 700분 → 하루 평균 100분
    // (180 × 7 − 700) ÷ 7 = 80분 높게 약속
    int[] minutes = {120, 0, 150, 100, 0, 130, 200};
    List<DayStudy> recent = new ArrayList<>();
    for (int index = 0; index < minutes.length; index++) {
      recent.add(new DayStudy(october(index + 1), minutes[index]));
    }

    ExpectationGap gap = ExpectationGap.ofInitialPlan(180, recent);

    assertThat(gap.averageDiffMinutes()).isEqualTo(80);
    assertThat(gap.direction()).isEqualTo(Direction.OVER);
    assertThat(gap.dates()).hasSize(ExpectationGap.RECENT_DAYS);
    assertThat(gap.isHypothesis()).isFalse();
  }

  @Test
  void doingMoreThanTheInitialPlanIsUnder() {
    List<DayStudy> recent = new ArrayList<>();
    for (int day = 1; day <= 7; day++) {
      recent.add(new DayStudy(october(day), 150));
    }

    ExpectationGap gap = ExpectationGap.ofInitialPlan(120, recent);

    assertThat(gap.averageDiffMinutes()).isEqualTo(-30);
    assertThat(gap.direction()).isEqualTo(Direction.UNDER);
  }

  @Test
  void initialPlanGapOfANewUserIsAHypothesis() {
    // 가입한 지 3일 된 사람은 3일 치만 있습니다. 5일 미만이라 가설입니다.
    List<DayStudy> recent =
        List.of(
            new DayStudy(october(1), 60),
            new DayStudy(october(2), 60),
            new DayStudy(october(3), 60));

    ExpectationGap gap = ExpectationGap.ofInitialPlan(120, recent);

    assertThat(gap.averageDiffMinutes()).isEqualTo(60);
    assertThat(gap.isHypothesis()).isTrue();
  }

  @Test
  void noInitialPlanMeansNoGap() {
    List<DayStudy> recent = List.of(new DayStudy(october(1), 60));

    assertThat(ExpectationGap.ofInitialPlan(0, recent)).isNull();
    assertThat(ExpectationGap.ofInitialPlan(180, List.of())).isNull();
  }
}
