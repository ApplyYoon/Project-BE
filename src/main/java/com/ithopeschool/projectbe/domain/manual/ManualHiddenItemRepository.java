package com.ithopeschool.projectbe.domain.manual;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// manual_hidden_items 테이블의 창구입니다. 모든 메서드에 userId 조건이 붙어 있어서, 남의 숨기기가 섞이지 않습니다.
public interface ManualHiddenItemRepository extends JpaRepository<ManualHiddenItem, Long> {

  // 내가 숨긴 항목 전부 (목록에서 빼는 데 씁니다)
  List<ManualHiddenItem> findByUserId(Long userId);

  // 내가 이 항목을 이미 숨겼는지 (두 번 숨겨도 한 줄만 두려고 씁니다)
  boolean existsByUserIdAndItemId(Long userId, String itemId);
}
