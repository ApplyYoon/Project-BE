package com.ithopeschool.projectbe.domain.ai.dto;

import java.util.List;

// "카드 폼 기본값 추천" 의 결과입니다. 아무것도 저장하지 않습니다. (이슈 #237, FE #419)
//   { "showHelp": false, "goalId": "4", "type": "lecture", "durationMinutes": 50,
//     "evidence": ["지난번 같은 제목의 카드(2026-10-15)와 같은 목표 · 유형 · 길이로 채웠어요"] }
// - goalId: 내 진행 중 목표 번호(글자). 못 골랐으면 null → FE 는 "목표 없음" 또는 자기 기본값
// - type: "self"(자습) / "lecture"(인강). 못 골랐으면 null → FE 기본값(self)
// - durationMinutes: 길이(분). 같은 제목의 지난 카드가 있으면 그 길이, 없으면 내 기록의 한 칸 길이라 늘 있습니다.
// - evidence: 왜 그렇게 채웠는지 학생에게 보여 줄 문장들
// - showHelp 가 true 면 제목에서 위기 표현이 감지된 것입니다. 나머지는 비어 있고, FE 는 도움 연결 안내를 먼저 보여 줍니다.
public record CardDefaultsResponse(
    boolean showHelp, String goalId, String type, Integer durationMinutes, List<String> evidence) {

  // 위기 표현이 감지됐을 때의 응답입니다.
  public static CardDefaultsResponse help() {
    return new CardDefaultsResponse(true, null, null, null, List.of());
  }
}
