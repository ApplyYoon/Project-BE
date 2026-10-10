package com.ithopeschool.projectbe.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

// "시험 기간" 규칙(이슈 #58)을 확인합니다. 시험 직전 3일 ~ 직후 2일이 시험 기간입니다.
// feExample 로 시작하는 5개는 FE 함수(isNearExam) 주석의 예시를 그대로 옮긴 것입니다.
class ExamPeriodTest {

  // 테스트에서 쓰는 시험 날짜: 2026년 10월 15일
  private static final LocalDate EXAM = LocalDate.of(2026, 10, 15);

  @Test
  void feExample1ThreeDaysBeforeIsNear() {
    // FE 예1) 오늘이 시험 3일 전 → true
    assertThat(ExamPeriod.isNearExam(LocalDate.of(2026, 10, 12), List.of(EXAM))).isTrue();
  }

  @Test
  void feExample2ExamDayIsNear() {
    // FE 예2) 오늘이 시험 당일 → true
    assertThat(ExamPeriod.isNearExam(LocalDate.of(2026, 10, 15), List.of(EXAM))).isTrue();
  }

  @Test
  void feExample3TwoDaysAfterIsNear() {
    // FE 예3) 오늘이 시험 2일 후 → true
    assertThat(ExamPeriod.isNearExam(LocalDate.of(2026, 10, 17), List.of(EXAM))).isTrue();
  }

  @Test
  void feExample4FourDaysBeforeIsNotNear() {
    // FE 예4) 오늘이 시험 4일 전 → false
    assertThat(ExamPeriod.isNearExam(LocalDate.of(2026, 10, 11), List.of(EXAM))).isFalse();
  }

  @Test
  void feExample5ThreeDaysAfterIsNotNear() {
    // FE 예5) 오늘이 시험 3일 후 → false (기간이 끝나면 원래대로 돌아옵니다)
    assertThat(ExamPeriod.isNearExam(LocalDate.of(2026, 10, 18), List.of(EXAM))).isFalse();
  }

  @Test
  void noExamMeansNotNear() {
    // 등록한 시험이 없으면 시험 기간이 아닙니다.
    assertThat(ExamPeriod.isNearExam(LocalDate.of(2026, 10, 15), List.of())).isFalse();
  }

  @Test
  void anyOneOfSeveralExamsIsEnough() {
    // 시험이 여러 개면 하나만 걸려도 시험 기간입니다. (한 달 전 시험 + 내일 시험)
    List<LocalDate> exams = List.of(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 10, 16));
    assertThat(ExamPeriod.isNearExam(LocalDate.of(2026, 10, 15), exams)).isTrue();
  }

  @Test
  void backToBackExamsKeepThePeriodGoing() {
    // 시험이 이어서 있으면(10/15, 10/19) 그 사이 날도 계속 시험 기간입니다.
    // 10/18 은 첫 시험의 3일 후라 그것만 보면 끝났지만, 다음 시험의 1일 전입니다.
    List<LocalDate> exams = List.of(EXAM, LocalDate.of(2026, 10, 19));
    assertThat(ExamPeriod.isNearExam(LocalDate.of(2026, 10, 18), exams)).isTrue();
    // 마지막 시험의 3일 후(10/22)에는 끝납니다.
    assertThat(ExamPeriod.isNearExam(LocalDate.of(2026, 10, 22), exams)).isFalse();
  }

  @Test
  void farAwayExamsAreNotNear() {
    // 전부 멀리 있는 시험이면 시험 기간이 아닙니다.
    List<LocalDate> exams = List.of(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 11, 20));
    assertThat(ExamPeriod.isNearExam(LocalDate.of(2026, 10, 15), exams)).isFalse();
  }

  @Test
  void countsDaysAcrossMonths() {
    // 달이 바뀌어도 날짜 수를 맞게 셉니다. 9월 28일은 10월 1일 시험의 3일 전입니다.
    LocalDate exam = LocalDate.of(2026, 10, 1);
    assertThat(ExamPeriod.isNearExam(LocalDate.of(2026, 9, 28), List.of(exam))).isTrue();
    assertThat(ExamPeriod.isNearExam(LocalDate.of(2026, 9, 27), List.of(exam))).isFalse();
  }

  @Test
  void todayFollowsTheFourAmDayRule() {
    // "오늘" 은 새벽 4시에 바뀝니다. (DayRule)
    ZoneId seoul = ZoneId.of("Asia/Seoul");
    Instant beforeFour = ZonedDateTime.of(2026, 10, 12, 3, 59, 0, 0, seoul).toInstant();
    Instant atFour = ZonedDateTime.of(2026, 10, 12, 4, 0, 0, 0, seoul).toInstant();

    // 10월 12일 03:59 는 아직 10월 11일 = 시험 4일 전 → 시험 기간이 아님
    assertThat(ExamPeriod.isNearExam(beforeFour, List.of(EXAM))).isFalse();
    // 10월 12일 04:00 부터 10월 12일 = 시험 3일 전 → 시험 기간
    assertThat(ExamPeriod.isNearExam(atFour, List.of(EXAM))).isTrue();
  }
}
