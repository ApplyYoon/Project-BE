package com.ithopeschool.projectbe.domain.goal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// goals 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 목표가 섞이지 않습니다.
public interface GoalRepository extends JpaRepository<Goal, Long> {

  // 내 목표 전부 (만든 순서). 진행 · 완료 · 포기 모두
  List<Goal> findByUserIdOrderById(Long userId);

  // 내 목표 중 그 번호. 없는 번호이거나 남의 목표면 빈 Optional 입니다.
  Optional<Goal> findByIdAndUserId(Long id, Long userId);

  // 내 목표 중 그 상태인 것의 개수. "진행 중 3개까지" 를 셀 때 씁니다.
  long countByUserIdAndStatus(Long userId, GoalStatus status);
}
