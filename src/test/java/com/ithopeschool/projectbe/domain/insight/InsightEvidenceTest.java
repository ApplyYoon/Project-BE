package com.ithopeschool.projectbe.domain.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.insight.InsightEvidence.Row;
import com.ithopeschool.projectbe.domain.summary.DaySummary.DaySession;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

// 인사이트의 근거를 만드는 규칙(이슈 #41)을 확인합니다.
// feExample 로 시작하는 테스트는 FE 함수(buildEvidenceRows) 주석의 예시를 그대로 옮긴 것입니다.
class InsightEvidenceTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private static LocalDate october(int day) {
    return LocalDate.of(2026, 10, day);
  }

  // 10월 day일 hour시에 시작해서 minutes 분 공부한 기록을 만듭니다. (일시정지 없음)
  private static DaySession study(int day, int hour, int minutes) {
    Instant startedAt = october(day).atTime(hour, 0).atZone(SEOUL).toInstant();
    return new DaySession(startedAt, startedAt.plusSeconds(minutes * 60L), 0, 0);
  }

  // ─── 근거 줄 ───

  @Test
  void feExampleRecentDateComesFirst() {
    // FE 예) buildEvidenceRows(['2026-10-03', '2026-10-05'], …) → [{ date: '2026-10-05' }, { date:
    // '2026-10-03' }]
    List<DaySession> sessions = List.of(study(3, 19, 60), study(5, 9, 120));
    Map<LocalDate, Integer> conditions = Map.of(october(3), 2, october(5), 4);

    assertThat(InsightEvidence.rowsOf(List.of(october(3), october(5)), sessions, conditions))
        .containsExactly(new Row(october(5), 120, 4), new Row(october(3), 60, 2));
  }

  @Test
  void sameDateMakesOnlyOneRow() {
    List<LocalDate> dates = List.of(october(3), october(5), october(3), october(5), october(4));

    assertThat(InsightEvidence.rowsOf(dates, List.of(), Map.of()))
        .extracting(Row::date)
        .containsExactly(october(5), october(4), october(3));
  }

  @Test
  void dayWithoutStudyOrCheckInIsZeroMinutesAndUnknownCondition() {
    // 공부 기록이 없으면 0분이고, 체크인을 안 했으면 컨디션은 "모름"(null)입니다. 3 같은 값으로 채우지 않습니다.
    assertThat(InsightEvidence.rowsOf(List.of(october(7)), List.of(), Map.of()))
        .containsExactly(new Row(october(7), 0, null));
  }

  @Test
  void studyMinutesAreOfThatDayOnly() {
    // 10/5 에 두 번(40분 + 20분) 공부했고, 10/6 새벽 1시의 30분도 새벽 4시 전이라 10/5 기록입니다.
    List<DaySession> sessions =
        List.of(study(5, 9, 40), study(5, 20, 20), study(6, 1, 30), study(6, 10, 500));

    assertThat(InsightEvidence.rowsOf(List.of(october(5)), sessions, Map.of()))
        .containsExactly(new Row(october(5), 90, null));
  }

  @Test
  void noDatesMeansNoRows() {
    assertThat(InsightEvidence.rowsOf(List.of(), List.of(study(5, 9, 40)), Map.of())).isEmpty();
  }

  // ─── 가설 ───

  @Test
  void hypothesisWhenAnyConditionHasFewerThanFiveDays() {
    // 오전 6일 · 밤 4일: 밤이 5일 미만이라 가설입니다.
    assertThat(InsightEvidence.isHypothesis(List.of(6, 4))).isTrue();
    assertThat(InsightEvidence.isHypothesis(List.of(4, 30))).isTrue();
    assertThat(InsightEvidence.isHypothesis(List.of(0, 0))).isTrue();
  }

  @Test
  void notHypothesisWhenEveryConditionHasFiveDays() {
    assertThat(InsightEvidence.isHypothesis(List.of(6, 5))).isFalse();
    assertThat(InsightEvidence.isHypothesis(List.of(5, 5))).isFalse();
    // 조건이 하나뿐인 인사이트(예: "최근 7일 평균")도 같은 기준입니다.
    assertThat(InsightEvidence.isHypothesis(List.of(5))).isFalse();
    assertThat(InsightEvidence.isHypothesis(List.of(4))).isTrue();
  }

  @Test
  void hypothesisWhenThereIsNothingToCompare() {
    assertThat(InsightEvidence.isHypothesis(List.of())).isTrue();
  }
}
