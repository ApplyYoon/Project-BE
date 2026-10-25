package com.ithopeschool.projectbe.domain.ai.dto;

import com.ithopeschool.projectbe.domain.ai.CardParse;
import java.time.LocalDate;
import java.util.List;

// "말로 카드 만들기" 의 결과입니다. 아무것도 저장하지 않습니다. (이슈 #237, FE #419)
//   { "showHelp": false,
//     "drafts": [ { "date": "2026-10-18", "startTime": "19:00", "durationMinutes": 25,
//                   "durationFromRecord": true, "title": "수학 문제집",
//                   "checklist": ["문제집 2장 풀기"], "goalId": "4" } ],
//     "durationEvidence": "최근 14일 공부 기록 8번의 한 번 길이 가운데값이 24분이라 25분으로 잡았어요" }
// - startTime 이 null 이면 문장에 시각이 없었던 것입니다. 카드는 시작 시각이 있어야 하니 FE 가 물어봅니다.
// - durationFromRecord 가 true 면 문장에 길이가 없어서 내 기록의 한 칸 길이를 넣은 것이고, 그 이유가 durationEvidence 입니다.
// - goalId 는 내 진행 중 목표 번호(글자)입니다. 못 골랐으면 null.
// - drafts 가 null 이면 AI 가 만들지 못한 것입니다. (AI 꺼짐 · 실패) 에러가 아니라 200 이고,
//   FE 는 카드를 직접 만들게 합니다. 빈 배열([])이면 AI 는 답했지만 쓸 수 있는 카드가 없었던 것입니다.
// - showHelp 가 true 면 문장에서 위기 표현이 감지된 것입니다. drafts 는 null 이고, FE 는 도움 연결 안내를 먼저 보여 줍니다.
public record CardParseResponse(boolean showHelp, List<Draft> drafts, String durationEvidence) {

  // 카드 초안 한 장 (FE 의 Card 에서 저장 때 정하는 칸들을 뺀 것)
  public record Draft(
      LocalDate date,
      String startTime,
      int durationMinutes,
      boolean durationFromRecord,
      String title,
      List<String> checklist,
      String goalId) {

    static Draft from(CardParse.Draft draft) {
      return new Draft(
          draft.date(),
          draft.startTime(),
          draft.durationMinutes(),
          draft.durationFromRecord(),
          draft.title(),
          draft.checklist(),
          draft.goalId());
    }
  }

  // 만든 초안들. 길이를 기록으로 채운 카드가 없으면 durationEvidence 는 null 입니다.
  public static CardParseResponse of(List<CardParse.Draft> drafts, String durationEvidence) {
    boolean anyFromRecord = drafts.stream().anyMatch(CardParse.Draft::durationFromRecord);
    return new CardParseResponse(
        false, drafts.stream().map(Draft::from).toList(), anyFromRecord ? durationEvidence : null);
  }

  // AI 가 만들지 못했을 때 (꺼짐 · 실패)
  public static CardParseResponse notMade() {
    return new CardParseResponse(false, null, null);
  }

  // 위기 표현이 감지됐을 때의 응답입니다.
  public static CardParseResponse help() {
    return new CardParseResponse(true, null, null);
  }
}
