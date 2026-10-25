package com.ithopeschool.projectbe.domain.ai.dto;

import com.ithopeschool.projectbe.domain.ai.DailyMissions;
import java.util.List;

// "AI 가 짜는 오늘의 미션" 의 결과입니다. 아무것도 저장하지 않습니다. (2026-10-18)
//   { "showHelp": false,
//     "missions": [ { "title": "수능 기출 미적분 3문제", "minutes": 15, "subject": "미적분Ⅰ" } ] }
// - missions 가 null 이면 AI 가 만들지 못한 것입니다. (AI 꺼짐 · 실패 · 쓸 만한 답이 아님)
//   에러가 아니라 200 이고, FE 는 규칙으로 만든 미션을 씁니다.
// - subject 는 보낸 오늘 과목 중 하나이거나 null 입니다.
// - showHelp 가 true 면 준비하는 것의 이름에서 위기 표현이 감지된 것입니다. missions 는 null 입니다.
public record DailyMissionsResponse(boolean showHelp, List<DailyMissions.Mission> missions) {

  // AI 가 만든 미션 (만들지 못했으면 null)
  public static DailyMissionsResponse of(List<DailyMissions.Mission> missions) {
    return new DailyMissionsResponse(false, missions);
  }

  // 위기 표현이 감지됐을 때의 응답입니다. AI 를 부르지 않습니다.
  public static DailyMissionsResponse help() {
    return new DailyMissionsResponse(true, null);
  }
}
