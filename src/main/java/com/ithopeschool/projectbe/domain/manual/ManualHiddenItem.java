package com.ithopeschool.projectbe.domain.manual;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 숨긴 사용설명서 항목 테이블. 한 줄 = 한 사람이 숨긴 사용설명서 항목 하나. (이슈 #46, 담당 학생이 설계)
// 사용설명서는 따로 저장하지 않고 부를 때마다 실험 · 인사이트에서 모아 만들어서, 숨긴 항목은 이름표(itemId)만 적어 둡니다.
// GET /api/v1/manual 은 이 표에 있는 이름표의 항목을 빼고 돌려줍니다.
//
// 같은 사람이 같은 항목을 두 번 숨겨도 한 줄만 있습니다. (uk_manual_hidden_items_user_id_item_id)
// 칸 이름이 userId 여야 탈퇴할 때 이 줄도 같이 지워집니다. (AccountService.withdraw 가 userId 칸을 보고 지움)
@Entity
@Table(
    name = "manual_hidden_items",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_manual_hidden_items_user_id_item_id",
            columnNames = {"user_id", "item_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ManualHiddenItem {

  // 줄을 구분하는 고유 번호
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 누가 숨겼는지 (users.id)
  @Column(nullable = false)
  private Long userId;

  // 숨긴 항목의 이름표 (예: "experiment-3" · "insight-morning-vs-night", ManualService 가 정한 id)
  // 다른 표의 번호가 아니라 글자라서, 실험 · 인사이트 표와 직접 이어져 있지 않습니다.
  @Column(nullable = false, length = 100)
  private String itemId;

  public ManualHiddenItem(Long userId, String itemId) {
    this.userId = userId;
    this.itemId = itemId;
  }
}
