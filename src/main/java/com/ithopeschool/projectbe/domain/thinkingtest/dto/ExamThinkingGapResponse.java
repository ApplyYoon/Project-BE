package com.ithopeschool.projectbe.domain.thinkingtest.dto;

import com.ithopeschool.projectbe.domain.thinkingtest.ExamThinkingGap;
import java.time.LocalDate;
import java.util.List;

// 시험 전 2주의 "내 생각 vs 현실" 결과 모양입니다. (ExamThinkingGap)
//   { "examDate": "2026-10-20", "fromDate": "2026-10-06", "toDate": "2026-10-19",
//     "reasonDays": 4, "enoughData": true,
//     "reasons": [ { "reason": "TOO_MUCH", "label": "계획 과다", "thoughtBy": [], "actualCount": 3,
// "verdict": "BIGGER" } ],
//     "plan": { "style": "낙관", "planRate": 58, "planCount": 9, "verdict": "MATCHED" },
//     "unverifiable": [ { "key": "FOCUS", "label": "집중력" } ],
//     "sentences": [ "꼽지 않았던 '계획 과다' 쪽이 시험 전 2주 중 3일, …", … ] }
// - reasons 는 /thinking-tests/gap 과 같은 모양입니다. 다만 actualCount 는 "횟수" 가 아니라 "그 이유가 나온 날 수" 입니다.
// - plan.planRate · plan.verdict 는 계획이 모자라 견주지 않았으면 null 입니다.
public record ExamThinkingGapResponse(
    LocalDate examDate,
    LocalDate fromDate,
    LocalDate toDate,
    int reasonDays,
    boolean enoughData,
    List<ThinkingGapResponse.Reason> reasons,
    Plan plan,
    List<ThinkingTestResponse.Chip> unverifiable,
    List<String> sentences) {

  public record Plan(String style, Integer planRate, int planCount, String verdict) {}

  public static ExamThinkingGapResponse from(ExamThinkingGap gap) {
    ExamThinkingGap.PlanGap plan = gap.plan();
    return new ExamThinkingGapResponse(
        gap.examDate(),
        gap.fromDate(),
        gap.toDate(),
        gap.reasonDays(),
        gap.enoughData(),
        ThinkingGapResponse.reasonsOf(gap.reasons()),
        new Plan(
            plan.style(),
            plan.planRate(),
            plan.planCount(),
            plan.verdict() == null ? null : plan.verdict().name()),
        ThinkingGapResponse.chipsOf(gap.unverifiable()),
        gap.sentences());
  }
}
