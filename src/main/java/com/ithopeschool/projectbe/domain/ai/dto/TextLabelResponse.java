package com.ithopeschool.projectbe.domain.ai.dto;

// 글에 이름표 붙이기의 결과입니다. 아무것도 저장하지 않습니다. (이슈 #237 표 7번)
//   { "showHelp": false, "label": "outer" }
// - label: reason 이면 inner · outer, memo 이면 contact · otherTask · worry · life 중 하나
//   ⚠️ 참고 칸입니다. 판정·집계에 쓰지 않습니다.
// - label 이 null 이면 AI 가 붙이지 못한 것입니다. (AI 꺼짐 · 하루 한도 · 실패) FE 는 이름표 없이 저장합니다.
// - showHelp 가 true 면 글에서 위기 표현이 감지된 것입니다. label 은 null 이고, FE 는 도움 연결 안내를 먼저 보여 줍니다.
public record TextLabelResponse(boolean showHelp, String label) {

  // 붙인 이름표 (못 붙였으면 null)
  public static TextLabelResponse of(String label) {
    return new TextLabelResponse(false, label);
  }

  // 위기 표현이 감지됐을 때의 응답입니다. AI 를 부르지 않습니다.
  public static TextLabelResponse help() {
    return new TextLabelResponse(true, null);
  }
}
