package com.ithopeschool.projectbe.domain.ai.dto;

// 첫 준비 행동의 결과입니다. 아무것도 저장하지 않습니다. (이슈 #236)
//   { "showHelp": false, "step": "수학 문제집 42쪽 펴기" }
// - step 이 null 이면 AI 가 만들지 못한 것입니다. (AI 꺼짐 · 실패 · 쓸 만한 답이 아님)
//   에러가 아니라 200 이고, FE 는 기본값(첫 항목 그대로)을 보여 줍니다.
// - showHelp 가 true 면 글에서 위기 표현이 감지된 것입니다. step 은 null 이고, FE 는 도움 연결 안내를 먼저 보여 줍니다.
public record FirstStepResponse(boolean showHelp, String step) {

  // AI 가 만든 준비 행동 (만들지 못했으면 null)
  public static FirstStepResponse of(String step) {
    return new FirstStepResponse(false, step);
  }

  // 위기 표현이 감지됐을 때의 응답입니다. AI 를 부르지 않습니다.
  public static FirstStepResponse help() {
    return new FirstStepResponse(true, null);
  }
}
