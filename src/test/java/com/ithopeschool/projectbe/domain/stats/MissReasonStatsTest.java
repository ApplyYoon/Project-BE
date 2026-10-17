package com.ithopeschool.projectbe.domain.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.stats.MissReasonStats.Day;
import com.ithopeschool.projectbe.domain.stats.MissReasonStats.ReasonFact;
import com.ithopeschool.projectbe.domain.stats.MissReasonStats.ReasonStat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// 실패 원인 분석(이슈 #39)의 규칙을 확인합니다.
// FE 에 같은 함수가 없어서, 기대값은 주석에 적은 대로 손으로 계산한 값입니다.
// 2026년 10월 1일은 목요일입니다. (10월 5일 = 월요일)
class MissReasonStatsTest {

  private static final LocalDate FROM = october(1);
  private static final LocalDate TO = october(14);

  private static LocalDate october(int day) {
    return LocalDate.of(2026, 10, day);
  }

  // 그날 계획을 못 채움 (달성률 50%)
  private static Day missed(int day) {
    return new Day(october(day), 50);
  }

  // 그날 계획을 다 채움 (달성률 100%)
  private static Day achieved(int day) {
    return new Day(october(day), 100);
  }

  // 그날 계획이 없음 (달성률 null)
  private static Day noPlan(int day) {
    return new Day(october(day), null);
  }

  private static ReasonFact fact(int day, MissReason reason) {
    return new ReasonFact(october(day), reason);
  }

  private static MissReasonStats statsOf(List<Day> days, List<ReasonFact> facts) {
    return MissReasonStats.of(days, facts, FROM, TO);
  }

  // 결과에서 한 원인의 줄을 찾습니다.
  private static ReasonStat statOf(MissReasonStats stats, MissReason reason) {
    return stats.reasons().stream().filter(s -> s.reason() == reason).findFirst().orElseThrow();
  }

  // ─── 빈도와 주요 원인 ───

  @Test
  void countsReasonsAndPicksTopTwo() {
    // 폰·SNS 3일, 피곤 2일, 약속·행사 1일 → 주요 원인은 폰·SNS, 피곤
    List<Day> days =
        List.of(missed(1), missed(2), missed(3), missed(5), missed(6), missed(7), achieved(8));
    List<ReasonFact> facts =
        List.of(
            fact(1, MissReason.PHONE),
            fact(2, MissReason.PHONE),
            fact(3, MissReason.PHONE),
            fact(5, MissReason.TIRED),
            fact(6, MissReason.TIRED),
            fact(7, MissReason.EVENT));

    MissReasonStats stats = statsOf(days, facts);

    assertThat(stats.missedDayCount()).isEqualTo(6);
    assertThat(stats.reasons())
        .extracting(ReasonStat::reason, ReasonStat::dayCount)
        .containsExactly(
            tuple(MissReason.PHONE, 3), tuple(MissReason.TIRED, 2), tuple(MissReason.EVENT, 1));
    assertThat(stats.topReasons()).containsExactly(MissReason.PHONE, MissReason.TIRED);
  }

  @Test
  void sameReasonOnSameDayCountsOnce() {
    // 같은 날 타이머에서도, 체크인에서도 '폰·SNS' → 하루
    MissReasonStats stats =
        statsOf(List.of(missed(1)), List.of(fact(1, MissReason.PHONE), fact(1, MissReason.PHONE)));

    assertThat(statOf(stats, MissReason.PHONE).dayCount()).isEqualTo(1);
  }

  @Test
  void differentReasonsOnSameDayAreBothCounted() {
    MissReasonStats stats =
        statsOf(List.of(missed(1)), List.of(fact(1, MissReason.PHONE), fact(1, MissReason.TIRED)));

    assertThat(stats.reasons()).hasSize(2);
    // 미달한 날은 하루뿐입니다.
    assertThat(stats.missedDayCount()).isEqualTo(1);
  }

  @Test
  void reasonsOnDaysThatWereNotMissedAreNotCounted() {
    // 6일은 다 채웠고, 7일은 계획이 없던 날 → 둘 다 미달이 아니라서 세지 않습니다.
    MissReasonStats stats =
        statsOf(
            List.of(achieved(6), noPlan(7)),
            List.of(fact(6, MissReason.PHONE), fact(7, MissReason.PHONE)));

    assertThat(stats.reasons()).isEmpty();
    assertThat(stats.topReasons()).isEmpty();
  }

  @Test
  void dayMissingFromListIsTreatedAsNoPlan() {
    // 9일의 달성률을 넘기지 않음 → 계획이 없던 날로 봅니다.
    MissReasonStats stats = statsOf(List.of(), List.of(fact(9, MissReason.PHONE)));

    assertThat(stats.missedDayCount()).isZero();
    assertThat(stats.reasons()).isEmpty();
  }

  @Test
  void etcIsNotATopReason() {
    // 기타 3일, 피곤 2일, 폰·SNS 1일 → 기타는 빼고 피곤, 폰·SNS
    List<Day> days = new ArrayList<>();
    for (int day = 1; day <= 6; day++) {
      days.add(missed(day));
    }
    List<ReasonFact> facts =
        List.of(
            fact(1, MissReason.ETC),
            fact(2, MissReason.ETC),
            fact(3, MissReason.ETC),
            fact(4, MissReason.TIRED),
            fact(5, MissReason.TIRED),
            fact(6, MissReason.PHONE));

    MissReasonStats stats = statsOf(days, facts);

    // 목록에는 기타도 그대로 보입니다.
    assertThat(stats.reasons().get(0).reason()).isEqualTo(MissReason.ETC);
    assertThat(stats.topReasons()).containsExactly(MissReason.TIRED, MissReason.PHONE);
  }

  @Test
  void onlyOneReasonMeansOnlyOneTopReason() {
    MissReasonStats stats = statsOf(List.of(missed(1)), List.of(fact(1, MissReason.TIRED)));

    assertThat(stats.topReasons()).containsExactly(MissReason.TIRED);
  }

  @Test
  void tieKeepsMissReasonOrder() {
    // 폰·SNS 와 피곤이 1일씩 → MissReason 에 먼저 적힌 피곤(TIRED)이 앞
    MissReasonStats stats =
        statsOf(
            List.of(missed(1), missed(2)),
            List.of(fact(1, MissReason.PHONE), fact(2, MissReason.TIRED)));

    assertThat(stats.topReasons()).containsExactly(MissReason.TIRED, MissReason.PHONE);
  }

  // ─── 기간 ───

  @Test
  void factsAndDaysOutsideThePeriodAreIgnored() {
    // 9월 30일과 10월 15일은 기간(10월 1일 ~ 14일) 밖
    List<Day> days =
        List.of(new Day(LocalDate.of(2026, 9, 30), 50), new Day(october(15), 50), missed(1));
    List<ReasonFact> facts =
        List.of(
            new ReasonFact(LocalDate.of(2026, 9, 30), MissReason.PHONE),
            fact(15, MissReason.PHONE),
            fact(1, MissReason.TIRED));

    MissReasonStats stats = statsOf(days, facts);

    assertThat(stats.missedDayCount()).isEqualTo(1);
    assertThat(stats.topReasons()).containsExactly(MissReason.TIRED);
  }

  @Test
  void missedDaysWithoutReasonAreStillCountedAsMissed() {
    // 3일 미달했는데 이유는 하루만 골랐음
    MissReasonStats stats =
        statsOf(List.of(missed(1), missed(2), missed(3)), List.of(fact(1, MissReason.PHONE)));

    assertThat(stats.missedDayCount()).isEqualTo(3);
    assertThat(statOf(stats, MissReason.PHONE).dayCount()).isEqualTo(1);
  }

  @Test
  void fromDateOfCountsBothEnds() {
    assertThat(MissReasonStats.fromDateOf(october(14), MissReasonStats.SHORT_DAYS))
        .isEqualTo(october(1));
    assertThat(MissReasonStats.fromDateOf(october(30), MissReasonStats.LONG_DAYS))
        .isEqualTo(october(1));
  }

  // ─── 요일 분포와 근거 날짜 ───

  @Test
  void weekdayCountsStartFromMonday() {
    // 5일(월), 6일(화), 12일(월) → 월요일 2, 화요일 1
    MissReasonStats stats =
        statsOf(
            List.of(missed(5), missed(6), missed(12)),
            List.of(
                fact(5, MissReason.PHONE), fact(6, MissReason.PHONE), fact(12, MissReason.PHONE)));

    assertThat(statOf(stats, MissReason.PHONE).weekdayCounts())
        .containsExactly(2, 1, 0, 0, 0, 0, 0);
  }

  @Test
  void evidenceDatesAreSortedFromOldest() {
    MissReasonStats stats =
        statsOf(
            List.of(missed(1), missed(3), missed(8)),
            List.of(
                fact(8, MissReason.PHONE), fact(1, MissReason.PHONE), fact(3, MissReason.PHONE)));

    assertThat(statOf(stats, MissReason.PHONE).dates())
        .containsExactly(october(1), october(3), october(8));
  }

  // ─── 회복 속도 ───

  @Test
  void recoveryIsDaysUntilNextAchievedDay() {
    // 1일 미달 → 2일 달성 : 1일
    MissReasonStats stats =
        statsOf(List.of(missed(1), achieved(2)), List.of(fact(1, MissReason.PHONE)));

    assertThat(statOf(stats, MissReason.PHONE).averageRecoveryDays()).isEqualTo(1.0);
  }

  @Test
  void daysWithoutPlanAndMissedDaysAreSkippedButStillPass() {
    // 3일 미달 → 4일 계획 없음 → 5일 미달 → 6일 달성 : 3일
    MissReasonStats stats =
        statsOf(
            List.of(missed(3), noPlan(4), missed(5), achieved(6)),
            List.of(fact(3, MissReason.TIRED)));

    assertThat(statOf(stats, MissReason.TIRED).averageRecoveryDays()).isEqualTo(3.0);
  }

  @Test
  void averageRecoveryIsRoundedToOneDecimalAtTheEnd() {
    // 1일 → 2일 달성 (1일), 3일 → 4일 달성 (1일), 5일 → 7일 달성 (2일) : 4 ÷ 3 = 1.33… → 1.3
    MissReasonStats stats =
        statsOf(
            List.of(
                missed(1), achieved(2), missed(3), achieved(4), missed(5), noPlan(6), achieved(7)),
            List.of(
                fact(1, MissReason.PHONE), fact(3, MissReason.PHONE), fact(5, MissReason.PHONE)));

    assertThat(statOf(stats, MissReason.PHONE).averageRecoveryDays()).isEqualTo(1.3);
  }

  @Test
  void notRecoveredByTheEndIsCountedSeparately() {
    // 1일 → 2일 달성 (1일), 13일 미달 → 14일도 미달 (아직 회복 안 됨)
    MissReasonStats stats =
        statsOf(
            List.of(missed(1), achieved(2), missed(13), missed(14)),
            List.of(fact(1, MissReason.PHONE), fact(13, MissReason.PHONE)));

    ReasonStat phone = statOf(stats, MissReason.PHONE);
    assertThat(phone.averageRecoveryDays()).isEqualTo(1.0);
    assertThat(phone.notRecoveredCount()).isEqualTo(1);
  }

  @Test
  void recoveryDoesNotLookPastTheEndOfThePeriod() {
    // 14일 미달, 15일 달성 → 15일은 기간 밖이라 아직 회복 안 됨. 평균은 null
    MissReasonStats stats =
        statsOf(List.of(missed(14), achieved(15)), List.of(fact(14, MissReason.TIRED)));

    ReasonStat tired = statOf(stats, MissReason.TIRED);
    assertThat(tired.averageRecoveryDays()).isNull();
    assertThat(tired.notRecoveredCount()).isEqualTo(1);
  }

  // ─── 기록이 없을 때 ───

  @Test
  void noRecordsGiveEmptyResult() {
    MissReasonStats stats = statsOf(List.of(), List.of());

    assertThat(stats).isEqualTo(new MissReasonStats(FROM, TO, 0, List.of(), List.of()));
  }

  @Test
  void nullReasonIsIgnored() {
    MissReasonStats stats = statsOf(List.of(missed(1)), List.of(new ReasonFact(october(1), null)));

    assertThat(stats.reasons()).isEmpty();
    assertThat(stats.missedDayCount()).isEqualTo(1);
  }
}
