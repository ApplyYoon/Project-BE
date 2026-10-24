package com.ithopeschool.projectbe.domain.ai.dto;

import java.util.List;

// 즉석 카드의 결과입니다. 아무것도 저장하지 않습니다. (이슈 #237 표 6번)
//   { "showHelp": false, "card": { "title": "수학 문제집", "items": ["42쪽 펴기", "5문제 풀기"] } }
// - title: 카드 제목 (30자까지). 평소 쓰던 카드 제목과 같은 일이면 그 제목 그대로입니다.
// - items: 체크리스트 2 ~ 4개
// - card 가 null 이면 AI 가 만들지 못한 것입니다. (AI 꺼짐 · 실패 · 쓸 수 없는 답)
//   에러가 아니라 200 이고, FE 는 기본값(한 줄을 제목으로, 체크리스트는 직접)을 씁니다.
// - showHelp 가 true 면 글에서 위기 표현이 감지된 것입니다. card 는 null 이고, FE 는 도움 연결 안내를 먼저 보여 줍니다.
public record QuickCardResponse(boolean showHelp, Card card) {

  // 카드 초안
  public record Card(String title, List<String> items) {}

  // 만든 카드 초안 (못 만들었으면 null)
  public static QuickCardResponse of(Card card) {
    return new QuickCardResponse(false, card);
  }

  // 위기 표현이 감지됐을 때의 응답입니다. AI 를 부르지 않습니다.
  public static QuickCardResponse help() {
    return new QuickCardResponse(true, null);
  }
}
