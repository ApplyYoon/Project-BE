package com.ithopeschool.projectbe.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// StudyPlanRepository 가 진짜 DB(H2)에서 "내 것, 그날 것만" 정확히 찾는지 확인합니다.
// 두 사람(1번, 2번)의 계획을 여러 날짜에 섞어 넣어 두고, 남의 것이나 다른 날 것이 딸려 나오지 않는지 봅니다.
@DataJpaTest
class StudyPlanRepositoryTest {

  @Autowired private StudyPlanRepository studyPlanRepository;
  @Autowired private TestEntityManager em;

  private static final LocalDate OCT_5 = LocalDate.of(2026, 10, 5);
  private static final LocalDate OCT_6 = LocalDate.of(2026, 10, 6);

  private void save(Long userId, LocalDate date, String content) {
    em.persistAndFlush(new StudyPlan(userId, 1L, date, content, 60));
  }

  @Test
  @DisplayName("내 계획 중 그날 것만, 만든 순서대로 나온다")
  void findByUserIdAndPlanDate() {
    save(1L, OCT_5, "수학");
    save(2L, OCT_5, "남의 계획");
    save(1L, OCT_6, "다음 날 계획");
    save(1L, OCT_5, "영어");
    save(1L, OCT_5, "과학");
    em.clear();

    assertThat(studyPlanRepository.findByUserIdAndPlanDateOrderById(1L, OCT_5))
        .extracting(StudyPlan::getContent)
        .containsExactly("수학", "영어", "과학");
    assertThat(studyPlanRepository.findByUserIdAndPlanDateOrderById(2L, OCT_5))
        .extracting(StudyPlan::getContent)
        .containsExactly("남의 계획");
  }

  @Test
  @DisplayName("계획이 없는 날, 계획이 없는 사람은 빈 목록")
  void emptyResults() {
    save(1L, OCT_5, "수학");

    assertThat(studyPlanRepository.findByUserIdAndPlanDateOrderById(1L, OCT_6)).isEmpty();
    assertThat(studyPlanRepository.findByUserIdAndPlanDateOrderById(999L, OCT_5)).isEmpty();
  }
}
