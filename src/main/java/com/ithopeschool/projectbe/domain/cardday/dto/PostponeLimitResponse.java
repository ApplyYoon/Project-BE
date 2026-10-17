package com.ithopeschool.projectbe.domain.cardday.dto;

// 그 카드 · 그 날짜를 지금 몇 번 더 미룰 수 있는지입니다. 카드의 [미루기 (n회 남음)] 버튼에 씁니다.
//   { "postponesLeft": 2, "blockReason": null }
//   { "postponesLeft": 0, "blockReason": "절대 카드는 미룰 수 없어요" }
// - blockReason: 미룰 수 없으면 왜 안 되는지 한 줄, 미룰 수 있으면 null
public record PostponeLimitResponse(int postponesLeft, String blockReason) {}
