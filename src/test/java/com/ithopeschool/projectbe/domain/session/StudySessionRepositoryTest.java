package com.ithopeschool.projectbe.domain.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.plan.PlanKind;
import java.time.Instant;
import java.util.List;
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

  // ---- 기간으로 찾기 (findStartedBetween) ----

  private List<Instant> startedBetween(Long userId, int fromMinutes, int toMinutes) {
    return studySessionRepository
        .findStartedBetween(userId, at(fromMinutes), at(toMinutes))
        .stream()
        .map(StudySession::getStartedAt)
        .toList();
  }

  @Test
  @DisplayName("기간 안에 시작한 내 기록만 찾고, 시작 시각이 빠른 순서로 돌려준다")
  void findsSessionsStartedInPeriodInOrder() {
    // setUp 에 내 기록 at(0)이 있습니다. 일부러 늦은 것을 먼저 저장해서 정렬을 확인합니다.
    em.persistAndFlush(new StudySession(1L, 10L, at(200), at(230), 0));
    em.persistAndFlush(new StudySession(1L, 10L, at(100), at(130), 0));

    assertThat(startedBetween(1L, -10, 300)).containsExactly(at(0), at(100), at(200));
  }

  @Test
  @DisplayName("기간의 시작 시각에 딱 맞게 시작한 기록은 들어가고, 끝 시각에 시작한 기록은 빠진다")
  void startIsIncludedAndEndIsExcluded() {
    em.persistAndFlush(new StudySession(1L, 10L, at(100), at(130), 0));

    // [at(0), at(100)) → at(0)은 들어가고 at(100)은 빠집니다.
    assertThat(startedBetween(1L, 0, 100)).containsExactly(at(0));
    // [at(100), at(200)) → at(100)이 들어갑니다. (하루의 끝과 다음 날의 시작이 같아도 한 번만 잡힘)
    assertThat(startedBetween(1L, 100, 200)).containsExactly(at(100));
  }

  @Test
  @DisplayName("끝난 시각이 아니라 시작한 시각으로 센다: 기간 끝을 걸친 기록은 들어가고, 기간 앞에서 시작한 기록은 빠진다")
  void countsByStartedAtNotEndedAt() {
    // 기록 at(0) ~ at(60) 입니다.
    // 기간 [at(30), at(90)) 에 끝은 들어오지만 시작이 기간 앞이라 빠집니다.
    assertThat(startedBetween(1L, 30, 90)).isEmpty();
    // 기간 [at(-10), at(30)) 에는 끝이 기간 뒤에 있어도, 시작이 기간 안이라 들어갑니다. (04:00 를 걸친 기록)
    assertThat(startedBetween(1L, -10, 30)).containsExactly(at(0));
  }

  @Test
  @DisplayName("남의 기록은 기간 안이어도 찾지 않는다")
  void findStartedBetweenIgnoresOthers() {
    // 남의 기록 at(120) 이 이 기간에 있지만, 내 기록만 나옵니다.
    assertThat(startedBetween(1L, -10, 300)).containsExactly(at(0));
    assertThat(startedBetween(2L, -10, 300)).containsExactly(at(120));
  }

  @Test
  @DisplayName("기간 안에 기록이 없으면 빈 목록이다")
  void findStartedBetweenEmpty() {
    assertThat(startedBetween(1L, 300, 400)).isEmpty();
  }

  // ---- 계획으로 찾기 (findByUserIdAndPlanIdIn) ----

  // 계획 번호와 종류를 정해서 at(startMinutes) 에 30분짜리 기록을 저장합니다.
  private void persistPlanSession(Long userId, Long planId, PlanKind kind, int startMinutes) {
    Long subjectId = kind == PlanKind.PLAY ? null : 10L;
    em.persistAndFlush(
        new StudySession(
            userId,
            subjectId,
            at(startMinutes),
            at(startMinutes + 30),
            0,
            0,
            planId,
            null,
            null,
            kind));
  }

  @Test
  @DisplayName("내 기록 중에 그 계획들로 켠 것만 찾는다 (노는 기록도 포함, 다른 계획·계획 없는 기록·남의 기록은 제외)")
  void findsMySessionsOfThosePlans() {
    persistPlanSession(1L, 7L, PlanKind.STUDY, 300); // 7번 계획 (공부)
    persistPlanSession(1L, 7L, PlanKind.STUDY, 400); // 7번 계획 (공부)
    persistPlanSession(1L, 8L, PlanKind.PLAY, 500); // 8번 계획 (노는 기록)
    persistPlanSession(1L, 9L, PlanKind.STUDY, 600); // 9번 계획 — 찾는 계획이 아님
    persistPlanSession(2L, 7L, PlanKind.STUDY, 700); // 남의 기록 (계획 번호가 같아도 제외)
    // setUp 의 내 기록 at(0)은 계획 없이 켠 것이라 제외됩니다.

    List<StudySession> found = studySessionRepository.findByUserIdAndPlanIdIn(1L, List.of(7L, 8L));

    assertThat(found)
        .extracting(StudySession::getStartedAt)
        .containsExactlyInAnyOrder(at(300), at(400), at(500));
    assertThat(found).allMatch(session -> session.getUserId().equals(1L));
  }
}
