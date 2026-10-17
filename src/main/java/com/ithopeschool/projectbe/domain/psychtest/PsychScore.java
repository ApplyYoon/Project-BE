package com.ithopeschool.projectbe.domain.psychtest;

import java.util.EnumMap;
import java.util.Map;

// 심리검사 답으로 척도 7개의 점수와 높낮이를 냅니다. (이슈 #219)
// FE 의 scorePsychTest · getScaleLevel (src/lib/psychTest.ts) 과 같은 규칙입니다. 한쪽을 고치면 다른 쪽도 고쳐 주세요.
// 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
//
// ■ 규칙
//   척도 점수 = 그 척도에 답한 문장들의 평균 (1 ~ 7). 문장이 5개(지금 검사)든 2개(옛 14문항 검사)든 같은 규칙입니다.
//   1 ~ 7 이 아닌 답은 셈에서 빼고, 답이 하나도 없으면 가운데(4)입니다.
//   높낮이: 5 이상 high(높음) · 3 이하 low(낮음) · 그 사이 mid(보통)
//
// ■ 예시 (FE 주석과 같음)
//   S1 = 6, S2 = 5 → 시작 저항 5.5 → high
//   A1 = 3, A2 = 2 → 주의 분산 2.5 → low
//   P1 = 4, P2 = 5 → 계획 낙관 4.5 → mid
//   C1 = 3, C2 = 3 → 관리 선호 3.0 → low  (3 "이하" 라서 3 도 낮음)
//   F1 = 5, F2 = 5 → 실패 민감도 5.0 → high (5 "이상" 이라서 5 도 높음)
public final class PsychScore {

  // 이 점수 이상이면 높음, 이하면 낮음 (기획서의 출발값)
  public static final double HIGH_AT = 5;
  public static final double LOW_AT = 3;
  // 한 척도에 답이 하나도 없을 때 쓰는 점수. 7점 척도의 가운데입니다.
  static final double MIDDLE = 4;

  // 높낮이
  public enum Level {
    HIGH("high", "높음"),
    MID("mid", "보통"),
    LOW("low", "낮음");

    private final String value;
    private final String label;

    Level(String value, String label) {
      this.value = value;
      this.label = label;
    }

    public String toValue() {
      return value;
    }

    public String label() {
      return label;
    }
  }

  private PsychScore() {}

  // 척도 7개의 점수 (척도 순서대로)
  public static Map<PsychScale, Double> scoresOf(Map<PsychItem, Integer> answers) {
    Map<PsychScale, Double> scores = new EnumMap<>(PsychScale.class);
    for (PsychScale scale : PsychScale.values()) {
      int sum = 0;
      int count = 0;
      for (PsychItem item : PsychItem.values()) {
        Integer answer = answers.get(item);
        if (item.scale() == scale && answer != null && answer >= 1 && answer <= 7) {
          sum += answer;
          count++;
        }
      }
      scores.put(scale, count > 0 ? (double) sum / count : MIDDLE);
    }
    return scores;
  }

  // 점수 하나의 높낮이
  public static Level levelOf(double score) {
    if (score >= HIGH_AT) {
      return Level.HIGH;
    }
    if (score <= LOW_AT) {
      return Level.LOW;
    }
    return Level.MID;
  }
}
