package com.ithopeschool.projectbe.domain.answercheck;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// answer_checks 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 기록은 찾지 못합니다.
public interface AnswerCheckRepository extends JpaRepository<AnswerCheck, Long> {

  // 내 채점 기록 하나. 번호가 없거나 다른 사람의 기록이면 빈 Optional 입니다. (둘 다 404 로 답합니다)
  Optional<AnswerCheck> findByIdAndUserId(Long id, Long userId);

  // 내 그날의 채점 기록들, 최근에 올린 것부터. (같은 시각이면 나중 번호부터)
  List<AnswerCheck> findByUserIdAndCheckDateOrderByCreatedAtDescIdDesc(
      Long userId, LocalDate checkDate);

  // 내 기간(from ~ to, 두 날짜 모두 포함) 채점 기록들. 최근 날짜부터, 같은 날은 위와 같은 순서(최근에 올린 것부터).
  // Between 은 양 끝을 포함합니다. (check_date >= from and check_date <= to)
  List<AnswerCheck> findByUserIdAndCheckDateBetweenOrderByCheckDateDescCreatedAtDescIdDesc(
      Long userId, LocalDate from, LocalDate to);
}
