package com.ithopeschool.projectbe.domain.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.plan.PlanKind;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class TargetRecommendationRepositoryTest {
  @Autowired private StudySessionRepository repository;
  @Autowired private TestEntityManager em;

  @Test
  void onlyLatestThreeOwnedStudyRecordsWithTargets() {
    Instant base = Instant.parse("2026-10-01T00:00:00Z");
    for (int i = 0; i < 4; i++) {
      save(7L, 3L, base.plusSeconds(i * 3600), 50, PlanKind.STUDY);
    }
    save(8L, 3L, base.plusSeconds(20000), 50, PlanKind.STUDY);
    save(7L, 4L, base.plusSeconds(20000), 50, PlanKind.STUDY);
    save(7L, 3L, base.plusSeconds(20000), 50, PlanKind.PLAY);
    save(7L, 3L, base.plusSeconds(20000), null, PlanKind.STUDY);
    save(7L, 3L, base.plusSeconds(20000), 0, PlanKind.STUDY);
    em.flush();
    var result =
        repository
            .findTop3ByUserIdAndSubjectIdAndKindAndTargetMinutesGreaterThanOrderByStartedAtDescIdDesc(
                7L, 3L, PlanKind.STUDY, 0);
    assertThat(result)
        .extracting(StudySession::getStartedAt)
        .containsExactly(base.plusSeconds(10800), base.plusSeconds(7200), base.plusSeconds(3600));
  }

  private void save(Long userId, Long subjectId, Instant start, Integer target, PlanKind kind) {
    em.persist(
        new StudySession(
            userId, subjectId, start, start.plusSeconds(3000), 0, 0, null, target, null, kind));
  }
}
