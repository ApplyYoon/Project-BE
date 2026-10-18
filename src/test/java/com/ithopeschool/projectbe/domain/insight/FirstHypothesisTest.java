package com.ithopeschool.projectbe.domain.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.onboarding.Chronotype;
import com.ithopeschool.projectbe.domain.onboarding.Distraction;
import com.ithopeschool.projectbe.domain.onboarding.FailureCause;
import com.ithopeschool.projectbe.domain.onboarding.Grade;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// 설문 답 → 첫 가설 규칙을 확인합니다. DB 없이 값만 넣어 봅니다.
class FirstHypothesisTest {

  // 필수 칸(학년·시험·시험 날짜·하루 공부 가능 시간)만 채운 설문 상자
  private static OnboardingAnswer.Values requiredOnly() {
    OnboardingAnswer.Values values = new OnboardingAnswer.Values();
    values.grade = Grade.values()[0];
    values.targetExam = "수능";
    values.targetExamDate = LocalDate.of(2027, 11, 18);
    values.dailyAvailableMinutes = 180;
    return values;
  }

  private static OnboardingAnswer answerOf(OnboardingAnswer.Values values) {
    return new OnboardingAnswer(7L, Instant.parse("2026-10-17T09:00:00Z"), values);
  }

  @Test
  @DisplayName("설문을 안 했거나 필수 칸만 답했으면 가설을 억지로 만들지 않는다 (빈 목록)")
  void noAnswersNoHypotheses() {
    assertThat(FirstHypothesis.from(null)).isEmpty();
    // 하루 공부 가능 시간은 모두가 답하는 칸이라 가설 재료로 쓰지 않습니다.
    assertThat(FirstHypothesis.from(answerOf(requiredOnly()))).isEmpty();
  }

  @Test
  @DisplayName("이슈 예시: 주말에 무너진다고 답하면 '주말에 무너지는 편이라고 했지?' 가설이 #37 리듬 분석과 이어진다")
  void weekendExample() {
    OnboardingAnswer.Values values = requiredOnly();
    values.pastFailureCauses = EnumSet.of(FailureCause.WEEKEND);

    List<FirstHypothesis.Hypothesis> hypotheses = FirstHypothesis.from(answerOf(values));

    assertThat(hypotheses).hasSize(1);
    FirstHypothesis.Hypothesis weekend = hypotheses.get(0);
    assertThat(weekend.answerType()).isEqualTo("pastFailureCauses");
    assertThat(weekend.answer()).isEqualTo("weekend");
    assertThat(weekend.sentence()).startsWith("주말에 무너지는 편이라고 했지?");
    assertThat(weekend.analysis()).isEqualTo(HypothesisAnalysis.RHYTHM);
    assertThat(weekend.analysis().getIssue()).isEqualTo(37);
  }

  @Test
  @DisplayName("답 하나에 가설 하나, 순서는 무너졌던 때 → 아침형/저녁형 → 집중 시간 → 방해 요소")
  void oneHypothesisPerAnswerInOrder() {
    OnboardingAnswer.Values values = requiredOnly();
    values.pastFailureCauses = EnumSet.of(FailureCause.TOO_MUCH_PLAN, FailureCause.AFTER_EXAM);
    values.chronotype = Chronotype.NIGHT;
    values.maxFocusMinutes = 50;
    values.biggestDistraction = Distraction.SLEEPY;

    List<FirstHypothesis.Hypothesis> hypotheses = FirstHypothesis.from(answerOf(values));

    assertThat(hypotheses)
        .extracting(FirstHypothesis.Hypothesis::answer)
        .containsExactly("afterExam", "tooMuchPlan", "night", "50", "졸림");
    assertThat(hypotheses)
        .extracting(FirstHypothesis.Hypothesis::analysis)
        .containsExactly(
            HypothesisAnalysis.RHYTHM,
            HypothesisAnalysis.PERCEPTION_GAP,
            HypothesisAnalysis.PERCEPTION_GAP,
            HypothesisAnalysis.PERCEPTION_GAP,
            HypothesisAnalysis.CONDITION);
    assertThat(hypotheses.get(3).sentence()).startsWith("한 번에 50분 정도 집중할 수 있다고 했지?");
  }

  @Test
  @DisplayName("DB 에서 읽어 순서가 뒤섞인 목록이어도 무너졌던 때는 FailureCause 순서로 나온다")
  void orderDoesNotDependOnStoredOrder() {
    OnboardingAnswer answer = answerOf(requiredOnly());
    // DB 에서 읽은 목록은 EnumSet 이 아니라 순서가 정해져 있지 않습니다. 일부러 거꾸로 넣어 봅니다.
    Set<FailureCause> shuffled =
        new LinkedHashSet<>(List.of(FailureCause.PHONE, FailureCause.WEEKEND));
    ReflectionTestUtils.setField(answer, "pastFailureCauses", shuffled);

    assertThat(FirstHypothesis.from(answer))
        .extracting(FirstHypothesis.Hypothesis::answer)
        .containsExactly("weekend", "phone");
  }

  @Test
  @DisplayName("모든 답에 규칙이 있고, 문장은 비어 있지 않으며 같은 문장이 없다 (값을 새로 추가하면 여기서 걸린다)")
  void everyAnswerHasARule() {
    List<String> sentences = new ArrayList<>();
    for (FailureCause cause : FailureCause.values()) {
      sentences.add(FirstHypothesis.fromFailureCause(cause).sentence());
    }
    for (Chronotype chronotype : Chronotype.values()) {
      sentences.add(FirstHypothesis.fromChronotype(chronotype).sentence());
    }
    for (Distraction distraction : Distraction.values()) {
      sentences.add(FirstHypothesis.fromDistraction(distraction).sentence());
    }

    assertThat(sentences).allMatch(sentence -> !sentence.isBlank());
    assertThat(sentences).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("#37 ~ #40 분석이 모두 어떤 가설과든 이어져 있다")
  void everyAnalysisIsUsed() {
    EnumSet<HypothesisAnalysis> used = EnumSet.noneOf(HypothesisAnalysis.class);
    for (FailureCause cause : FailureCause.values()) {
      used.add(FirstHypothesis.fromFailureCause(cause).analysis());
    }
    for (Distraction distraction : Distraction.values()) {
      used.add(FirstHypothesis.fromDistraction(distraction).analysis());
    }
    used.add(FirstHypothesis.fromChronotype(Chronotype.MORNING).analysis());

    assertThat(used).containsExactlyInAnyOrder(HypothesisAnalysis.values());
  }
}
