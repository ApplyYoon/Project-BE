package com.ithopeschool.projectbe.domain.cardday.dto;

// 미루기 · 실패로 기록한 결과입니다.
//   { "mark": { 표시 하나 }, "postponesLeft": 4, "safetyBrake": false }
// - postponesLeft: 미루기를 한 뒤에 이 카드를 더 미룰 수 있는 횟수. 실패 기록에서는 null 입니다.
// - safetyBrake: 사유 글에서 위기 표현(#56)이 감지됐으면 true. 기록은 그대로 저장되고,
//   FE 는 도움 연결 안내(1388 · 109)를 먼저 보여 줍니다. (말로 계획 세우기의 showHelp 와 같은 뜻)
public record CardMarkResultResponse(
    CardDayMarkResponse mark, Integer postponesLeft, boolean safetyBrake) {}
