package com.ithopeschool.projectbe.domain.manual.dto;

import com.ithopeschool.projectbe.domain.manual.ManualItem;
import java.time.LocalDate;

// 나 사용설명서 항목 하나의 응답입니다. (FE 의 ManualItem 과 같은 이름)
//   { "id": "experiment-3", "category": "effectiveMethod", "title": "시작할 때 폰을 가방에 넣기",
//     "confirmedDate": "2026-10-15", "evidence": "14일 실험: 카드 실행률 50% → 70% (+20%p)" }
//
// id: 항목을 구분하는 값. 어디서 온 항목인지 앞에 붙습니다. ("experiment-3" 실험 3번 · "insight-7" 인사이트 7번)
// category: rhythm · cautionDay · effectiveMethod · careStyle 중 하나
public record ManualItemResponse(
    String id, String category, String title, LocalDate confirmedDate, String evidence) {

  public static ManualItemResponse of(String id, ManualItem item) {
    return new ManualItemResponse(
        id, item.category().getValue(), item.title(), item.confirmedDate(), item.evidence());
  }
}
