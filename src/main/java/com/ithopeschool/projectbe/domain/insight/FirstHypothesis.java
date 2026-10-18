package com.ithopeschool.projectbe.domain.insight;

import com.ithopeschool.projectbe.domain.onboarding.Chronotype;
import com.ithopeschool.projectbe.domain.onboarding.Distraction;
import com.ithopeschool.projectbe.domain.onboarding.FailureCause;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswer;
import java.util.ArrayList;
import java.util.List;

// 온보딩 설문 답으로 "앞으로 2주 동안 확인할 가설" 을 만듭니다. (이슈 #35, 기획서 v2 3장 0주차)
// 기록이 하나도 없는 첫날에도 할 말이 있게 하려는 것입니다. 2주 뒤 이 가설을 데이터로 확인하는 것이 첫 '아하' 입니다.
//
// 정해 둔 규칙
//   1. 가설 문장은 AI 가 아니라 아래 코드의 규칙으로 만듭니다. 답 하나에 문장 하나입니다.
//   2. 재료는 "과거에 계획이 무너졌던 때"(여러 개) 와 자기 인식 세 가지(아침형/저녁형 · 한 번에 집중 가능한 시간 · 가장 큰 방해 요소)
// 입니다.
//      하루 공부 가능 시간은 자기 인식이지만 모두가 꼭 답하는 칸이라 쓰지 않습니다. (그 비교는 #40 "처음 계획 대비 지금" 이 합니다)
//   3. 순서: 무너졌던 때(FailureCause 순서) → 아침형/저녁형 → 집중 시간 → 방해 요소
//   4. 답이 하나도 없으면 가설을 억지로 만들지 않고 빈 목록입니다. 대신 DEFAULT_SENTENCE 를 보여줍니다.
//   5. 문장은 사용자를 깎아내리지 않습니다. "~라고 했지? 확인해볼게" 처럼 같이 알아보는 말투입니다. (CLAUDE.md 4장)
//
// 예시
//   무너졌던 때 [주말] + 저녁형  →  "주말에 무너지는 편이라고 했지? ..." (#37), "저녁형이라고 했지? ..." (#40)
//   설문을 안 했거나 필수 칸만 답함  →  [] (기본 문장)
public final class FirstHypothesis {

  // 가설이 하나도 없을 때 보여주는 기본 문장
  public static final String DEFAULT_SENTENCE = "2주 동안 기록으로 직접 알아볼게. 14일 뒤 첫 리포트에서 알려줄게!";

  // 가설 하나입니다.
  // answerType: 어느 설문 칸의 답인지 (onboarding 응답의 칸 이름 그대로. 예: "pastFailureCauses")
  // answer: 그 칸에 고른 답 (FE 와 주고받는 글자 그대로. 예: "weekend", "night", "50", "폰·SNS")
  // sentence: 사용자에게 보여줄 가설 문장
  // analysis: 2주 뒤에 이 가설을 확인할 분석 (#37 ~ #40)
  public record Hypothesis(
      String answerType, String answer, String sentence, HypothesisAnalysis analysis) {}

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private FirstHypothesis() {}

  // 설문 답으로 가설 목록을 만듭니다. 설문을 아직 안 했으면(answer 가 null) 빈 목록입니다.
  public static List<Hypothesis> from(OnboardingAnswer answer) {
    List<Hypothesis> hypotheses = new ArrayList<>();
    if (answer == null) {
      return hypotheses;
    }

    // DB 에서 읽은 pastFailureCauses 는 순서가 정해져 있지 않습니다. 그래서 FailureCause 에 적힌 순서대로 돌면서
    // 고른 것만 담습니다. (규칙 3)
    for (FailureCause cause : FailureCause.values()) {
      if (answer.getPastFailureCauses().contains(cause)) {
        hypotheses.add(fromFailureCause(cause));
      }
    }
    if (answer.getChronotype() != null) {
      hypotheses.add(fromChronotype(answer.getChronotype()));
    }
    if (answer.getMaxFocusMinutes() != null) {
      hypotheses.add(fromMaxFocusMinutes(answer.getMaxFocusMinutes()));
    }
    if (answer.getBiggestDistraction() != null) {
      hypotheses.add(fromDistraction(answer.getBiggestDistraction()));
    }
    return hypotheses;
  }

  // 과거에 계획이 무너졌던 때 → 가설
  static Hypothesis fromFailureCause(FailureCause cause) {
    String sentence;
    HypothesisAnalysis analysis;
    switch (cause) {
      case AFTER_EXAM -> {
        sentence = "시험이 끝나면 계획이 무너지는 편이라고 했지? 시험 다음 날 기록을 2주 동안 확인해볼게.";
        analysis = HypothesisAnalysis.RHYTHM;
      }
      case WEEKEND -> {
        sentence = "주말에 무너지는 편이라고 했지? 2주 동안 평일과 주말을 비교해서 확인해볼게.";
        analysis = HypothesisAnalysis.RHYTHM;
      }
      case LATE_ACADEMY -> {
        sentence = "학원이 늦게 끝난 날 무너지는 편이라고 했지? 그다음 날 기록까지 2주 동안 확인해볼게.";
        analysis = HypothesisAnalysis.RHYTHM;
      }
      case PHONE -> {
        sentence = "폰 때문에 계획이 무너지는 편이라고 했지? 계획을 못 지킨 날의 이유를 2주 동안 모아서 확인해볼게.";
        analysis = HypothesisAnalysis.FAILURE_CAUSE;
      }
      case TOO_MUCH_PLAN -> {
        sentence = "계획을 많이 세워서 무너지는 편이라고 했지? 계획한 시간과 실제 공부 시간을 2주 동안 비교해볼게.";
        analysis = HypothesisAnalysis.PERCEPTION_GAP;
      }
      // FailureCause 에 새 값이 생겼는데 여기를 안 고치면 바로 알 수 있게 에러를 냅니다.
      default -> throw new IllegalStateException("가설 규칙이 없는 무너진 때: " + cause);
    }
    return new Hypothesis("pastFailureCauses", cause.getValue(), sentence, analysis);
  }

  // 아침형 / 저녁형 → 가설. 자기 인식과 실제 시간대 효율(#37)을 견주는 것은 #40 입니다.
  static Hypothesis fromChronotype(Chronotype chronotype) {
    String sentence =
        chronotype == Chronotype.MORNING
            ? "아침형이라고 했지? 오전과 밤 중 언제 더 오래 집중하는지 2주 동안 확인해볼게."
            : "저녁형이라고 했지? 밤과 오전 중 언제 더 오래 집중하는지 2주 동안 확인해볼게.";
    return new Hypothesis(
        "chronotype", chronotype.getValue(), sentence, HypothesisAnalysis.PERCEPTION_GAP);
  }

  // 한 번에 집중 가능한 시간(분) → 가설
  static Hypothesis fromMaxFocusMinutes(int minutes) {
    String sentence = "한 번에 " + minutes + "분 정도 집중할 수 있다고 했지? 실제로 한 번 앉으면 몇 분 이어지는지 2주 동안 확인해볼게.";
    return new Hypothesis(
        "maxFocusMinutes", String.valueOf(minutes), sentence, HypothesisAnalysis.PERCEPTION_GAP);
  }

  // 가장 큰 방해 요소 → 가설
  // 졸림은 잠과 관련이 있어서 수면 조건을 보는 #38, 나머지는 못 지킨 날의 이유를 세는 #39 로 확인합니다.
  static Hypothesis fromDistraction(Distraction distraction) {
    String sentence;
    HypothesisAnalysis analysis = HypothesisAnalysis.FAILURE_CAUSE;
    String check = " 계획을 못 지킨 날의 이유와 같은지 2주 동안 확인해볼게.";
    switch (distraction) {
      case PHONE -> sentence = "폰·SNS 가 가장 큰 방해 요소라고 했지?" + check;
      case SLEEPY -> {
        sentence = "졸린 게 가장 큰 방해 요소라고 했지? 잠을 잔 시간과 공부가 잘된 날을 2주 동안 비교해볼게.";
        analysis = HypothesisAnalysis.CONDITION;
      }
      case WANDERING_MIND -> sentence = "잡생각이 가장 큰 방해 요소라고 했지?" + check;
      case PEOPLE -> sentence = "친구·가족이 가장 큰 방해 요소라고 했지?" + check;
      case NOISE -> sentence = "소음·주변 환경이 가장 큰 방해 요소라고 했지?" + check;
      case OTHER -> sentence = "따로 신경 쓰이는 방해 요소가 있다고 했지? 계획을 못 지킨 날의 이유를 2주 동안 모아서 확인해볼게.";
      // Distraction 에 새 값이 생겼는데 여기를 안 고치면 바로 알 수 있게 에러를 냅니다.
      default -> throw new IllegalStateException("가설 규칙이 없는 방해 요소: " + distraction);
    }
    return new Hypothesis("biggestDistraction", distraction.getValue(), sentence, analysis);
  }
}
