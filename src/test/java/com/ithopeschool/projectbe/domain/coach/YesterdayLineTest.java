package com.ithopeschool.projectbe.domain.coach;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.coach.dto.WeeklyCoachRequest;
import com.ithopeschool.projectbe.domain.summary.DaySummary;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 스프링을 띄우지 않는 단위 테스트입니다. 홈의 "어제 한 줄" 기본 문장을 만드는 규칙(이슈 #87)을 봅니다.
// feExample 로 시작하는 3개는 FE 함수(getYesterdayLine) 주석의 예시를 그대로 옮긴 것입니다.
class YesterdayLineTest {

  private static final Instant TEN_AM = Instant.parse("2026-10-11T01:00:00Z");

  // 순공시간과 달성률만 정해서 어제의 요약을 만듭니다. (나머지 값은 문장에 쓰이지 않습니다)
  private static DaySummary summary(int studyMinutes, Integer achievementRate) {
    return new DaySummary(studyMinutes, achievementRate, studyMinutes > 0 ? TEN_AM : null, 0, 0);
  }

  private static WeeklyCoachRequest.Number number(String label, String value, String basis) {
    return new WeeklyCoachRequest.Number(label, value, basis);
  }

  // ─── FE 예시 ───

  @Test
  @DisplayName("FE 예1) 기록도 계획도 있으면 순공시간과 달성률을 같이 말한다")
  void feExample1StudyWithPlan() {
    assertThat(YesterdayLine.of(summary(190, 79), true))
        .isEqualTo(
            new YesterdayLine(
                "어제 순공 3시간 10분, 계획의 79%야.",
                List.of(
                    number("어제 순공시간", "3시간 10분", "타이머 기록"),
                    number("어제 계획 대비 실제", "79%", "어제 계획"))));
  }

  @Test
  @DisplayName("FE 예2) 계획 없이 공부했으면 '계획의 0%' 가 아니라 해냈다고 말한다")
  void feExample2StudyWithoutPlan() {
    assertThat(YesterdayLine.of(summary(60, null), false))
        .isEqualTo(
            new YesterdayLine(
                "어제 순공 1시간, 계획 없이도 해냈네.", List.of(number("어제 순공시간", "1시간", "타이머 기록"))));
  }

  @Test
  @DisplayName("FE 예3) 어제 기록이 없으면 못 한 것을 짚지 않고, AI 에게 줄 숫자도 없다")
  void feExample3NoStudy() {
    assertThat(YesterdayLine.of(summary(0, 0), true))
        .isEqualTo(new YesterdayLine("오늘부터 시작해 볼까?", List.of()));
    // 계획도 없던 날도 같습니다.
    assertThat(YesterdayLine.of(summary(0, null), false))
        .isEqualTo(new YesterdayLine("오늘부터 시작해 볼까?", List.of()));
  }

  // ─── 계획이 있었는지 ───

  @Test
  @DisplayName("계획은 있었지만 목표가 0분이라 달성률을 모르면 계획 없이 한 날처럼 말한다")
  void planWithoutTargetMinutes() {
    YesterdayLine line = YesterdayLine.of(summary(30, null), true);

    assertThat(line.sentence()).isEqualTo("어제 순공 30분, 계획 없이도 해냈네.");
    assertThat(line.evidence()).containsExactly(number("어제 순공시간", "30분", "타이머 기록"));
  }

  @Test
  @DisplayName("달성률이 100% 여도 0% 여도 숫자 그대로 말한다 (평가하는 말을 붙이지 않는다)")
  void rateIsSaidAsItIs() {
    assertThat(YesterdayLine.of(summary(120, 100), true).sentence())
        .isEqualTo("어제 순공 2시간, 계획의 100%야.");
    // 1분 공부하고 계획이 아주 많았던 날: 달성률 0% 도 숫자 그대로입니다.
    assertThat(YesterdayLine.of(summary(1, 0), true).sentence()).isEqualTo("어제 순공 1분, 계획의 0%야.");
  }

  // ─── 시간을 적는 모양 ───

  @Test
  @DisplayName("시간은 '40분' · '2시간' · '2시간 14분' 모양으로 적는다")
  void minutesText() {
    assertThat(YesterdayLine.of(summary(40, null), false).sentence()).startsWith("어제 순공 40분,");
    assertThat(YesterdayLine.of(summary(59, null), false).sentence()).startsWith("어제 순공 59분,");
    assertThat(YesterdayLine.of(summary(60, null), false).sentence()).startsWith("어제 순공 1시간,");
    assertThat(YesterdayLine.of(summary(120, null), false).sentence()).startsWith("어제 순공 2시간,");
    assertThat(YesterdayLine.of(summary(134, null), false).sentence()).startsWith("어제 순공 2시간 14분,");
  }

  // ─── AI 에게 건넬 수 있는 모양인지 ───

  @Test
  @DisplayName("만든 근거 숫자는 FE 가 보낼 때와 같은 검사(30자, 쓸 수 있는 글자)를 통과한다")
  void evidencePassesTheRequestValidation() {
    List<WeeklyCoachRequest.Number> evidence =
        YesterdayLine.of(summary(1439, 100), true).evidence();

    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();
      for (WeeklyCoachRequest.Number number : evidence) {
        assertThat(validator.validate(number)).as("근거 숫자 %s", number).isEmpty();
      }
    }
  }

  @Test
  @DisplayName("기본 문장에 쓴 숫자는 모두 근거에 있다 (AI 문장과 같은 검사를 통과한다)")
  void sentenceUsesOnlyEvidenceNumbers() {
    YesterdayLine line = YesterdayLine.of(summary(190, 79), true);

    assertThat(CoachParagraphCheck.isSafe(line.sentence(), line.evidence())).isTrue();
  }
}
