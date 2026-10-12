package com.ithopeschool.projectbe.domain.coach;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.coach.dto.WeeklyCoachRequest;
import com.ithopeschool.projectbe.domain.stats.StudyFacts;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactValue;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.TimeOfDay;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 스프링을 띄우지 않는 단위 테스트입니다. 코치 문단의 기본 문장과 근거 숫자를 만드는 규칙(이슈 #87)을 봅니다.
// feExample 로 시작하는 6개는 FE 함수(getCoachSummary) 주석의 예시를 그대로 옮긴 것입니다.
class CoachSummaryTest {

  // 요일 번호입니다. 0 = 일요일 … 6 = 토요일 (StudyFacts.completionByWeekday 의 순서)
  private static final int SUNDAY = 0;
  private static final int MONDAY = 1;
  private static final int TUESDAY = 2;

  private static final String COMPLETION_20_11 = "목표를 정한 타이머 20번 중 11번을 끝까지 했어. (완주율 55%)";
  private static final String COMPLETION_6_6 = "목표를 정한 타이머 6번 중 6번을 끝까지 했어. (완주율 100%)";
  private static final String STOP_62 = "중간에 멈춘 타이머는 목표의 62% 쯤에서 멈췄어. 다음엔 목표를 조금 짧게 잡아 보면 어때?";
  private static final String KEEP_GOING = "지금 흐름이 좋아. 다음 주도 이만큼만 이어 가 보자.";

  // 테스트에서 필요한 숫자만 채워서 StudyFacts 를 만드는 도우미입니다. 안 채운 숫자는 "모름" 입니다.
  private static final class Facts {
    private FactValue planRate = FactValue.UNKNOWN;
    private int completedCount = 0;
    private FactValue completionRate = FactValue.UNKNOWN;
    private FactValue startDelayMinutes = FactValue.UNKNOWN;
    private FactValue stopPoint = FactValue.UNKNOWN;
    private final List<FactValue> byWeekday = new ArrayList<>();

    private Facts() {
      for (int weekday = 0; weekday < 7; weekday++) {
        byWeekday.add(FactValue.UNKNOWN);
      }
    }

    // 계획 대비 실제 rate% (계획 planCount 개로 계산)
    private Facts planRate(int rate, int planCount) {
      planRate = new FactValue(rate, planCount);
      return this;
    }

    // 완주율 rate% (목표가 있는 타이머 total 번 중 completed 번 완주)
    private Facts completion(int rate, int total, int completed) {
      completionRate = new FactValue(rate, total);
      completedCount = completed;
      return this;
    }

    // 그 요일의 완주율 rate% (타이머 total 번)
    private Facts weekday(int weekday, int rate, int total) {
      byWeekday.set(weekday, new FactValue(rate, total));
      return this;
    }

    // 평균 시작 지연 minutes 분 (계획 planCount 개로 계산)
    private Facts startDelay(int minutes, int planCount) {
      startDelayMinutes = new FactValue(minutes, planCount);
      return this;
    }

    // 멈추는 지점 percent% (중간에 멈춘 타이머 stoppedCount 번)
    private Facts stopPoint(int percent, int stoppedCount) {
      stopPoint = new FactValue(percent, stoppedCount);
      return this;
    }

    private StudyFacts build() {
      Map<TimeOfDay, FactValue> byTimeOfDay =
          Map.of(
              TimeOfDay.MORNING, FactValue.UNKNOWN,
              TimeOfDay.AFTERNOON, FactValue.UNKNOWN,
              TimeOfDay.EVENING, FactValue.UNKNOWN,
              TimeOfDay.NIGHT, FactValue.UNKNOWN);
      return new StudyFacts(
          0,
          0,
          planRate,
          completedCount,
          completionRate,
          startDelayMinutes,
          stopPoint,
          List.copyOf(byWeekday),
          byTimeOfDay);
    }
  }

  // 기록이 하나도 없는 주입니다. (모든 숫자가 "모름")
  private static final StudyFacts NO_RECORDS = new Facts().build();

  private static WeeklyCoachRequest.Number number(String label, String value, String basis) {
    return new WeeklyCoachRequest.Number(label, value, basis);
  }

  // FE 예1 · 예2 의 이번 주입니다. 화요일 완주율만 바꿔서 씁니다.
  private static Facts exampleWeek(int tuesdayRate) {
    return new Facts()
        .planRate(64, 12)
        .completion(55, 20, 11)
        .weekday(TUESDAY, tuesdayRate, 5)
        .stopPoint(62, 9);
  }

  // FE 예3 · 예4 의 이번 주입니다. 시작 지연만 바꿔서 씁니다.
  private static Facts steadyWeek(int startDelayMinutes) {
    return new Facts().planRate(90, 4).completion(100, 6, 6).startDelay(startDelayMinutes, 4);
  }

  // ─── FE 예시 ───

  @Test
  @DisplayName("FE 예1) 요일 완주율이 전체보다 15%p 만 낮으면 요일 문장은 빠진다")
  void feExample1WeekdayGapIsTooSmall() {
    StudyFacts lastWeek = new Facts().planRate(56, 10).build();

    CoachSummary summary = CoachSummary.of(exampleWeek(40).build(), lastWeek);

    assertThat(summary.sentences())
        .containsExactly("이번 주는 계획의 64% 를 했어.", "지난주보다 8%p 올랐어.", COMPLETION_20_11, STOP_62);
    assertThat(summary.evidence())
        .containsExactly(
            number("계획 대비 실제", "64%", "계획 12개"),
            number("지난주 계획 대비 실제", "56%", "계획 10개"),
            number("완주율", "55%", "20번"),
            number("멈추는 지점", "목표의 62%", "9번"));
  }

  @Test
  @DisplayName("FE 예2) 요일 완주율이 전체보다 20%p 이상 낮으면 눈에 띄는 요일로 말한다")
  void feExample2WeakWeekdayIsMentioned() {
    StudyFacts lastWeek = new Facts().planRate(56, 10).build();

    CoachSummary summary = CoachSummary.of(exampleWeek(20).build(), lastWeek);

    assertThat(summary.sentences())
        .containsExactly(
            "이번 주는 계획의 64% 를 했어.",
            "지난주보다 8%p 올랐어.",
            COMPLETION_20_11,
            "눈에 띄는 건 화요일이야. 화요일 완주율은 20% 로 전체(55%)보다 낮아.",
            STOP_62);
    assertThat(summary.evidence()).contains(number("화요일 완주율", "20%", "5번"));
  }

  @Test
  @DisplayName("FE 예3) 멈춘 타이머가 없고 평균 23분 늦게 시작했으면 시작 알림을 권한다")
  void feExample3LateStart() {
    CoachSummary summary = CoachSummary.of(steadyWeek(23).build(), NO_RECORDS);

    assertThat(summary.sentences())
        .containsExactly(
            "이번 주는 계획의 90% 를 했어.", COMPLETION_6_6, "계획한 시각보다 평균 23분 늦게 시작했어. 시작 시각에 알림을 맞춰 두면 어때?");
    assertThat(summary.evidence())
        .containsExactly(
            number("계획 대비 실제", "90%", "계획 4개"),
            number("완주율", "100%", "6번"),
            number("시작 지연", "평균 23분", "계획 4개"));
  }

  @Test
  @DisplayName("FE 예4) 멈춘 타이머도 없고 시작도 늦지 않았으면 이어 가자고 한다")
  void feExample4KeepGoing() {
    CoachSummary summary = CoachSummary.of(steadyWeek(5).build(), NO_RECORDS);

    assertThat(summary.sentences())
        .containsExactly("이번 주는 계획의 90% 를 했어.", COMPLETION_6_6, KEEP_GOING);
    // 마지막 문장에는 숫자가 없어서 근거도 늘지 않습니다.
    assertThat(summary.evidence())
        .containsExactly(number("계획 대비 실제", "90%", "계획 4개"), number("완주율", "100%", "6번"));
  }

  @Test
  @DisplayName("FE 예5) 목표가 있는 타이머가 4번뿐이면 문단을 만들지 않는다")
  void feExample5TooFewTimers() {
    StudyFacts thisWeek = new Facts().planRate(90, 4).completion(75, 4, 3).build();

    assertThat(CoachSummary.of(thisWeek, NO_RECORDS)).isNull();
  }

  @Test
  @DisplayName("FE 예6) 계획이 없던 주는 첫 문장 없이 완주율부터 말한다")
  void feExample6NoPlans() {
    StudyFacts thisWeek = new Facts().completion(60, 5, 3).build();

    CoachSummary summary = CoachSummary.of(thisWeek, NO_RECORDS);

    assertThat(summary.sentences())
        .containsExactly("목표를 정한 타이머 5번 중 3번을 끝까지 했어. (완주율 60%)", KEEP_GOING);
    assertThat(summary.evidence()).containsExactly(number("완주율", "60%", "5번"));
  }

  // ─── 규칙 0: 기록이 모자란 주 ───

  @Test
  @DisplayName("기록이 하나도 없는 주는 문단을 만들지 않는다")
  void noRecordsMeansNoSummary() {
    assertThat(CoachSummary.of(NO_RECORDS, NO_RECORDS)).isNull();
  }

  // ─── 규칙 1: 지난주와 비교 ───

  @Test
  @DisplayName("지난주보다 낮으면 '내렸어' 라고 말한다")
  void planRateWentDown() {
    StudyFacts thisWeek = new Facts().planRate(50, 8).completion(60, 5, 3).build();
    StudyFacts lastWeek = new Facts().planRate(72, 9).build();

    CoachSummary summary = CoachSummary.of(thisWeek, lastWeek);

    assertThat(summary.sentences()).contains("지난주보다 22%p 내렸어.");
    assertThat(summary.evidence()).contains(number("지난주 계획 대비 실제", "72%", "계획 9개"));
  }

  @Test
  @DisplayName("지난주와 같거나 지난주 값이 없으면 비교 문장을 넣지 않는다")
  void noComparisonWhenSameOrUnknown() {
    StudyFacts thisWeek = new Facts().planRate(64, 12).completion(60, 5, 3).build();
    StudyFacts sameLastWeek = new Facts().planRate(64, 7).build();

    List<String> expected =
        List.of("이번 주는 계획의 64% 를 했어.", "목표를 정한 타이머 5번 중 3번을 끝까지 했어. (완주율 60%)", KEEP_GOING);
    assertThat(CoachSummary.of(thisWeek, sameLastWeek).sentences()).isEqualTo(expected);
    assertThat(CoachSummary.of(thisWeek, NO_RECORDS).sentences()).isEqualTo(expected);
  }

  // ─── 규칙 3: 눈에 띄는 요일 ───

  @Test
  @DisplayName("전체보다 정확히 20%p 낮은 요일은 말하고, 19%p 낮은 요일은 말하지 않는다")
  void weekdayGapBoundary() {
    StudyFacts gap20 = new Facts().completion(55, 20, 11).weekday(MONDAY, 35, 4).build();
    StudyFacts gap19 = new Facts().completion(55, 20, 11).weekday(MONDAY, 36, 4).build();

    assertThat(CoachSummary.of(gap20, NO_RECORDS).sentences())
        .contains("눈에 띄는 건 월요일이야. 월요일 완주율은 35% 로 전체(55%)보다 낮아.");
    assertThat(CoachSummary.of(gap19, NO_RECORDS).sentences())
        .noneMatch(sentence -> sentence.startsWith("눈에 띄는 건"));
  }

  @Test
  @DisplayName("타이머가 1번뿐인 요일은 아무리 낮아도 눈에 띄는 요일로 고르지 않는다")
  void weekdayWithOneTimerIsIgnored() {
    // 화요일은 0% 지만 1번뿐이라 빼고, 2번 있는 월요일(30%)을 고릅니다.
    StudyFacts thisWeek =
        new Facts().completion(55, 20, 11).weekday(TUESDAY, 0, 1).weekday(MONDAY, 30, 2).build();

    CoachSummary summary = CoachSummary.of(thisWeek, NO_RECORDS);

    assertThat(summary.sentences()).contains("눈에 띄는 건 월요일이야. 월요일 완주율은 30% 로 전체(55%)보다 낮아.");
    assertThat(summary.evidence()).contains(number("월요일 완주율", "30%", "2번"));
  }

  @Test
  @DisplayName("가장 낮은 요일이 여럿이면 일요일부터 세어 먼저 나오는 요일을 말한다")
  void firstWeekdayWinsOnTie() {
    StudyFacts thisWeek =
        new Facts()
            .completion(55, 20, 11)
            .weekday(TUESDAY, 20, 5)
            .weekday(SUNDAY, 20, 3)
            .weekday(MONDAY, 25, 4)
            .build();

    assertThat(CoachSummary.of(thisWeek, NO_RECORDS).sentences())
        .contains("눈에 띄는 건 일요일이야. 일요일 완주율은 20% 로 전체(55%)보다 낮아.");
  }

  // ─── 규칙 4: 권하는 말 ───

  @Test
  @DisplayName("멈춘 타이머가 2번 이상이면 시작이 늦었어도 멈춘 지점을 먼저 말한다")
  void stopPointComesBeforeLateStart() {
    StudyFacts thisWeek =
        new Facts().completion(60, 5, 3).stopPoint(62, 2).startDelay(40, 3).build();

    CoachSummary summary = CoachSummary.of(thisWeek, NO_RECORDS);

    assertThat(summary.sentences()).last().isEqualTo(STOP_62);
    // 말하지 않은 시작 지연은 근거에도 넣지 않습니다.
    assertThat(summary.evidence())
        .containsExactly(number("완주율", "60%", "5번"), number("멈추는 지점", "목표의 62%", "2번"));
  }

  @Test
  @DisplayName("멈춘 타이머가 1번뿐이면 멈춘 지점은 말하지 않고 다음 규칙으로 넘어간다")
  void singleStoppedTimerIsNotEnough() {
    StudyFacts thisWeek =
        new Facts().completion(80, 5, 4).stopPoint(30, 1).startDelay(16, 3).build();

    assertThat(CoachSummary.of(thisWeek, NO_RECORDS).sentences())
        .last()
        .isEqualTo("계획한 시각보다 평균 16분 늦게 시작했어. 시작 시각에 알림을 맞춰 두면 어때?");
  }

  @Test
  @DisplayName("평균 시작 지연이 정확히 15분이면 늦다고 말하지 않는다 (15분을 넘어야 한다)")
  void lateStartBoundary() {
    StudyFacts thisWeek = new Facts().completion(100, 5, 5).startDelay(15, 3).build();

    assertThat(CoachSummary.of(thisWeek, NO_RECORDS).sentences()).last().isEqualTo(KEEP_GOING);
  }

  // ─── 월간 ───

  @Test
  @DisplayName("월간 리포트는 같은 규칙에 기간을 부르는 말만 바꿔 쓴다")
  void monthWords() {
    StudyFacts thisMonth = new Facts().planRate(70, 40).completion(100, 30, 30).build();
    StudyFacts lastMonth = new Facts().planRate(60, 35).build();

    CoachSummary summary = CoachSummary.of(thisMonth, lastMonth, CoachSummary.MONTH_WORDS);

    assertThat(summary.sentences())
        .containsExactly(
            "이 4주는 계획의 70% 를 했어.",
            "그 전 4주보다 10%p 올랐어.",
            "목표를 정한 타이머 30번 중 30번을 끝까지 했어. (완주율 100%)",
            "지금 흐름이 좋아. 다음 달도 이만큼만 이어 가 보자.");
    assertThat(summary.evidence()).contains(number("그 전 4주 계획 대비 실제", "60%", "계획 35개"));
  }

  // ─── AI 에게 건넬 수 있는 모양인지 ───

  @Test
  @DisplayName("만든 근거 숫자는 FE 가 보낼 때와 같은 검사(30자, 쓸 수 있는 글자)를 통과한다")
  void evidencePassesTheRequestValidation() {
    // 문장이 가장 많이 나오는 경우(지난주 비교 + 눈에 띄는 요일 + 멈춘 지점)와 시작 지연, 월간을 모두 봅니다.
    StudyFacts lastWeek = new Facts().planRate(56, 10).build();
    List<WeeklyCoachRequest.Number> evidence = new ArrayList<>();
    evidence.addAll(CoachSummary.of(exampleWeek(20).build(), lastWeek).evidence());
    evidence.addAll(CoachSummary.of(steadyWeek(23).build(), NO_RECORDS).evidence());
    evidence.addAll(
        CoachSummary.of(exampleWeek(20).build(), lastWeek, CoachSummary.MONTH_WORDS).evidence());

    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();
      for (WeeklyCoachRequest.Number number : evidence) {
        assertThat(validator.validate(number)).as("근거 숫자 %s", number).isEmpty();
      }
    }
  }
}
