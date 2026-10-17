package com.ithopeschool.projectbe.domain.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

// 처음 계획을 실제 DB(H2)에 넣고 꺼내 봅니다.
@DataJpaTest
class UserInitialPlanRepositoryTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

  @Autowired private UserInitialPlanRepository userInitialPlanRepository;

  @Test
  @DisplayName("저장한 처음 계획을 내 번호로 찾을 수 있고, 남의 번호로는 안 보인다")
  void findsMine() {
    userInitialPlanRepository.saveAndFlush(new UserInitialPlan(1L, TODAY, 180, 5));

    assertThat(userInitialPlanRepository.findByUserId(1L))
        .get()
        .extracting(UserInitialPlan::getDailyMinutes, UserInitialPlan::getDaysPerWeek)
        .containsExactly(180, 5);
    assertThat(userInitialPlanRepository.existsByUserId(1L)).isTrue();
    assertThat(userInitialPlanRepository.findByUserId(2L)).isEmpty();
    assertThat(userInitialPlanRepository.existsByUserId(2L)).isFalse();
  }

  @Test
  @DisplayName("한 사람에 한 줄: 같은 사람의 처음 계획을 또 넣으면 DB 가 막는다 (uk_initial_plans_user_id)")
  void onePerUser() {
    userInitialPlanRepository.saveAndFlush(new UserInitialPlan(1L, TODAY, 180, 5));

    assertThatThrownBy(
            () -> userInitialPlanRepository.saveAndFlush(new UserInitialPlan(1L, TODAY, 60, 3)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
