package com.ithopeschool.projectbe.domain.suggestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// 개선안 실험의 진행 상황 계산 규칙(이슈 #52)을 확인합니다.
// feExample 로 시작하는 테스트는 FE 함수(src/lib/experimentResult.ts) 주석의 예시를 그대로 옮긴 것입니다.
class ExperimentProgressTest {

  // FE 예시의 실험: 10월 1일 시작, 14일 (10월 1일 ~ 10월 14일)
  private static final LocalDate START = LocalDate.of(2026, 10, 1);
  private static final int DAYS = 14;

  private static LocalDate october(int day) {
    return LocalDate.of(2026, 10, day);
  }

  // 10월 from 일부터 to 일까지 매일 지켰다고 답한 기록을 만듭니다.
  private static List<LocalDate> keptEveryDay(int from, int to) {
    List<LocalDate> dates = new ArrayList<>();
    for (int day = from; day <= to; day++) {
      dates.add(october(day));
    }
    return dates;
  }

  // ─── FE 예시 ───

  @Test
  void feExampleFifthDayOfFourteen() {
    // FE 예) 10/1 시작, 14일, 오늘 10/5 → dayIndex 5, resultDate 10/15, elapsedDays 4
    //        그 사이 지켰다고 답한 날이 4일 → rate 100 (4 ÷ 4)
    ExperimentProgress progress =
        ExperimentProgress.of(START, DAYS, keptEveryDay(1, 4), october(5));

    assertThat(progress).isEqualTo(new ExperimentProgress(14, 5, october(15), 4, 4, 100));
  }

  @Test
  void feExampleExperimentEndsTheDayAfterTheLastDay() {
    // FE 예) 10/1 시작, 14일 → 10/14 까지가 실험 기간. 10/14 는 진행 중, 10/15 부터 "끝난" 것입니다.
    assertThat(ExperimentProgress.isFinished(START, DAYS, october(14))).isFalse();
    assertThat(ExperimentProgress.isFinished(START, DAYS, october(15))).isTrue();
    assertThat(ExperimentProgress.isFinished(START, DAYS, october(30))).isTrue();
    assertThat(ExperimentProgress.resultDateOf(START, DAYS)).isEqualTo(october(15));
  }

  // ─── 며칠째인지 ───

  @Test
  void firstDayHasNothingToCountYet() {
    // 첫날: 1일째이고, 이행률을 셀 지난 날이 없어서 이행률은 0 이 아니라 null 입니다.
    ExperimentProgress progress = ExperimentProgress.of(START, DAYS, List.of(), START);

    assertThat(progress.dayIndex()).isEqualTo(1);
    assertThat(progress.elapsedDays()).isEqualTo(0);
    assertThat(progress.rate()).isNull();
  }

  @Test
  void beforeTheStartDayIsDayZero() {
    // 시작 전(9월 28일): 0일째, 셀 날도 없습니다.
    ExperimentProgress progress =
        ExperimentProgress.of(START, DAYS, List.of(), LocalDate.of(2026, 9, 28));

    assertThat(progress.dayIndex()).isEqualTo(0);
    assertThat(progress.elapsedDays()).isEqualTo(0);
    assertThat(progress.keptDays()).isEqualTo(0);
    assertThat(progress.rate()).isNull();
  }

  @Test
  void lastDayCountsUntilYesterday() {
    // 마지막 날(10월 14일): 14일째이고, 어제(10월 13일)까지 13일을 셉니다.
    ExperimentProgress progress =
        ExperimentProgress.of(START, DAYS, keptEveryDay(1, 13), october(14));

    assertThat(progress.dayIndex()).isEqualTo(14);
    assertThat(progress.elapsedDays()).isEqualTo(13);
    assertThat(progress.rate()).isEqualTo(100);
  }

  @Test
  void afterTheEndEverythingStopsAtTotalDays() {
    // 끝난 뒤에는 며칠째도, 셀 날 수도 전체 일수(14)에서 멈춥니다. 한 달 뒤에 봐도 같습니다.
    ExperimentProgress dayAfter =
        ExperimentProgress.of(START, DAYS, keptEveryDay(1, 14), october(15));
    ExperimentProgress monthLater =
        ExperimentProgress.of(START, DAYS, keptEveryDay(1, 14), LocalDate.of(2026, 11, 15));

    assertThat(dayAfter).isEqualTo(new ExperimentProgress(14, 14, october(15), 14, 14, 100));
    assertThat(monthLater).isEqualTo(dayAfter);
  }

  @Test
  void countsDaysAcrossMonths() {
    // 달이 바뀌어도 맞게 셉니다. 9월 25일 시작, 10일 → 결과는 10월 5일. 10월 2일은 8일째
    LocalDate start = LocalDate.of(2026, 9, 25);

    ExperimentProgress progress = ExperimentProgress.of(start, 10, List.of(), october(2));

    assertThat(progress.dayIndex()).isEqualTo(8);
    assertThat(progress.elapsedDays()).isEqualTo(7);
    assertThat(progress.resultDate()).isEqualTo(october(5));
  }

  // ─── 지킨 날 세기 ───

  @Test
  void rateIsBasedOnElapsedDaysNotTotalDays() {
    // 진행 중의 이행률은 "지난 날" 기준입니다. 5일째에 지난 4일 중 3일을 지켰으면 75% 입니다.
    // (전체 14일 기준이면 21% 로 나와서, 잘 하고 있는데도 못 하는 것처럼 보입니다)
    List<LocalDate> kept = List.of(october(1), october(2), october(4));

    ExperimentProgress progress = ExperimentProgress.of(START, DAYS, kept, october(5));

    assertThat(progress.keptDays()).isEqualTo(3);
    assertThat(progress.elapsedDays()).isEqualTo(4);
    assertThat(progress.rate()).isEqualTo(75);
  }

  @Test
  void todaysAnswerIsNotCountedYet() {
    // 오늘(10월 5일) 지켰다고 미리 답했어도 세지 않습니다. 어제까지만 셉니다.
    List<LocalDate> kept = List.of(october(4), october(5));

    ExperimentProgress progress = ExperimentProgress.of(START, DAYS, kept, october(5));

    assertThat(progress.keptDays()).isEqualTo(1);
  }

  @Test
  void answersOutsideTheExperimentAreIgnored() {
    // 실험을 시작하기 전(9월 30일)이나 끝난 뒤(10월 15일)의 답은 세지 않습니다.
    List<LocalDate> kept = new ArrayList<>(keptEveryDay(1, 14));
    kept.add(LocalDate.of(2026, 9, 30));
    kept.add(october(15));
    kept.add(october(20));

    ExperimentProgress progress = ExperimentProgress.of(START, DAYS, kept, october(25));

    assertThat(progress.keptDays()).isEqualTo(14);
    assertThat(progress.rate()).isEqualTo(100);
  }

  @Test
  void rateIsRounded() {
    // 이행률은 반올림합니다. 3일 중 1일 → 33.3… → 33, 3일 중 2일 → 66.6… → 67
    assertThat(ExperimentProgress.of(START, DAYS, List.of(october(1)), october(4)).rate())
        .isEqualTo(33);
    assertThat(
            ExperimentProgress.of(START, DAYS, List.of(october(1), october(2)), october(4)).rate())
        .isEqualTo(67);
  }

  @Test
  void noKeptDaysIsZeroPercentNotUnknown() {
    // 지난 날이 있는데 하나도 못 지켰으면 null 이 아니라 0% 입니다.
    ExperimentProgress progress = ExperimentProgress.of(START, DAYS, List.of(), october(5));

    assertThat(progress.rate()).isEqualTo(0);
  }

  // ─── 끝났는지 · 기준선 ───

  @Test
  void isFinishedWorksForShortExperiments() {
    // 7일짜리 실험(10월 1일 ~ 7일)은 10월 8일부터 끝난 실험입니다. 시작 전에는 끝난 것이 아닙니다.
    assertThat(ExperimentProgress.isFinished(START, 7, october(7))).isFalse();
    assertThat(ExperimentProgress.isFinished(START, 7, october(8))).isTrue();
    assertThat(ExperimentProgress.isFinished(START, 7, LocalDate.of(2026, 9, 20))).isFalse();
  }

  @Test
  void baselineIsTheTwoWeeksRightBeforeTheStart() {
    // 기준선은 실험 직전 2주입니다. 10월 1일 시작 → 9월 17일 ~ 9월 30일 (14일)
    LocalDate baselineStart = ExperimentProgress.baselineStartOf(START);

    assertThat(baselineStart).isEqualTo(LocalDate.of(2026, 9, 17));
    // 기준선의 마지막 날은 시작 전날입니다.
    assertThat(baselineStart.plusDays(ExperimentProgress.BASELINE_DAYS - 1))
        .isEqualTo(LocalDate.of(2026, 9, 30));
  }
}
