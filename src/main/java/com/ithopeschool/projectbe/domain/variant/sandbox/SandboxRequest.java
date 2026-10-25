package com.ithopeschool.projectbe.domain.variant.sandbox;

import java.util.List;

// 샌드박스에 넘기는 일감입니다. JSON 으로 바꿔 샌드박스 프로그램의 입력(stdin)으로 보냅니다. (docs/VARIANT-SPEC.md 6-3)
//
//   code        : AI 가 쓴 Symja 계산 코드. 마지막 값이 정답입니다
//   format      : "MC5" · "SHORT"
//   choices     : MC5 선지(Symja 식). SHORT 면 null
//   answerIndex : AI 가 말한 정답 선지 번호. SHORT 면 null
//   shortAnswer : AI 가 말한 단답형 정답. MC5 면 null
public record SandboxRequest(
    String code, String format, List<String> choices, Integer answerIndex, Integer shortAnswer) {}
