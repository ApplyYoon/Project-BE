package com.ithopeschool.projectbe.domain.checkin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 질문을 고르는 규칙만 확인합니다. DB 없이 숫자와 참/거짓만 넣어 봅니다.
class CheckInQuestionsTest {

  @Test
  @DisplayName("기본 질문은 언제나 기분 하나다")
  void basicIsOnlyMood() {
    assertThat(CheckInQuestions.pick(null, false, false).basic()).containsExactly("mood");
    assertThat(CheckInQuestions.pick(10, true, true).basic()).containsExactly("mood");
  }

  @Test
  @DisplayName("컨디션과 예상 순공은 매일 자세히에서 묻고, 기본 질문에는 없다")
  void conditionAndExpectedAreAlwaysInDetail() {
    CheckInQuestions questions = CheckInQuestions.pick(100, false, false);

    assertThat(questions.detail()).containsExactly("condition", "expectedMinutes");
    assertThat(questions.basic()).doesNotContain("condition", "expectedMinutes");
  }

  @Test
  @DisplayName("미달 기준은 100% 미만이다. 계획이 없던 날(null)은 미달이 아니다")
  void missThreshold() {
    assertThat(CheckInQuestions.MISS_THRESHOLD_PERCENT).isEqualTo(100);
    assertThat(CheckInQuestions.isMissedDay(0)).isTrue();
    assertThat(CheckInQuestions.isMissedDay(79)).isTrue();
    assertThat(CheckInQuestions.isMissedDay(99)).isTrue();
    assertThat(CheckInQuestions.isMissedDay(100)).isFalse();
    assertThat(CheckInQuestions.isMissedDay(null)).isFalse();
  }

  @Test
  @DisplayName("어제 못 채웠으면 이유를 묻고, 다 채웠거나 계획이 없었으면 묻지 않는다")
  void asksMissReasonOnlyOnMissedDay() {
    assertThat(CheckInQuestions.pick(79, false, false).detail()).contains("missReason");
    assertThat(CheckInQuestions.pick(99, false, false).detail()).contains("missReason");
    assertThat(CheckInQuestions.pick(100, false, false).detail()).doesNotContain("missReason");
    assertThat(CheckInQuestions.pick(null, false, false).detail()).doesNotContain("missReason");
  }

  @Test
  @DisplayName("어제 못 채웠어도, 그만둔 이유(stopReason)가 이미 있으면 이유를 묻지 않는다")
  void skipsMissReasonWhenStopReasonExists() {
    assertThat(CheckInQuestions.pick(50, true, false).detail()).doesNotContain("missReason");
  }

  @Test
  @DisplayName("어제 약속이 있으면 지켰는지 묻고, 없으면 묻지 않는다")
  void asksPromiseKeptOnlyWhenPromised() {
    assertThat(CheckInQuestions.pick(100, false, true).detail()).contains("promiseKept");
    assertThat(CheckInQuestions.pick(100, false, false).detail()).doesNotContain("promiseKept");
  }

  @Test
  @DisplayName("조건이 모두 맞으면 자세히 질문 네 개가 정해진 순서로 나온다. 개선안 질문은 아직 없다")
  void allConditions() {
    assertThat(CheckInQuestions.pick(79, false, true).detail())
        .containsExactly("condition", "expectedMinutes", "missReason", "promiseKept");
  }
}
