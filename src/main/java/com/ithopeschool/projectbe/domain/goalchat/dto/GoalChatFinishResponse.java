package com.ithopeschool.projectbe.domain.goalchat.dto;

import com.ithopeschool.projectbe.domain.goalchat.GoalDraft;
import com.ithopeschool.projectbe.domain.safety.HelpLine;
import java.util.List;

// '목표 만들기' 대화를 끝냈을 때의 응답입니다. 아무것도 저장하지 않습니다. (이슈 #239)
//   { "goalDraft": { "kind": "score", "title": "11월 학평 수학 3등급", "dDay": "2026-11-19",
//                    "currentScore": "5등급", "totalAmount": null, "amountUnit": null,
//                    "benefit": "…", "cost": "…", "obstacle": "…", "plan": "…" },
//     "showHelp": false, "helpLines": [] }
//
// - goalDraft 칸은 목표 저장(POST /api/v1/goals)의 요청과 같은 이름입니다. FE 는 저장 화면에 채워 학생에게 확인받습니다.
//   대화에서 정하지 못했거나 규칙에 안 맞는 칸은 null 입니다. (GoalDraft)
// - goalDraft 가 null 이면 초안을 만들지 못한 것입니다. (AI 꺼짐 · 실패 · 남은 칸 없음)
//   에러가 아니라 200 이고, FE 는 빈 저장 화면(직접 쓰기)으로 보냅니다.
// - showHelp 가 true 면 학생의 말에서 위기 표현이 감지된 것입니다. goalDraft 는 null 이고 helpLines 에 도움받을 곳이 있습니다.
public record GoalChatFinishResponse(
    GoalDraft goalDraft, boolean showHelp, List<HelpLine> helpLines) {

  // 초안 (만들지 못했으면 null)
  public static GoalChatFinishResponse of(GoalDraft goalDraft) {
    return new GoalChatFinishResponse(goalDraft, false, List.of());
  }

  // 위기 표현이 감지됐을 때
  public static GoalChatFinishResponse help() {
    return new GoalChatFinishResponse(null, true, HelpLine.ALL);
  }
}
