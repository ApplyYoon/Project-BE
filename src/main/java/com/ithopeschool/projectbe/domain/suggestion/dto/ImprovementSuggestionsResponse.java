package com.ithopeschool.projectbe.domain.suggestion.dto;

import java.util.List;

// 개선안 3개 제안의 결과입니다. 아무것도 저장하지 않습니다. (이슈 #51)
//   { "cause": "phone", "basis": "recent",
//     "suggestions": [
//       { "key": "phone-easy-1", "level": "easy",
//         "ifText": "화요일에 공부를 시작하면", "thenText": "알림을 모두 끈다", "polished": true,
//         "original": { "ifText": "공부를 시작하면", "thenText": "알림을 모두 끈다" } }, … ] }
// - cause: 이번 개선안이 겨냥한 실패 원인 (체크인의 missReason 과 같은 글자). 고를 것이 없으면 null
// - basis: 원인을 무엇으로 골랐는지. "recent"(최근 30일 기록) 또는 "onboarding"(처음 설문의 과거 실패 경험). 없으면 null
// - suggestions: 쉬움 · 보통 · 강함 순서로 3개. 고를 원인이 없으면 빈 목록 []
//   - key: 전략 이름. 고른 개선안(#52)·효과 판정(#54)이 이 이름으로 전략을 가리킵니다
//   - ifText · thenText: 보여 줄 문장. AI 가 다듬었으면 다듬은 문장, 아니면 원래 문장
//   - polished: AI 가 다듬은 문장이면 true. AI 가 꺼졌거나 검사에 걸리면 false (원래 문장)
//   - original: 카탈로그의 원래 문장 (다듬은 문장과 뜻이 같은지 사람이 견줘 볼 수 있게)
public record ImprovementSuggestionsResponse(
    String cause, String basis, List<Suggestion> suggestions) {

  // 개선안 하나
  public record Suggestion(
      String key,
      String level,
      String ifText,
      String thenText,
      boolean polished,
      Original original) {}

  // 카탈로그의 원래 문장
  public record Original(String ifText, String thenText) {}

  // 고를 원인이 없을 때 (기록도 설문 답도 없음)
  public static ImprovementSuggestionsResponse none() {
    return new ImprovementSuggestionsResponse(null, null, List.of());
  }
}
