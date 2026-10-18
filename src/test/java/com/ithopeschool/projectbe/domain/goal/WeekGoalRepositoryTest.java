package com.ithopeschool.projectbe.domain.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

// 진짜 DB(H2)에서 한 주에 한 줄 규칙과 판정 대상 찾기를 확인합니다. (이슈 #88)
@DataJpaTest
class WeekGoalRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-10-21T03:00:00Z");
  private static final WeeklyGoal KEEP = new WeeklyGoal(WeeklyGoal.Kind.KEEP, 50, 50);

  @Autowired private WeekGoalRepository weekGoalRepository;

  @Test
  @DisplayName("같은 사람의 같은 주에는 두 줄을 저장할 수 없다 (다른 사람은 된다)")
  void oneGoalPerWeek() {
    LocalDate monday = LocalDate.of(2026, 10, 19);
    weekGoalRepository.saveAndFlush(new WeekGoal(1L, monday, KEEP, NOW));
    weekGoalRepository.saveAndFlush(new WeekGoal(2L, monday, KEEP, NOW));

    assertThatThrownBy(() -> weekGoalRepository.saveAndFlush(new WeekGoal(1L, monday, KEEP, NOW)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("판정 대상은 판정 전이고 그 주가 끝난(월요일이 기준 이하인) 목표만, 모든 사람 것")
  void endedAndNotJudged() {
    WeekGoal ended =
        weekGoalRepository.saveAndFlush(new WeekGoal(1L, LocalDate.of(2026, 10, 12), KEEP, NOW));
    WeekGoal judged = new WeekGoal(2L, LocalDate.of(2026, 10, 5), KEEP, NOW);
    judged.judge(new WeeklyGoal.Progress(50, WeeklyGoal.Status.ACHIEVED, 100), NOW);
    weekGoalRepository.saveAndFlush(judged);
    weekGoalRepository.saveAndFlush(new WeekGoal(3L, LocalDate.of(2026, 10, 19), KEEP, NOW));

    assertThat(
            weekGoalRepository.findByStatusIsNullAndWeekStartLessThanEqualOrderById(
                LocalDate.of(2026, 10, 12)))
        .extracting(WeekGoal::getId)
        .containsExactly(ended.getId());
  }
}
