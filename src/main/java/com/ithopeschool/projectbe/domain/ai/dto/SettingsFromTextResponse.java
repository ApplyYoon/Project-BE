package com.ithopeschool.projectbe.domain.ai.dto;

// 말로 설정 바꾸기의 결과입니다. **변경안만** 돌려주고 설정은 바꾸지 않습니다. (이슈 #237 표 8번)
//   { "showHelp": false,
//     "proposal": { "callTime": null, "dailyCount": null, "quietStart": null, "quietEnd": "09:00",
//                   "summary": "아침 9시까지 연락하지 않도록 연락 금지 끝 시각을 09:00 으로 바꿔요" } }
// - proposal 의 네 칸은 관리 설정 바꾸기(PATCH /api/v1/settings)의 칸과 같은 이름 · 같은 모양입니다.
//   바뀌는 칸만 값이 있고, 나머지는 null 입니다. 학생이 확인하면 FE 가 값이 있는 칸만 그대로 PATCH 로 보냅니다.
// - proposal 이 null 이면 변경안을 못 만든 것입니다. (AI 꺼짐 · 하루 한도 · 실패 · 바꿀 칸이 없음 · 말로 못 바꾸는 칸을 부탁함)
//   에러가 아니라 200 이고, FE 는 설정 화면으로 보냅니다.
// - showHelp 가 true 면 글에서 위기 표현이 감지된 것입니다. proposal 은 null 이고, FE 는 도움 연결 안내를 먼저 보여 줍니다.
public record SettingsFromTextResponse(boolean showHelp, Proposal proposal) {

  // 설정 변경안
  public record Proposal(
      String callTime, Integer dailyCount, String quietStart, String quietEnd, String summary) {}

  // 만든 변경안 (못 만들었으면 null)
  public static SettingsFromTextResponse of(Proposal proposal) {
    return new SettingsFromTextResponse(false, proposal);
  }

  // 위기 표현이 감지됐을 때의 응답입니다. AI 를 부르지 않습니다.
  public static SettingsFromTextResponse help() {
    return new SettingsFromTextResponse(true, null);
  }
}
