package com.ithopeschool.projectbe.domain.exam;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// exams 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 시험이 섞이지 않습니다.
public interface ExamRepository extends JpaRepository<Exam, Long> {

  // 내 시험을 모두 찾습니다. 시험 날짜가 늦은 것부터, 같은 날이면 나중에 만든 것(번호가 큰 것)부터입니다.
  List<Exam> findByUserIdOrderByExamDateDescIdDesc(Long userId);

  // 내 시험 중에서 번호로 하나 찾습니다. 없는 번호이거나 남의 시험이면 빈 Optional 입니다.
  Optional<Exam> findByIdAndUserId(Long id, Long userId);

  // 내 시험 중 이 목표에 이어진 것. 시험 날짜가 늦은 것부터입니다. (이슈 #228, 목표 상세의 시험 목록)
  List<Exam> findByUserIdAndGoalIdOrderByExamDateDescIdDesc(Long userId, Long goalId);
}
