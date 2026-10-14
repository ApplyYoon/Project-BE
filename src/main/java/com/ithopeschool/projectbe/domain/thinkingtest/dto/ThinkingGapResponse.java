package com.ithopeschool.projectbe.domain.thinkingtest.dto;

import com.ithopeschool.projectbe.domain.bias.Weakness;
import com.ithopeschool.projectbe.domain.call.CallSummary;
import com.ithopeschool.projectbe.domain.thinkingtest.ThinkingGap;
import java.util.ArrayList;
import java.util.List;

// "내 생각 vs 현실" 분석 결과의 모양입니다. (ThinkingGap)
//   { "days": 14, "callCount": 5, "enoughData": true,
//     "reasons": [ { "reason": "TOO_MUCH", "label": "계획 과다", "thoughtBy": [], "actualCount": 3,
// "verdict": "BIGGER" } ],
//     "habits": [ { "habit": "PLANNING_FALLACY", "label": "계획 오류", "thought": true, "foundCount":
// 2, "verdict": "MATCHED" } ],
//     "unverifiable": [ { "key": "FOCUS", "label": "집중력" } ],
//     "sentences": [ "꼽지 않았던 '계획 과다' 쪽이 실제로는 못 채운 이유로 3번 나왔어. 생각보다 큰 원인일 수 있어.", … ] }
// - verdict: MATCHED(생각이 맞았어) · SMALLER(생각보다 작았어) · BIGGER(생각보다 컸어)
public record ThinkingGapResponse(
    int days,
    int callCount,
    boolean enoughData,
    List<Reason> reasons,
    List<Habit> habits,
    List<ThinkingTestResponse.Chip> unverifiable,
    List<String> sentences) {

  public record Reason(
      String reason,
      String label,
      List<ThinkingTestResponse.Chip> thoughtBy,
      int actualCount,
      String verdict) {}

  public record Habit(
      String habit, String label, boolean thought, int foundCount, String verdict) {}

  public static ThinkingGapResponse from(ThinkingGap gap) {
    List<Reason> reasons = new ArrayList<>();
    for (ThinkingGap.ReasonGap reason : gap.reasons()) {
      reasons.add(
          new Reason(
              reason.reason().name(),
              CallSummary.missReasonLabel(reason.reason()),
              chipsOf(reason.thoughtBy()),
              reason.actualCount(),
              reason.verdict().name()));
    }
    List<Habit> habits = new ArrayList<>();
    for (ThinkingGap.HabitGap habit : gap.habits()) {
      habits.add(
          new Habit(
              habit.bias().name(),
              habit.bias().getLabel(),
              habit.thought(),
              habit.foundCount(),
              habit.verdict().name()));
    }
    return new ThinkingGapResponse(
        gap.days(),
        gap.callCount(),
        gap.enoughData(),
        reasons,
        habits,
        chipsOf(gap.unverifiable()),
        gap.sentences());
  }

  private static List<ThinkingTestResponse.Chip> chipsOf(List<Weakness> weaknesses) {
    List<ThinkingTestResponse.Chip> chips = new ArrayList<>();
    for (Weakness weakness : weaknesses) {
      chips.add(new ThinkingTestResponse.Chip(weakness.name(), weakness.getLabel()));
    }
    return chips;
  }
}
