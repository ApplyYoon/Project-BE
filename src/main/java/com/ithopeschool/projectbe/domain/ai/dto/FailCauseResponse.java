package com.ithopeschool.projectbe.domain.ai.dto;

import java.util.List;

// 예상 실패 원인 선지의 결과입니다. 아무것도 저장하지 않습니다. (이슈 #237 표 3번)
//   { "showHelp": false,
//     "options": [ { "reason": "tired", "text": "학원 갔다 와서 졸려서 미루기", "inner": false },
//                  { "reason": "event", "text": "친구 연락에 나갔다 오기", "inner": false },
//                  { "reason": "phone", "text": "문제 막힐 때 폰부터 집기", "inner": true } ] }
// - reason: 원인 글자 (체크인의 missReason 과 같은 글자: "tired", "phone", "tooMuch" …)
// - text  : 이 카드에 맞춘 짧은 장면 (AI 가 씀, 30자까지)
// - inner : 내 행동에서 나오는 원인인지 (폰·SNS · 하기 싫음). 코드가 정합니다. 3개 중 하나 이상은 true 입니다.
// - options 가 null 이면 선지를 못 만든 것입니다. (기록이 모자람 · AI 꺼짐 · 하루 한도 · 실패) FE 는 주관식으로 받습니다.
// - showHelp 가 true 면 글에서 위기 표현이 감지된 것입니다. options 는 null 이고, FE 는 도움 연결 안내를 먼저 보여 줍니다.
public record FailCauseResponse(boolean showHelp, List<Option> options) {

  // 선지 하나
  public record Option(String reason, String text, boolean inner) {}

  // 만든 선지 (못 만들었으면 null)
  public static FailCauseResponse of(List<Option> options) {
    return new FailCauseResponse(false, options);
  }

  // 위기 표현이 감지됐을 때의 응답입니다. AI 를 부르지 않습니다.
  public static FailCauseResponse help() {
    return new FailCauseResponse(true, null);
  }
}
