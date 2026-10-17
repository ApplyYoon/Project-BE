package com.ithopeschool.projectbe.domain.insight.dto;

import jakarta.validation.constraints.NotBlank;

// "인사이트에 반응하기" 요청의 모양입니다. 이름은 FE 의 Insight.response 와 같습니다.
//   { "response": "surprised" }
// - surprised(몰랐는데 맞아) · alreadyKnew(이미 알던 거야) · wrong(틀렸어) 중 하나인지는 Service 에서 검사합니다.
public record InsightReactionRequest(@NotBlank(message = "반응을 골라 주세요") String response) {}
