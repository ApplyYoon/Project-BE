package com.ithopeschool.projectbe.domain.thinkingtest;

import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

// 생각 습관 테스트의 점수로 "지금 내 공부 스타일"(4개 축)과 "많이 해당하는 습관" 을 냅니다. (이슈 #35)
// FE 의 scoreThinkingTest · getTopHabits (src/lib/thinkingTest.ts) 와 같은 규칙입니다. 한쪽을 고치면 다른 쪽도 고쳐 주세요.
//
// ■ 4개 축 (습관 2개씩)
//   계획할 때     계획 오류 + 미래의 나 믿기      높으면 낙관 · 낮으면 현실
//   넘어졌을 때   될 대로 되라 + 다 아니면 0      높으면 멈춤 · 낮으면 회복
//   움직이는 힘   기분 따라 판단하기 + 보상 심리   높으면 기분 · 낮으면 루틴
//   원인을 볼 때  상황 탓하기 + 한 번으로 단정하기 높으면 고정 · 낮으면 성장
//   두 습관 평균의 평균이 4.5 이상이면 높은 쪽, 3.5 이하면 낮은 쪽, 그 사이는 "균형" 입니다. (7점 척도의 가운데는 4)
//
// ■ 많이 해당하는 습관: 평균이 5 보다 높은 것 (5 = 약간 그렇다). 높은 것부터.
//   "이상" 이 아니라 "초과" 인 이유: 모든 문장에 7 만 누르면 역문항 때문에 모든 습관이 딱 5.0 이 되기 때문입니다.
//
// 예시
//   계획 오류 6.0 · 미래의 나 5.0  →  계획할 때 5.5 → "낙관"
//   될 대로 되라 4.0 · 다 아니면 0 4.6  →  넘어졌을 때 4.3 → "균형"
public final class StudyStyle {

  public static final double HIGH_AT = 4.5;
  public static final double LOW_AT = 3.5;
  public static final double TOP_HABIT_ABOVE = 5.0;

  // 축 하나. name: 축 이름 / first·second: 묶는 습관 / highLabel·lowLabel: 높을 때·낮을 때 이름
  public record Axis(
      String name, CognitiveBias first, CognitiveBias second, String highLabel, String lowLabel) {}

  public static final List<Axis> AXES =
      List.of(
          new Axis("계획할 때", CognitiveBias.PLANNING_FALLACY, CognitiveBias.FUTURE_SELF, "낙관", "현실"),
          new Axis("넘어졌을 때", CognitiveBias.WHAT_THE_HELL, CognitiveBias.ALL_OR_NOTHING, "멈춤", "회복"),
          new Axis(
              "움직이는 힘",
              CognitiveBias.EMOTIONAL_REASONING,
              CognitiveBias.MORAL_LICENSING,
              "기분",
              "루틴"),
          new Axis(
              "원인을 볼 때", CognitiveBias.EXTERNAL_BLAME, CognitiveBias.OVERGENERALIZING, "고정", "성장"));

  // 축 하나의 결과. average: 두 습관 평균의 평균 (1 ~ 7) / label: 높은 쪽 · 낮은 쪽 · "균형" 중 하나
  public record AxisResult(Axis axis, double average, String label) {}

  private StudyStyle() {}

  // 4개 축의 결과. 점수가 없는 습관은 가운데(4)로 봅니다.
  public static List<AxisResult> stylesOf(Map<CognitiveBias, Double> scores) {
    List<AxisResult> results = new ArrayList<>();
    for (Axis axis : AXES) {
      double average = (scoreOf(scores, axis.first()) + scoreOf(scores, axis.second())) / 2;
      String label = "균형";
      if (average >= HIGH_AT) {
        label = axis.highLabel();
      } else if (average <= LOW_AT) {
        label = axis.lowLabel();
      }
      results.add(new AxisResult(axis, average, label));
    }
    return results;
  }

  // "지금 내 공부 스타일" 한 줄.   →  "낙관 · 회복 · 기분 · 성장"
  public static String styleNameOf(Map<CognitiveBias, Double> scores) {
    List<String> labels = new ArrayList<>();
    for (AxisResult result : stylesOf(scores)) {
      labels.add(result.label());
    }
    return String.join(" · ", labels);
  }

  // 많이 해당하는 습관들 (평균이 5 보다 높은 것, 높은 것부터, 같으면 목록 순서). 없으면 빈 목록
  public static List<CognitiveBias> topHabitsOf(Map<CognitiveBias, Double> scores) {
    List<CognitiveBias> top = new ArrayList<>();
    for (CognitiveBias bias : CognitiveBias.values()) {
      if (scoreOf(scores, bias) > TOP_HABIT_ABOVE) {
        top.add(bias);
      }
    }
    // sort 는 같은 값이면 원래 순서(목록 순서)를 지킵니다.
    top.sort(Comparator.comparingDouble((CognitiveBias bias) -> scoreOf(scores, bias)).reversed());
    return top;
  }

  // 습관 하나의 평균. 없으면 가운데(4)입니다.
  private static double scoreOf(Map<CognitiveBias, Double> scores, CognitiveBias bias) {
    Double score = scores.get(bias);
    return score == null ? 4.0 : score;
  }
}
