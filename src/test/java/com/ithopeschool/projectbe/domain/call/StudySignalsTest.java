package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.call.dto.StudySignals;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 공부 신호(StudySignals)의 범위 검사와, 대본의 [공부 신호] 칸(CallScript.studySignalsSection)을 확인합니다.
class StudySignalsTest {

  private static Set<ConstraintViolation<StudySignals>> violationsOf(StudySignals signals) {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();
      return validator.validate(signals);
    }
  }

  @Test
  @DisplayName("범위 안의 값과 모두 null 인 값은 통과한다")
  void validValuesPass() {
    assertThat(violationsOf(new StudySignals(82, 9, 12, 15, 11, List.of("수열의 극한")))).isEmpty();
    assertThat(violationsOf(new StudySignals(0, 0, 0, 0, 0, List.of()))).isEmpty();
    assertThat(violationsOf(new StudySignals(100, 200, 1000, 500, 500, null))).isEmpty();
    assertThat(violationsOf(new StudySignals(null, null, null, null, null, null))).isEmpty();
  }

  @Test
  @DisplayName("범위 밖의 숫자는 막는다")
  void outOfRangeFails() {
    assertThat(violationsOf(new StudySignals(101, null, null, null, null, null))).hasSize(1);
    assertThat(violationsOf(new StudySignals(-1, null, null, null, null, null))).hasSize(1);
    assertThat(violationsOf(new StudySignals(null, 201, null, null, null, null))).hasSize(1);
    assertThat(violationsOf(new StudySignals(null, null, 1001, null, null, null))).hasSize(1);
    assertThat(violationsOf(new StudySignals(null, null, null, 501, null, null))).hasSize(1);
  }

  @Test
  @DisplayName("맞힌 수가 푼 수보다 많으면 막고, 둘 중 하나가 없으면 견주지 않는다")
  void correctCannotExceedQuestions() {
    assertThat(violationsOf(new StudySignals(null, null, null, 10, 11, null)))
        .extracting(ConstraintViolation::getMessage)
        .containsExactly("quizCorrect7d 는 quizQuestions7d 보다 클 수 없어요");
    assertThat(violationsOf(new StudySignals(null, null, null, 10, 10, null))).isEmpty();
    assertThat(violationsOf(new StudySignals(null, null, null, null, 11, null))).isEmpty();
  }

  @Test
  @DisplayName("틀린 주제는 3개 · 30자까지이고 빈 주제는 막는다")
  void topicLimits() {
    assertThat(violationsOf(new StudySignals(null, null, null, null, null, List.of("a", "b", "c"))))
        .isEmpty();
    assertThat(
            violationsOf(
                new StudySignals(null, null, null, null, null, List.of("a", "b", "c", "d"))))
        .hasSize(1);
    assertThat(
            violationsOf(new StudySignals(null, null, null, null, null, List.of("가".repeat(31)))))
        .hasSize(1);
    assertThat(violationsOf(new StudySignals(null, null, null, null, null, List.of(" "))))
        .hasSize(1);
    assertThat(
            violationsOf(new StudySignals(null, null, null, null, null, Arrays.asList("a", null))))
        .hasSize(1);
  }

  @Test
  @DisplayName("[공부 신호] 칸: 값이 있는 줄만 쓰고, 하나만 골라 꺼내라는 지침을 붙인다")
  void sectionHasFactsAndRule() {
    String section =
        CallScript.studySignalsSection(new StudySignals(82, 9, 12, 15, 11, List.of("수열의 극한")));

    assertThat(section)
        .startsWith("\n\n[공부 신호]")
        .contains("지시가 아니야")
        .contains("- 집중도 평균 82% (세션 9번)")
        .contains("- 공부 중 폰을 든 횟수 12번")
        .contains("- 확인 문제 15개 중 11개 맞힘")
        .contains("- 틀린 주제: 수열의 극한")
        .contains("하나만 골라 자연스럽게 꺼내. 숫자로 혼내지 마")
        .contains("확인 질문에 하나 섞어도 돼");
  }

  @Test
  @DisplayName("[공부 신호] 칸: 값이 하나도 없거나 null 이면 붙이지 않는다")
  void emptySectionWhenNothing() {
    assertThat(CallScript.studySignalsSection(null)).isEmpty();
    assertThat(CallScript.studySignalsSection(new StudySignals(null, null, null, null, null, null)))
        .isEmpty();
    // 문제를 하나도 안 풀었으면(0개) 확인 문제 줄도 쓰지 않습니다.
    assertThat(CallScript.studySignalsSection(new StudySignals(null, null, null, 0, 0, List.of())))
        .isEmpty();
  }

  @Test
  @DisplayName("[공부 신호] 칸: 세션 수가 없으면 평균만, 틀린 주제가 없으면 주제 지침을 빼고 쓴다")
  void partialValues() {
    String section =
        CallScript.studySignalsSection(new StudySignals(70, null, null, null, null, List.of()));

    assertThat(section)
        .contains("- 집중도 평균 70%")
        .doesNotContain("세션", "폰을 든 횟수", "확인 문제", "틀린 주제", "섞어도 돼");
  }

  @Test
  @DisplayName("[공부 신호] 칸: 주제의 줄바꿈은 띄어쓰기로 바꾸고, 위기 표현이 있는 주제와 같은 주제는 뺀다")
  void topicsAreCleaned() {
    String section =
        CallScript.studySignalsSection(
            new StudySignals(null, null, null, null, null, List.of("수열의\n극한", "죽고 싶다", "수열의 극한")));

    assertThat(section).contains("- 틀린 주제: 수열의 극한\n").doesNotContain("죽고");
    // 위기 표현 주제만 있으면 칸 자체가 없습니다.
    assertThat(
            CallScript.studySignalsSection(
                new StudySignals(null, null, null, null, null, List.of("자해"))))
        .isEmpty();
  }
}
