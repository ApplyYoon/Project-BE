package com.ithopeschool.projectbe.domain.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// StudySessionRepository 가 진짜 DB(H2)에서 "내 기록 중 시간이 겹치는 것" 을 정확히 찾는지 확인합니다.
@DataJpaTest
class StudySessionRepositoryTest {

  @Autowired private StudySessionRepository studySessionRepository;
  @Autowired private TestEntityManager em;

  // 기준 시각에서 몇 분 뒤인지로 시각을 만듭니다. (10:00 → at(0), 11:00 → at(60) 처럼 읽으면 됩니다)
  private static final Instant BASE = Instant.ofEpochMilli(1_790_000_000_000L);

  private static Instant at(int minutes) {
    return BASE.plusSeconds(minutes * 60L);
  }

  private boolean overlaps(Long userId, int startMinutes, int endMinutes) {
    return studySessionRepository.existsByUserIdAndStartedAtLessThanAndEndedAtGreaterThan(
        userId, at(endMinutes), at(startMinutes));
  }

  @BeforeEach
  void setUp() {
    // 내 기록 10:00 ~ 11:00
    em.persistAndFlush(new StudySession(1L, 10L, at(0), at(60), 0));
    // 남의 기록 12:00 ~ 13:00
    em.persistAndFlush(new StudySession(2L, 20L, at(120), at(180), 0));
  }

  @Test
  @DisplayName("내 기록과 일부라도 겹치면 true")
  void partlyOverlaps() {
    assertThat(overlaps(1L, 30, 90)).isTrue(); // 10:30 ~ 11:30 (뒤쪽이 겹침)
    assertThat(overlaps(1L, -30, 30)).isTrue(); // 09:30 ~ 10:30 (앞쪽이 겹침)
  }

  @Test
  @DisplayName("내 기록 안에 들어가거나, 내 기록을 감싸도 true")
  void insideOrAround() {
    assertThat(overlaps(1L, 10, 20)).isTrue(); // 10:10 ~ 10:20
    assertThat(overlaps(1L, -10, 70)).isTrue(); // 09:50 ~ 11:10
    assertThat(overlaps(1L, 0, 60)).isTrue(); // 똑같은 시간
  }

  @Test
  @DisplayName("끝과 시작이 딱 붙은 기록은 겹치지 않는다")
  void touchingIsNotOverlap() {
    assertThat(overlaps(1L, 60, 90)).isFalse(); // 11:00 ~ 11:30
    assertThat(overlaps(1L, -30, 0)).isFalse(); // 09:30 ~ 10:00
  }

  @Test
  @DisplayName("남의 기록과 겹치는 시간은 내 기록과 겹친 것이 아니다")
  void othersSessionsAreIgnored() {
    assertThat(overlaps(1L, 120, 180)).isFalse();
    assertThat(overlaps(2L, 120, 180)).isTrue();
  }
}
