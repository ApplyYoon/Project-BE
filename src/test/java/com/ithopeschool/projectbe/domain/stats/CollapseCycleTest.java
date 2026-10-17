package com.ithopeschool.projectbe.domain.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.stats.CollapseCycle.CollapseEvent;
import com.ithopeschool.projectbe.domain.stats.CollapseCycle.Day;
import com.ithopeschool.projectbe.domain.stats.CollapseCycle.RecoveryEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// 무너짐·회복 주기(이슈 #37)의 규칙을 확인합니다.
// FE 에 같은 함수가 없어서, 기대값은 주석에 적은 대로 손으로 계산한 값입니다.
// 표기: O = 다 채운 날(100%), X = 못 채운 날(50%), - = 계획이 없던 날
class CollapseCycleTest {

  private static final LocalDate FROM = october(1);
  private static final LocalDate TO = october(31);

  private static LocalDate october(int day) {
    return LocalDate.of(2026, 10, day);
  }

  // 10월 1일부터 하루에 한 글자씩 읽어 날들을 만듭니다. "OOX-O" → 1일 O, 2일 O, 3일 X, 4일 계획 없음, 5일 O
  private static List<Day> days(String pattern) {
    List<Day> days = new ArrayList<>();
    for (int i = 0; i < pattern.length(); i++) {
      char mark = pattern.charAt(i);
      // 100 과 null 을 한 줄 ?: 에 섞으면 자바가 null 을 숫자로 바꾸려다 에러가 나서 if 로 나눕니다.
      Integer rate = null;
      if (mark == 'O') {
        rate = 100;
      } else if (mark == 'X') {
        rate = 50;
      }
      days.add(new Day(october(i + 1), rate));
    }
    return days;
  }

  private static CollapseCycle cycleOf(String pattern) {
    return CollapseCycle.of(days(pattern), FROM, TO);
  }

  // ─── 무너짐 ───

  @Test
  void findsCollapseOnTheThirdDay() {
    // 이슈의 완료 조건: "3일째 무너짐" 이 잡힌다
    // 이틀 채우고 사흘째 못 채우기를 5번 → 3, 6, 9, 12, 15일에 모두 3일째 무너짐
    CollapseCycle cycle = cycleOf("OOXOOXOOXOOXOOXO");

    assertThat(cycle.collapse().commonDay()).isEqualTo(3);
    assertThat(cycle.collapse().commonDayCount()).isEqualTo(5);
    assertThat(cycle.collapse().events())
        .extracting(CollapseEvent::date)
        .containsExactly(october(3), october(6), october(9), october(12), october(15));
    // 근거가 5개라 가설이 아닙니다.
    assertThat(cycle.collapse().hypothesis()).isFalse();
  }

  @Test
  void fewerThanFiveCollapsesIsHypothesis() {
    // 3일째 무너짐이 3번뿐 → 가설
    CollapseCycle cycle = cycleOf("OOXOOXOOXO");

    assertThat(cycle.collapse().commonDay()).isEqualTo(3);
    assertThat(cycle.collapse().commonDayCount()).isEqualTo(3);
    assertThat(cycle.collapse().hypothesis()).isTrue();
  }

  @Test
  void daysWithoutPlanAreSkippedWhenCounting() {
    // O - O X → 계획 없는 2일은 건너뛰고 3일째(4일)에 무너짐
    CollapseCycle cycle = cycleOf("O-OX");

    assertThat(cycle.collapse().events()).containsExactly(new CollapseEvent(october(4), 3));
  }

  @Test
  void consecutiveMissesAreOneCollapse() {
    // O O X X → 3일에 한 번 무너졌고, 4일은 이어서 못 채운 것이라 다시 세지 않음
    CollapseCycle cycle = cycleOf("OOXXO");

    assertThat(cycle.collapse().events()).containsExactly(new CollapseEvent(october(3), 3));
  }

  @Test
  void missingFromTheFirstDayIsNotACollapse() {
    // 첫 계획일부터 못 채움 → 그 앞에 며칠 채웠는지 몰라서 무너짐이 아님
    CollapseCycle cycle = cycleOf("-XXO");

    assertThat(cycle.collapse().events()).isEmpty();
    assertThat(cycle.collapse().commonDay()).isNull();
    assertThat(cycle.collapse().commonDayCount()).isZero();
  }

  @Test
  void tieGoesToTheEarlierDay() {
    // 2일째 무너짐 2번(2일, 4일), 3일째 무너짐 2번(7일, 10일) → 더 빠른 2일째
    CollapseCycle cycle = cycleOf("OXOXOOXOOX");

    assertThat(cycle.collapse().events())
        .extracting(CollapseEvent::dayNumber)
        .containsExactly(2, 2, 3, 3);
    assertThat(cycle.collapse().commonDay()).isEqualTo(2);
    assertThat(cycle.collapse().commonDayCount()).isEqualTo(2);
  }

  // ─── 회복 ───

  @Test
  void recoveryCountsCalendarDaysFromTheFirstMiss() {
    // 3일 X → 4일 X → 5일 계획 없음 → 6일 O : 3일 만에 회복
    CollapseCycle cycle = cycleOf("OOXX-O");

    assertThat(cycle.recovery().events())
        .containsExactly(new RecoveryEvent(october(3), october(6), 3));
    assertThat(cycle.recovery().averageDays()).isEqualTo(3.0);
    assertThat(cycle.recovery().slumpSince()).isNull();
  }

  @Test
  void recoveryAfterMissingFromTheFirstDayIsCounted() {
    // 무너짐으로는 세지 않지만, 다시 채운 것은 회복입니다. 1일 X → 3일 O : 2일
    CollapseCycle cycle = cycleOf("XXO");

    assertThat(cycle.recovery().events())
        .containsExactly(new RecoveryEvent(october(1), october(3), 2));
  }

  @Test
  void averageRecoveryIsRoundedToOneDecimalAtTheEnd() {
    // 회복 1일, 1일, 2일 → 4 ÷ 3 = 1.33… → 1.3
    CollapseCycle cycle = cycleOf("OXOXOXXO");

    assertThat(cycle.recovery().events()).extracting(RecoveryEvent::days).containsExactly(1, 1, 2);
    assertThat(cycle.recovery().averageDays()).isEqualTo(1.3);
    assertThat(cycle.recovery().hypothesis()).isTrue();
  }

  @Test
  void fiveRecoveriesIsNotHypothesis() {
    CollapseCycle cycle = cycleOf("OOXOOXOOXOOXOOXO");

    assertThat(cycle.recovery().events()).hasSize(5);
    assertThat(cycle.recovery().averageDays()).isEqualTo(1.0);
    assertThat(cycle.recovery().hypothesis()).isFalse();
  }

  @Test
  void stillInSlumpAtTheEnd() {
    // 2일부터 끝까지 못 채움 → 아직 무너진 중 (2일부터). 회복한 적이 없어서 평균은 null
    CollapseCycle cycle = cycleOf("OXX");

    assertThat(cycle.recovery().slumpSince()).isEqualTo(october(2));
    assertThat(cycle.recovery().averageDays()).isNull();
    assertThat(cycle.recovery().events()).isEmpty();
  }

  // ─── 기간과 기록이 없을 때 ───

  @Test
  void daysOutsideThePeriodAreIgnoredAndOrderDoesNotMatter() {
    // 9/30 X 는 기간 밖. 10/1 O, 10/2 O, 10/3 X 를 뒤섞어 넘겨도 3일째 무너짐 하나
    List<Day> days =
        List.of(
            new Day(october(3), 50),
            new Day(LocalDate.of(2026, 9, 30), 50),
            new Day(october(1), 100),
            new Day(october(2), 100));

    CollapseCycle cycle = CollapseCycle.of(days, FROM, TO);

    assertThat(cycle.collapse().events()).containsExactly(new CollapseEvent(october(3), 3));
  }

  @Test
  void noRecordsGiveEmptyResult() {
    CollapseCycle cycle = CollapseCycle.of(List.of(), FROM, TO);

    assertThat(cycle.collapse().commonDay()).isNull();
    assertThat(cycle.collapse().events()).isEmpty();
    assertThat(cycle.collapse().hypothesis()).isTrue();
    assertThat(cycle.recovery().averageDays()).isNull();
    assertThat(cycle.recovery().events()).isEmpty();
    assertThat(cycle.recovery().slumpSince()).isNull();
    assertThat(cycle.recovery().hypothesis()).isTrue();
  }
}
