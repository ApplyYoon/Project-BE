package com.ithopeschool.projectbe.domain.exam;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.exam.ExamDday.ExamDate;
import com.ithopeschool.projectbe.domain.exam.ExamDday.Split;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

// 시험 날짜 계산 규칙(이슈 #16)을 확인합니다.
// feExample 로 시작하는 테스트는 FE 함수(src/lib/exams.ts · src/lib/firstMe.ts) 주석의 예시를 그대로 옮긴 것입니다.
// 시험은 2026-10-17(토) 입니다.
class ExamDdayTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private static final LocalDate EXAM = LocalDate.of(2026, 10, 17);

  private static LocalDate october(int day) {
    return LocalDate.of(2026, 10, day);
  }

  // 한국 시간 10월 "day일 시:분" 을 순간(Instant)으로 바꾸는 도우미입니다.
  private static Instant at(int day, int hour, int minute) {
    return ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, SEOUL).toInstant();
  }

  // ─── 며칠 남았나 ───

  @Test
  void feExampleTodayTomorrowYesterday() {
    // FE 예) 오늘이면 0, 내일이면 1, 어제면 -1
    assertThat(ExamDday.daysUntil(EXAM, october(17))).isEqualTo(0);
    assertThat(ExamDday.daysUntil(EXAM, october(16))).isEqualTo(1);
    assertThat(ExamDday.daysUntil(EXAM, october(18))).isEqualTo(-1);
  }

  @Test
  void feExampleDatesAreComparedNotTimes() {
    // FE 예) 밤 11시에 봐도 "내일 시험" 은 1 입니다. (24시간이 안 남았어도 0 이 아닙니다)
    assertThat(ExamDday.daysUntil(EXAM, at(16, 23, 0))).isEqualTo(1);
    assertThat(ExamDday.daysUntil(EXAM, at(16, 9, 0))).isEqualTo(1);
  }

  @Test
  void feExampleBeforeFourAmIsStillYesterday() {
    // FE 예) 새벽 2시에는 아직 어제라서 "오늘 시험" 도 1 입니다.
    assertThat(ExamDday.daysUntil(EXAM, at(17, 2, 0))).isEqualTo(1);
    // 새벽 4시가 되면 시험 당일입니다.
    assertThat(ExamDday.daysUntil(EXAM, at(17, 4, 0))).isEqualTo(0);
    // 시험 다음 날 새벽 3시도 아직 시험 당일입니다.
    assertThat(ExamDday.daysUntil(EXAM, at(18, 3, 0))).isEqualTo(0);
  }

  @Test
  void daysUntilCrossesMonthsAndYears() {
    assertThat(ExamDday.daysUntil(LocalDate.of(2026, 11, 18), october(10))).isEqualTo(39);
    assertThat(ExamDday.daysUntil(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 12, 31)))
        .isEqualTo(1);
  }

  // ─── 남은 날 (지난 시험은 없음) ───

  @Test
  void feExampleDaysLeft() {
    // FE 예) 오늘 2026-10-10, 시험 2026-11-18 → 39 / 시험 2026-10-01(이미 지남) → null
    assertThat(ExamDday.daysLeft(LocalDate.of(2026, 11, 18), october(10))).isEqualTo(39);
    assertThat(ExamDday.daysLeft(october(1), october(10))).isNull();
    // 시험 당일은 0 입니다. ("남은 날 없음" 이 아닙니다)
    assertThat(ExamDday.daysLeft(october(10), october(10))).isEqualTo(0);
  }

  // ─── D-day 글자 ───

  @Test
  void feExampleDdayText() {
    // FE 예) 12 → 'D-12' / 0 → 'D-day' / -3 → 'D+3'
    assertThat(ExamDday.text(12)).isEqualTo("D-12");
    assertThat(ExamDday.text(0)).isEqualTo("D-day");
    assertThat(ExamDday.text(-3)).isEqualTo("D+3");
    assertThat(ExamDday.text(1)).isEqualTo("D-1");
    assertThat(ExamDday.text(-1)).isEqualTo("D+1");
  }

  // ─── 실제 점수를 넣어도 되는지 ───

  @Test
  void actualScoreCanBeEnteredFromTheDayAfterTheExam() {
    // 시험 전날과 당일에는 넣을 수 없고, 다음 날부터 넣을 수 있습니다.
    assertThat(ExamDday.canEnterActualScore(EXAM, october(16))).isFalse();
    assertThat(ExamDday.canEnterActualScore(EXAM, october(17))).isFalse();
    assertThat(ExamDday.canEnterActualScore(EXAM, october(18))).isTrue();
    assertThat(ExamDday.canEnterActualScore(EXAM, october(30))).isTrue();
  }

  // ─── 다가오는 시험 · 지난 시험 ───

  @Test
  void splitPutsTodayInUpcomingAndSortsByCloseness() {
    // 오늘은 10/17. 다가오는 시험은 가까운 날짜가 앞, 지난 시험은 최근 날짜가 앞입니다.
    ExamDate today = new ExamDate(1L, october(17));
    ExamDate nextWeek = new ExamDate(2L, october(24));
    ExamDate tomorrow = new ExamDate(3L, october(18));
    ExamDate yesterday = new ExamDate(4L, october(16));
    ExamDate lastMonth = new ExamDate(5L, LocalDate.of(2026, 9, 10));
    ExamDate lastWeek = new ExamDate(6L, october(10));

    Split split =
        ExamDday.split(
            List.of(nextWeek, lastMonth, today, yesterday, tomorrow, lastWeek), october(17));

    assertThat(split.upcoming()).containsExactly(today, tomorrow, nextWeek);
    assertThat(split.past()).containsExactly(yesterday, lastWeek, lastMonth);
  }

  @Test
  void examsOnTheSameDateKeepTheirOrder() {
    ExamDate first = new ExamDate(9L, october(24));
    ExamDate second = new ExamDate(3L, october(24));
    ExamDate pastFirst = new ExamDate(8L, october(1));
    ExamDate pastSecond = new ExamDate(2L, october(1));

    Split split = ExamDday.split(List.of(first, pastFirst, second, pastSecond), october(17));

    assertThat(split.upcoming()).containsExactly(first, second);
    assertThat(split.past()).containsExactly(pastFirst, pastSecond);
  }

  @Test
  void splitOfNoExamsIsEmpty() {
    assertThat(ExamDday.split(List.of(), october(17))).isEqualTo(new Split(List.of(), List.of()));
  }

  // ─── 점수 범위 ───

  @Test
  void feExampleScoreRange() {
    // FE 예) '85' → 85 / '101' → null (0 ~ 100 만 받습니다)
    assertThat(ExamDday.isValidScore(85)).isTrue();
    assertThat(ExamDday.isValidScore(101)).isFalse();
    assertThat(ExamDday.isValidScore(0)).isTrue();
    assertThat(ExamDday.isValidScore(100)).isTrue();
    assertThat(ExamDday.isValidScore(-1)).isFalse();
    // 안 적은 점수는 올바른 점수가 아닙니다. (빈 칸인지 틀린 값인지는 부르는 쪽에서 구분합니다)
    assertThat(ExamDday.isValidScore(null)).isFalse();
  }
}
