package com.ithopeschool.projectbe.domain.call.dto;

import com.ithopeschool.projectbe.domain.call.CheckCall;

// 확인 통화 문이 열렸는지 알려 주는 모양입니다. (GET /api/v1/realtime/check-call, 2026-10-18)
//   열림: { "open": true, "reason": null,
//           "studied": { "title": "미적분 3단원", "minutes": 40, "endedAt": 1792310400000 } }
//   닫힘: { "open": false, "studied": null, "reason": "NO_STUDY_TODAY" }
// - studied 는 오늘 가장 최근에 끝낸 공부입니다. 위로하는 통화라서 공부 없이 열렸으면 null 입니다.
//   title 은 카드 제목이고, 카드 없이 켠 옛 기록이면 null 입니다. endedAt 은 밀리초 숫자입니다. (FE 의 Date.now() 와 같은 모양)
// - 체크리스트는 FE 가 이미 가지고 있어서 보내지 않습니다. (대본에는 넣습니다)
public record CheckCallResponse(boolean open, Studied studied, String reason) {

  public record Studied(String title, int minutes, long endedAt) {}

  public static CheckCallResponse of(CheckCall.Gate gate) {
    CheckCall.Studied studied = gate.studied();
    return new CheckCallResponse(
        gate.open(),
        studied == null
            ? null
            : new Studied(studied.title(), studied.minutes(), studied.endedAt().toEpochMilli()),
        gate.reason());
  }
}
