package com.ithopeschool.projectbe.domain.thinkingtest.dto;

import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.bias.Weakness;
import com.ithopeschool.projectbe.domain.thinkingtest.StudyStyle;
import com.ithopeschool.projectbe.domain.thinkingtest.ThinkingTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// 생각 습관 테스트 결과 하나를 FE 에 알려 주는 모양입니다.
//   { "id": "3", "takenAt": "2026-10-15T09:00:00Z", "testVersion": 2,
//     "styleName": "낙관 · 회복 · 기분 · 성장",
//     "styles": [ { "name": "계획할 때", "label": "낙관", "average": 5.5, "highLabel": "낙관", "lowLabel":
// "현실" }, … ],
//     "scores": [ { "habit": "PLANNING_FALLACY", "label": "계획 오류", "average": 6.0 }, … ],
//     "topHabits": [ "PLANNING_FALLACY" ],
//     "weaknesses": [ { "key": "GAME", "label": "게임" } ] }
// - scores 는 생각 습관 목록 순서입니다. 누구의 결과인지(userId)는 담지 않습니다.
public record ThinkingTestResponse(
    String id,
    Instant takenAt,
    int testVersion,
    String styleName,
    List<Style> styles,
    List<Score> scores,
    List<String> topHabits,
    List<Chip> weaknesses) {

  public record Style(
      String name, String label, double average, String highLabel, String lowLabel) {}

  public record Score(String habit, String label, double average) {}

  public record Chip(String key, String label) {}

  public static ThinkingTestResponse from(ThinkingTest test) {
    List<Style> styles = new ArrayList<>();
    for (StudyStyle.AxisResult result : StudyStyle.stylesOf(test.getScores())) {
      styles.add(
          new Style(
              result.axis().name(),
              result.label(),
              roundOne(result.average()),
              result.axis().highLabel(),
              result.axis().lowLabel()));
    }
    List<Score> scores = new ArrayList<>();
    for (CognitiveBias bias : CognitiveBias.values()) {
      Double average = test.getScores().get(bias);
      if (average != null) {
        scores.add(new Score(bias.name(), bias.getLabel(), roundOne(average)));
      }
    }
    List<String> topHabits = new ArrayList<>();
    for (CognitiveBias bias : StudyStyle.topHabitsOf(test.getScores())) {
      topHabits.add(bias.name());
    }
    List<Chip> weaknesses = new ArrayList<>();
    for (Weakness weakness : Weakness.values()) {
      if (test.getWeaknesses().contains(weakness)) {
        weaknesses.add(new Chip(weakness.name(), weakness.getLabel()));
      }
    }
    return new ThinkingTestResponse(
        String.valueOf(test.getId()),
        test.getTakenAt(),
        test.getTestVersion(),
        StudyStyle.styleNameOf(test.getScores()),
        styles,
        scores,
        topHabits,
        weaknesses);
  }

  // 소수 첫째 자리까지만 남깁니다. (5.333… → 5.3)
  private static double roundOne(double value) {
    return Math.round(value * 10) / 10.0;
  }
}
