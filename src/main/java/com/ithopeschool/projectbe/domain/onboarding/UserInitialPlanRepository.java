package com.ithopeschool.projectbe.domain.onboarding;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// initial_plans 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 계획이 섞이지 않습니다.
public interface UserInitialPlanRepository extends JpaRepository<UserInitialPlan, Long> {

  // 내 처음 계획. 아직 안 세웠으면 빈 Optional 입니다.
  Optional<UserInitialPlan> findByUserId(Long userId);

  // 내가 처음 계획을 이미 세웠는지 알려줍니다.
  boolean existsByUserId(Long userId);
}
