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

  // ---- 시험 기간의 관리 강도 상한 2 (이슈 #232) ----

  @Test
  void capCareLevelKeepsTheLevelAtTwoOrBelow() {
    // 시험 기간에는 3·4 가 2 로 내려가고, 0·1·2 는 그대로입니다. 0(회복)으로 내리지는 않습니다.
    assertThat(ExamPeriod.capCareLevel(4, true)).isEqualTo(2);
    assertThat(ExamPeriod.capCareLevel(3, true)).isEqualTo(2);
    assertThat(ExamPeriod.capCareLevel(2, true)).isEqualTo(2);
    assertThat(ExamPeriod.capCareLevel(1, true)).isEqualTo(1);
    assertThat(ExamPeriod.capCareLevel(0, true)).isZero();
    // 시험 기간이 아니면 그대로입니다.
    assertThat(ExamPeriod.capCareLevel(3, false)).isEqualTo(3);
    assertThat(ExamPeriod.capCareLevel(4, false)).isEqualTo(4);
  }

  // ---- 낮춘 이유 문장 (이슈 #58) ----

  @Test
  void reasonOnlyDuringTheExamPeriod() {
    // 시험 기간에는 이유 문장이 있고, 기간이 끝나면(시험 3일 후) 없어집니다.
    assertThat(ExamPeriod.reasonFor(LocalDate.of(2026, 10, 13), List.of(EXAM)))
        .isEqualTo("시험 기간이라 독촉은 쉬어갈게. 컨디션 챙기면서 할 수 있는 만큼만 하자.");
    assertThat(ExamPeriod.reasonFor(LocalDate.of(2026, 10, 17), List.of(EXAM)))
        .isEqualTo(ExamPeriod.REASON);
    assertThat(ExamPeriod.reasonFor(LocalDate.of(2026, 10, 18), List.of(EXAM))).isNull();
    assertThat(ExamPeriod.reasonFor(LocalDate.of(2026, 10, 11), List.of(EXAM))).isNull();
    assertThat(ExamPeriod.reasonFor(LocalDate.of(2026, 10, 13), List.of())).isNull();
  }

  @Test
  void reasonDoesNotBlameTheStudent() {
    // AI 가 하는 말에는 깎아내리는 말이 없어야 합니다. (CLAUDE.md 4장) 이유 문장은 쉬어 간다는 것만 말합니다.
    for (String word : new String[] {"왜", "못", "게으", "한심", "실망", "또"}) {
      assertThat(ExamPeriod.REASON).as("'%s' 가 없어야 함", word).doesNotContain(word);
    }
  }
}
