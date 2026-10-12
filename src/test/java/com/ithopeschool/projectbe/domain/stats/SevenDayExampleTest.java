package com.ithopeschool.projectbe.domain.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactPlan;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactSession;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactValue;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.TimeOfDay;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

// 예시 데이터 7일을 "손으로 계산한 값" 과 코드가 계산한 값이 같은지 확인합니다. (이슈 #36 완료 조건)
// 숫자를 고칠 일이 생기면 아래 표부터 손으로 다시 계산해 보세요. 표와 코드가 다르면 둘 중 하나가 틀린 것입니다.
//
// ■ 한 주: 2026-10-05(월) ~ 2026-10-11(일)
//
//   계획                               타이머 (시작 시각 · 순공 · 목표)
//   월  가) 60분, 19:00 시작          ①  가) 19:10 · 60분 · 목표 60분   → 완주,  10분 늦음
//   화  나) 90분, 19:00 시작          ②  나) 19:30 · 45분 · 목표 90분   → 멈춤(50%), 30분 늦음
//   수  다) 60분, 시각 안 정함        ③  다) 16:00 · 60분 · 목표 60분   → 완주
//   목  라) 120분, 20:00 시작         ④  라) 19:50 · 30분 · 목표 60분   → 멈춤(50%), 10분 일찍 = 0분 늦음
//                                     ⑤  라) 21:00 · 60분 · 목표 60분   → 완주  (라 의 두 번째 타이머)
//   금  계획 없음                     ⑥  계획 없이 23:00 · 25분 · 목표 없음
//   토  마) 60분, 10:00 시작          (한 번도 시작하지 않음)
//   일  계획 없음                     ⑦  계획 없이 09:00 · 50분 · 목표 50분   → 완주
//                                     ⑧  계획 없이 월요일 새벽 01:00 · 40분 · 목표 60분 → 멈춤(66.6…%)
//                                        (새벽 4시 전이라 아직 일요일 기록입니다)
//
// ■ 손으로 계산
//   계획한 시간      60 + 90 + 60 + 120 + 60                    = 390분 (계획 5개)
//   순공시간        60 + 45 + 60 + 30 + 60 + 25 + 50 + 40       = 370분
//   계획 대비 실제   370 ÷ 390 = 94.87…                          → 94%
//   완주율          목표가 있는 타이머 7번(⑥ 빼고) 중 ①③⑤⑦ 4번  → 4 ÷ 7 = 57.1… → 57%
//   멈추는 지점      ② 50% · ④ 50% · ⑧ 66.6…%  → 평균 55.5…      → 55% (3번)
//   평균 시작 지연   가) 10분 · 나) 30분 · 라) 0분  → 40 ÷ 3 = 13.3… → 13분 (계획 3개)
//                   (다 는 시작 시각이 없고, 마 는 시작한 적이 없어서 뺍니다. 라 는 첫 타이머 ④ 만 봅니다)
//   요일별 완주율    일 ⑦⑧ 1/2 = 50% · 월 ① 100% · 화 ② 0% · 수 ③ 100% · 목 ④⑤ 1/2 = 50% · 금·토 모름
//   시간대별 완주율  아침 ⑦ 100% · 낮 ③ 100% · 저녁 ①②④⑤ 2/4 = 50% · 밤 ⑧ 0%
//                   (⑥ 은 밤 11시지만 목표가 없어서 세지 않습니다)
//   하루 평균 순공   370 ÷ 7일 = 52.8…                           → 53분 (공부 안 한 토요일도 7일에 들어갑니다)
class SevenDayExampleTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
  private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 11);

  private static final long MINUTE = 60_000;

  // 계획 번호
  private static final long GA = 1;
  private static final long NA = 2;
  private static final long DA = 3;
  private static final long RA = 4;
  private static final long MA = 5;

  // 한국 시간 10월 "day일 시:분" 을 순간(Instant)으로 바꾸는 도우미입니다.
  private static Instant at(int day, int hour, int minute) {
    return ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, SEOUL).toInstant();
  }

  private static FactPlan plan(long id, int day, int targetMinutes, LocalTime startTime) {
    return new FactPlan(id, LocalDate.of(2026, 10, day), targetMinutes, startTime);
  }

  private static FactSession timer(Long planId, Instant startedAt, int studyMinutes, int target) {
    return new FactSession(planId, startedAt, studyMinutes * MINUTE, target);
  }

  private static final List<FactPlan> PLANS =
      List.of(
          plan(GA, 5, 60, LocalTime.of(19, 0)),
          plan(NA, 6, 90, LocalTime.of(19, 0)),
          plan(DA, 7, 60, null),
          plan(RA, 8, 120, LocalTime.of(20, 0)),
          plan(MA, 10, 60, LocalTime.of(10, 0)));

  private static final List<FactSession> SESSIONS =
      List.of(
          timer(GA, at(5, 19, 10), 60, 60), // ①
          timer(NA, at(6, 19, 30), 45, 90), // ②
          timer(DA, at(7, 16, 0), 60, 60), // ③
          timer(RA, at(8, 19, 50), 30, 60), // ④
          timer(RA, at(8, 21, 0), 60, 60), // ⑤
          timer(null, at(9, 23, 0), 25, 0), // ⑥
          timer(null, at(11, 9, 0), 50, 50), // ⑦
          timer(null, at(12, 1, 0), 40, 60)); // ⑧ 달력으로는 10월 12일(월) 새벽 1시

  @Test
  void sevenDayFactsMatchTheHandCalculation() {
    StudyFacts facts = StudyFacts.of(PLANS, SESSIONS, MONDAY, SUNDAY);

    assertThat(facts.plannedMinutes()).isEqualTo(390);
    assertThat(facts.studyMinutes()).isEqualTo(370);
    assertThat(facts.planRate()).isEqualTo(new FactValue(94, 5));
    assertThat(facts.completedCount()).isEqualTo(4);
    assertThat(facts.completionRate()).isEqualTo(new FactValue(57, 7));
    assertThat(facts.stopPoint()).isEqualTo(new FactValue(55, 3));
    assertThat(facts.startDelayMinutes()).isEqualTo(new FactValue(13, 3));
  }

  @Test
  void sevenDayCompletionByWeekdayMatchesTheHandCalculation() {
    StudyFacts facts = StudyFacts.of(PLANS, SESSIONS, MONDAY, SUNDAY);

    // 0번째 = 일요일 … 6번째 = 토요일
    assertThat(facts.completionByWeekday())
        .containsExactly(
            new FactValue(50, 2), // 일: ⑦ 완주, ⑧ 멈춤
            new FactValue(100, 1), // 월: ①
            new FactValue(0, 1), // 화: ②
            new FactValue(100, 1), // 수: ③
            new FactValue(50, 2), // 목: ④ 멈춤, ⑤ 완주
            FactValue.UNKNOWN, // 금: ⑥ 은 목표가 없음
            FactValue.UNKNOWN); // 토: 타이머 없음
  }

  @Test
  void sevenDayCompletionByTimeOfDayMatchesTheHandCalculation() {
    StudyFacts facts = StudyFacts.of(PLANS, SESSIONS, MONDAY, SUNDAY);

    assertThat(facts.completionByTimeOfDay())
        .containsEntry(TimeOfDay.MORNING, new FactValue(100, 1)) // ⑦ 09:00
        .containsEntry(TimeOfDay.AFTERNOON, new FactValue(100, 1)) // ③ 16:00
        .containsEntry(TimeOfDay.EVENING, new FactValue(50, 4)) // ①②④⑤
        .containsEntry(TimeOfDay.NIGHT, new FactValue(0, 1)); // ⑧ 01:00
  }

  @Test
  void dailyAverageIncludesDaysWithoutStudy() {
    // 다음 주 수요일(10/14)에 이 주를 돌아봅니다. 지난 주라서 7일 전부로 나눕니다.
    WeeklyFacts weekly = WeeklyFacts.of(PLANS, SESSIONS, MONDAY, LocalDate.of(2026, 10, 14));

    // 370 ÷ 7 = 52.8… → 53분. 공부한 6일로 나누면 61.6…분이 되는데, 그렇게 세지 않습니다.
    assertThat(weekly.dailyAverageMinutes()).isEqualTo(53);
    assertThat(weekly.thisWeek().studyMinutes()).isEqualTo(370);
    // 그 전 주(9/28 ~ 10/4)에는 기록이 없습니다. ⑧ 처럼 다음 주로 넘어간 기록도 섞이지 않습니다.
    assertThat(weekly.lastWeek().studyMinutes()).isZero();
    assertThat(weekly.lastWeek().planRate()).isEqualTo(FactValue.UNKNOWN);
  }
}
