package com.ithopeschool.projectbe.domain.plan;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// study_plans 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 계획이 섞이지 않습니다.
public interface StudyPlanRepository extends JpaRepository<StudyPlan, Long> {

  // 그날의 내 계획을 만든 순서(번호 순서)대로 모두 찾습니다. 없으면 빈 목록입니다.
  // (user_id, plan_date) 색인 덕분에 계획이 많아져도 빨리 찾습니다.
  List<StudyPlan> findByUserIdAndPlanDateOrderById(Long userId, LocalDate planDate);

  // 내 계획 중에서 번호로 하나 찾습니다. 없는 번호이거나 남의 계획이면 빈 Optional 입니다.
  Optional<StudyPlan> findByIdAndUserId(Long id, Long userId);
}
