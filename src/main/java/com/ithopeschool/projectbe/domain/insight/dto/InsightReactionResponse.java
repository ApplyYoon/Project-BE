package com.ithopeschool.projectbe.domain.insight.dto;

import com.ithopeschool.projectbe.domain.insight.Insight;

// 반응을 저장한 결과입니다.
//   { "id": "3", "response": "surprised" }
// - id 는 다른 API 와 같이 글자로 보냅니다.
public record InsightReactionResponse(String id, String response) {

  public static InsightReactionResponse from(Insight insight) {
    return new InsightReactionResponse(
        String.valueOf(insight.getId()),
        insight.getReaction() == null ? null : insight.getReaction().toValue());
  }
}
