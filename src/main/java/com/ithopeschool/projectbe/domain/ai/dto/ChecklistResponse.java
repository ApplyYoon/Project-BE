package com.ithopeschool.projectbe.domain.ai.dto;

import java.util.List;

// 체크리스트 추천의 결과입니다. 아무것도 저장하지 않습니다. (이슈 #236)
//   { "showHelp": false, "items": ["42~45쪽 문제 풀기", "틀린 문제 표시하기", "채점하기"] }
// - items 가 null 이면 AI 가 만들지 못한 것입니다. (AI 꺼짐 · 하루 한도 · 실패 · 쓸 만한 항목이 모자람)
//   에러가 아니라 200 이고, FE 는 기본값(직접 쓰기)을 씁니다.
// - showHelp 가 true 면 글에서 위기 표현이 감지된 것입니다. items 는 null 이고, FE 는 도움 연결 안내를 먼저 보여 줍니다.
public record ChecklistResponse(boolean showHelp, List<String> items) {

  // AI 가 만든 항목들 (만들지 못했으면 null)
  public static ChecklistResponse of(List<String> items) {
    return new ChecklistResponse(false, items);
  }

  // 위기 표현이 감지됐을 때의 응답입니다. AI 를 부르지 않습니다.
  public static ChecklistResponse help() {
    return new ChecklistResponse(true, null);
  }
}
