package com.ithopeschool.projectbe.domain.exam;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// exam_scores 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 점수가 섞이지 않습니다.
public interface ExamScoreRepository extends JpaRepository<ExamScore, Long> {

  // 시험 하나의 내 점수를 만든 순서(번호 순서)대로 찾습니다. 점수가 없으면 빈 목록입니다.
  List<ExamScore> findByUserIdAndExamIdOrderById(Long userId, Long examId);

  // 여러 시험의 내 점수를 한 번에 찾습니다. (시험 목록을 만들 때 시험마다 따로 묻지 않으려고 씁니다)
  List<ExamScore> findByUserIdAndExamIdInOrderById(Long userId, Collection<Long> examIds);
}
