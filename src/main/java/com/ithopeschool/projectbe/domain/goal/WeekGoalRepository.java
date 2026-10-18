package com.ithopeschool.projectbe.domain.goal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// weekly_goals 테이블의 창구입니다. 한 사람의 목표를 찾을 때는 늘 userId 가 들어 있습니다. (이슈 #88)
public interface WeekGoalRepository extends JpaRepository<WeekGoal, Long> {

  // 내 그 주(월요일)의 목표. 아직 안 정했으면 빈 Optional 입니다.
  Optional<WeekGoal> findByUserIdAndWeekStart(Long userId, LocalDate weekStart);

  // 내 from ~ to (둘 다 포함) 사이의 주 목표들, 주 순서로
  List<WeekGoal> findByUserIdAndWeekStartBetweenOrderByWeekStartAsc(
      Long userId, LocalDate from, LocalDate to);

  // 판정 전인 목표 중, 그 주가 끝난 것들 (예약 작업이 모든 학생 것을 한 번에 판정할 때)
  // lastWeekStart: 이 날짜까지의 월요일로 시작한 주가 끝난 주입니다.
  List<WeekGoal> findByStatusIsNullAndWeekStartLessThanEqualOrderById(LocalDate lastWeekStart);
}
