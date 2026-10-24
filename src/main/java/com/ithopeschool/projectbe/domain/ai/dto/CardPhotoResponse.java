package com.ithopeschool.projectbe.domain.ai.dto;

import com.ithopeschool.projectbe.domain.ai.CardParse;
import java.util.List;

// "시험 범위 사진으로 카드 만들기" 의 결과입니다. 아무것도 저장하지 않습니다. (사진도 저장하지 않음)
// "말로 카드 만들기"(CardParseResponse)와 같은 칸에 scopeText 하나를 더했습니다.
//   { "showHelp": false,
//     "drafts": [ { "date": "2026-10-18", "startTime": null, "durationMinutes": 25,
//                   "durationFromRecord": true, "title": "수학 3단원 개념",
//                   "checklist": ["교과서 52~60쪽 읽기"], "goalId": "4" } ],
//     "durationEvidence": "최근 14일 공부 기록 8번의 ...",
//     "scopeText": "수학 3~4단원, 교과서 52~88쪽" }
// - drafts 가 null 이면 만들지 못한 것입니다. (AI 꺼짐 · 실패 · 시험 범위가 아닌 사진) 에러가 아니라 200 입니다.
// - startTime 은 늘 null 입니다. 사진에는 시각이 없어서 FE 가 물어봅니다.
// - scopeText 는 AI 가 사진에서 읽은 범위 한 줄입니다. 못 읽었으면 null.
// - showHelp 가 true 면 메모나 사진 속 글에서 위기 표현이 감지된 것입니다. FE 는 도움 연결 안내를 먼저 보여 줍니다.
public record CardPhotoResponse(
    boolean showHelp,
    List<CardParseResponse.Draft> drafts,
    String durationEvidence,
    String scopeText) {

  // 만든 초안들. 길이를 기록으로 채운 카드가 없으면 durationEvidence 는 null 입니다. (CardParseResponse.of 와 같음)
  public static CardPhotoResponse of(
      List<CardParse.Draft> drafts, String durationEvidence, String scopeText) {
    CardParseResponse cards = CardParseResponse.of(drafts, durationEvidence);
    return new CardPhotoResponse(false, cards.drafts(), cards.durationEvidence(), scopeText);
  }

  // 만들지 못했을 때 (AI 꺼짐 · 실패 · 시험 범위가 아닌 사진)
  public static CardPhotoResponse notMade() {
    return new CardPhotoResponse(false, null, null, null);
  }

  // 위기 표현이 감지됐을 때의 응답입니다.
  public static CardPhotoResponse help() {
    return new CardPhotoResponse(true, null, null, null);
  }
}
