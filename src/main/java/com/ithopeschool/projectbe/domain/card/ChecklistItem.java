package com.ithopeschool.projectbe.domain.card;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 카드 체크리스트의 한 항목. 딸린 테이블 card_checklist_items 의 한 줄입니다. (FE 의 ChecklistItem)
// 엔티티가 아니라 카드(Card)에 매달린 값이라서 @Embeddable 입니다. 그래서 고유 번호(id)가 없습니다. (SubjectLevel 과 같은 방식)
//
// 체크했는지(doneAt)는 여기에 두지 않습니다. 반복 카드는 한 장이라서 날짜마다 체크 상태를 나눠 담을 곳이
// 그날의 세션 기록(endReport.checked, 이슈 #224)이기 때문입니다. (#222 의 결정)
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChecklistItem {

  // 항목을 구분하는 글자. FE 의 ChecklistItem.id 입니다. (예: "i1")
  // 세션 기록의 "체크한 항목" 이 이 값으로 항목을 가리킵니다.
  // 컬럼 이름을 id 나 key 로 하지 않은 이유: key 는 DB 예약어이고, id 는 고유 번호처럼 보여서 헷갈립니다.
  @Column(name = "item_key", nullable = false, length = 40)
  private String itemKey;

  // 할 일 (예: "수학 문제집 42~45쪽"). 100자까지
  @Column(nullable = false, length = 100)
  private String text;

  public ChecklistItem(String itemKey, String text) {
    this.itemKey = itemKey;
    this.text = text;
  }
}
