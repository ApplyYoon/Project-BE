package com.ithopeschool.projectbe.domain.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.risk.RiskRule.Assessment;
import com.ithopeschool.projectbe.domain.risk.RiskRule.Day;
import com.ithopeschool.projectbe.domain.risk.RiskRule.WeekdayEvidence;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 위험 예측(이슈 #49)의 규칙 두 개와 위험 수준 계산을 확인합니다.
// FE 에 같은 함수가 없어서, 기대값은 주석에 적은 대로 손으로 계산한 값입니다.
// 예측하는 날은 2026년 10월 12일 월요일입니다. (10/5 월, 9/7 월 → 월요일 5번: 9/7 9/14 9/21 9/28 10/5)
class RiskRuleTest {

  private static final LocalDate TARGET = LocalDate.of(2026, 10, 12);

  // 10월 day 일 hour:minute (한국 시간)
  private static Instant kst(int day, int hour, int minute) {
    return LocalDateTime.of(2026, 10, day, hour, minute)
        .atZone(ZoneId.of("Asia/Seoul"))
        .toInstant();
  }

  // 9/7(월) ~ 10/11(일) 35일. 월요일 5번은 mondayRate, 나머지 30일은 otherRate
  private static List<Day> fiveWeeks(int mondayRate, int otherRate) {
    List<Day> days = new ArrayList<>();
    LocalDate last = LocalDate.of(2026, 10, 11);
    for (LocalDate date = LocalDate.of(2026, 9, 7); !date.isAfter(last); date = date.plusDays(1)) {
      days.add(new Day(date, date.getDayOfWeek() == DayOfWeek.MONDAY ? mondayRate : otherRate));
    }
    return days;
  }

  // 월요일 5번(9/7 ~ 10/5)은 mondayRate, 화요일 5번(9/8 ~ 10/6)은 tuesdayRate 인 10일
  // → 전체 평균 = (월 평균 + 화 평균) / 2 라서, 차이를 손으로 계산하기 쉽습니다.
  private static List<Day> mondaysAndTuesdays(int mondayRate, int tuesdayRate) {
    List<Day> days = new ArrayList<>();
    for (int week = 0; week < 5; week++) {
      LocalDate monday = LocalDate.of(2026, 9, 7).plusWeeks(week);
      days.add(new Day(monday, mondayRate));
      days.add(new Day(monday.plusDays(1), tuesdayRate));
    }
    return days;
  }

  @Test
  @DisplayName("예측하는 날 2026-10-12 는 월요일이다 (테스트의 전제)")
  void targetIsMonday() {
    assertThat(TARGET.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
  }

  // ─── 규칙 ① 약한 요일 ───

  @Test
  @DisplayName("월요일 평균 50%, 전체 평균 76% 면 약한 요일이 켜진다 (근거 5일)")
  void weakWeekdayOn() {
    // 월요일 5번 50%, 나머지 30일 80% → 전체 (250 + 2400) / 35 = 75.7 → 보여 줄 때 76
    WeekdayEvidence evidence = RiskRule.weekdayEvidenceOf(TARGET, fiveWeeks(50, 80));

    assertThat(evidence).isEqualTo(new WeekdayEvidence(50, 76, 5, true));
  }

  @Test
  @DisplayName("차이가 딱 15%p 이면 켜지고, 14.5%p 이면 안 켜진다")
  void gapBoundary() {
    // 월요일 50, 화요일 80 → 전체 65.0 → 차이 15.0 → 켜짐
    assertThat(RiskRule.weekdayEvidenceOf(TARGET, mondaysAndTuesdays(50, 80)))
        .isEqualTo(new WeekdayEvidence(50, 65, 5, true));

    // 월요일 51, 화요일 80 → 전체 65.5 → 차이 14.5 → 안 켜짐
    // 보여 주는 숫자는 51 과 66 이라 15 차이처럼 보이지만, 비교는 반올림하기 전 숫자로 하므로 안 켜집니다.
    assertThat(RiskRule.weekdayEvidenceOf(TARGET, mondaysAndTuesdays(51, 80)))
        .isEqualTo(new WeekdayEvidence(51, 66, 5, false));
  }

  @Test
  @DisplayName("그 요일이 전체 평균보다 높으면 안 켜진다")
  void strongWeekdayIsNotRisk() {
    WeekdayEvidence evidence = RiskRule.weekdayEvidenceOf(TARGET, fiveWeeks(95, 60));

    assertThat(evidence.weak()).isFalse();
  }

  @Test
  @DisplayName("그 요일에 달성률이 있는 날이 4일뿐이면 근거가 모자라서 규칙을 쓰지 않는다")
  void fewerThanFiveWeekdays() {
    // 월요일 4번(9/14 ~ 10/5)만 0%, 나머지는 100% → 평균 차이는 크지만 근거가 4일
    List<Day> days = new ArrayList<>(fiveWeeks(0, 100));
    days.removeIf(day -> day.date().equals(LocalDate.of(2026, 9, 7)));

    assertThat(RiskRule.weekdayEvidenceOf(TARGET, days)).isNull();
    assertThat(RiskRule.assess(TARGET, days, null).level()).isEqualTo(RiskLevel.NONE);
  }

  @Test
  @DisplayName("계획이 없던 날(달성률 null)은 월요일 5일 근거에 세지 않고, 전체 평균에도 0 으로 넣지 않는다")
  void daysWithoutPlanAreSkipped() {
    // 월요일 4번 50% + 8/31(월) 은 계획 없음(null) → 월요일 근거는 4일
    List<Day> days = new ArrayList<>(fiveWeeks(50, 80));
    days.removeIf(day -> day.date().equals(LocalDate.of(2026, 9, 7)));
    days.add(new Day(LocalDate.of(2026, 8, 31), null));
    assertThat(RiskRule.weekdayEvidenceOf(TARGET, days)).isNull();

    // 월요일 5번 50% + 화요일은 전부 계획 없음(null) → 전체 평균은 월요일만으로 50%, 차이 0
    List<Day> onlyMondays = new ArrayList<>();
    for (Day day : fiveWeeks(50, 80)) {
      onlyMondays.add(
          day.date().getDayOfWeek() == DayOfWeek.MONDAY ? day : new Day(day.date(), null));
    }
    assertThat(RiskRule.weekdayEvidenceOf(TARGET, onlyMondays))
        .isEqualTo(new WeekdayEvidence(50, 50, 5, false));
  }

  @Test
  @DisplayName("예측하는 날과 그 뒤의 날은 재료로 쓰지 않는다")
  void targetAndLaterDaysAreIgnored() {
    // 이전 월요일은 4번뿐 (9/14 ~ 10/5). 10/12(예측하는 날)와 10/19 를 세면 6번이 돼 켜져 버립니다.
    List<Day> days = new ArrayList<>(fiveWeeks(0, 100));
    days.removeIf(day -> day.date().equals(LocalDate.of(2026, 9, 7)));
    days.add(new Day(TARGET, 0));
    days.add(new Day(LocalDate.of(2026, 10, 19), 0));

    assertThat(RiskRule.weekdayEvidenceOf(TARGET, days)).isNull();
  }

  @Test
  @DisplayName("날짜 순서가 뒤섞여 들어와도 결과가 같다")
  void orderDoesNotMatter() {
    List<Day> days = new ArrayList<>(fiveWeeks(50, 80));
    List<Day> shuffled = new ArrayList<>(days);
    Collections.reverse(shuffled);

    assertThat(RiskRule.weekdayEvidenceOf(TARGET, shuffled))
        .isEqualTo(RiskRule.weekdayEvidenceOf(TARGET, days));
  }

  // ─── 규칙 ② 늦게 잠 ───

  @Test
  @DisplayName("예측하는 날 새벽 1시 정각부터 늦게 잔 것이다")
  void lateSleepBoundary() {
    // 월요일 예측: 일요일 10/11 23:59, 월요일 10/12 00:59 → 늦지 않음 / 월요일 01:00, 03:00 → 늦음
    assertThat(RiskRule.isLateSleep(TARGET, kst(11, 23, 59))).isFalse();
    assertThat(RiskRule.isLateSleep(TARGET, kst(12, 0, 59))).isFalse();
    assertThat(RiskRule.isLateSleep(TARGET, kst(12, 1, 0))).isTrue();
    assertThat(RiskRule.isLateSleep(TARGET, kst(12, 3, 0))).isTrue();
  }

  @Test
  @DisplayName("어젯밤 = 전날 오후 6시 이상 ~ 그날 오전 10시 미만. 여러 번이면 가장 이른 시각")
  void lastNightWindow() {
    // 범위: 일요일 10/11 18:00 이상 ~ 월요일 10/12 10:00 미만
    List<Instant> candidates =
        Arrays.asList(
            kst(11, 17, 59), // 범위 밖: 오후 5시 59분 (낮잠이나 저녁 잠)
            null, // 모르는 값은 건너뜀
            kst(12, 2, 30),
            kst(12, 0, 10), // 가장 이름
            kst(12, 10, 0)); // 범위 밖: 정확히 오전 10시는 포함하지 않음

    assertThat(RiskRule.lastNightSleptAt(TARGET, candidates)).isEqualTo(kst(12, 0, 10));
  }

  @Test
  @DisplayName("어젯밤 범위의 양 끝: 오후 6시 정각은 포함, 오전 9시 59분도 포함")
  void lastNightWindowEdges() {
    assertThat(RiskRule.lastNightSleptAt(TARGET, List.of(kst(11, 18, 0))))
        .isEqualTo(kst(11, 18, 0));
    assertThat(RiskRule.lastNightSleptAt(TARGET, List.of(kst(12, 9, 59))))
        .isEqualTo(kst(12, 9, 59));
  }

  @Test
  @DisplayName("범위 안에 잠든 기록이 없으면 null (수면을 모르는 것과 같다)")
  void noSleepInWindow() {
    assertThat(RiskRule.lastNightSleptAt(TARGET, List.of())).isNull();
    assertThat(RiskRule.lastNightSleptAt(TARGET, List.of(kst(11, 17, 0), kst(12, 11, 0)))).isNull();
  }

  // ─── 위험 수준 ───

  @Test
  @DisplayName("규칙 둘 다 켜지면 HIGH, 이유는 약한 요일 → 늦게 잠 순서")
  void bothRulesAreHigh() {
    Instant sleptAt = kst(12, 2, 0);

    Assessment result = RiskRule.assess(TARGET, fiveWeeks(50, 80), sleptAt);

    assertThat(result.level()).isEqualTo(RiskLevel.HIGH);
    assertThat(result.reasons()).containsExactly(RiskReason.WEAK_WEEKDAY, RiskReason.LATE_SLEEP);
    assertThat(result.weekdayEvidence()).isEqualTo(new WeekdayEvidence(50, 76, 5, true));
    assertThat(result.sleptAt()).isEqualTo(sleptAt);
  }

  @Test
  @DisplayName("약한 요일만 켜지면 WATCH (일찍 잠 · 수면을 모름 둘 다)")
  void weakWeekdayOnlyIsWatch() {
    Assessment earlySleep = RiskRule.assess(TARGET, fiveWeeks(50, 80), kst(12, 0, 30));
    assertThat(earlySleep.level()).isEqualTo(RiskLevel.WATCH);
    assertThat(earlySleep.reasons()).containsExactly(RiskReason.WEAK_WEEKDAY);

    // 수면 데이터가 없으면 규칙 ② 는 쓰지 않아 최대 WATCH 입니다.
    Assessment unknownSleep = RiskRule.assess(TARGET, fiveWeeks(50, 80), null);
    assertThat(unknownSleep.level()).isEqualTo(RiskLevel.WATCH);
    assertThat(unknownSleep.reasons()).containsExactly(RiskReason.WEAK_WEEKDAY);
    assertThat(unknownSleep.sleptAt()).isNull();
  }

  @Test
  @DisplayName("늦게 잠만 켜지면 WATCH (요일이 약하지 않거나, 근거가 모자라도 같다)")
  void lateSleepOnlyIsWatch() {
    Instant sleptAt = kst(12, 2, 0);

    Assessment strongWeekday = RiskRule.assess(TARGET, fiveWeeks(80, 80), sleptAt);
    assertThat(strongWeekday.level()).isEqualTo(RiskLevel.WATCH);
    assertThat(strongWeekday.reasons()).containsExactly(RiskReason.LATE_SLEEP);

    // 기록이 하나도 없는 새 학생: 요일 근거는 null, 늦게 잠만 켜짐
    Assessment newStudent = RiskRule.assess(TARGET, List.of(), sleptAt);
    assertThat(newStudent.level()).isEqualTo(RiskLevel.WATCH);
    assertThat(newStudent.weekdayEvidence()).isNull();
  }

  @Test
  @DisplayName("켜진 규칙이 없으면 NONE 이고 위험한 날이 아니다")
  void noRuleIsNone() {
    Assessment normal = RiskRule.assess(TARGET, fiveWeeks(80, 80), kst(12, 0, 30));
    assertThat(normal.level()).isEqualTo(RiskLevel.NONE);
    assertThat(normal.reasons()).isEmpty();
    assertThat(normal.level().isRisky()).isFalse();

    Assessment nothingKnown = RiskRule.assess(TARGET, List.of(), null);
    assertThat(nothingKnown.level()).isEqualTo(RiskLevel.NONE);
    assertThat(nothingKnown.weekdayEvidence()).isNull();
    assertThat(nothingKnown.sleptAt()).isNull();
  }

  @Test
  @DisplayName("주의 이상만 위험한 날로 본다")
  void riskyLevels() {
    assertThat(RiskLevel.NONE.isRisky()).isFalse();
    assertThat(RiskLevel.WATCH.isRisky()).isTrue();
    assertThat(RiskLevel.HIGH.isRisky()).isTrue();
  }
}
