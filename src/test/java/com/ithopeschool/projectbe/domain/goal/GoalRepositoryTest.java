package com.ithopeschool.projectbe.domain.goal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// GoalRepository 가 진짜 DB(H2)에서 "내 것만" 정확히 찾고, 고친 기록이 순서대로 다시 읽히는지 확인합니다.
@DataJpaTest
class GoalRepositoryTest {

  @Autowired private GoalRepository repository;
  @Autowired private TestEntityManager em;

  private static final LocalDate EXAM_DAY = LocalDate.of(2026, 11, 19);

  private Goal save(Long userId, String title) {
    return em.persistAndFlush(
        new Goal(
            userId,
            GoalKind.COMPLETION,
            new Goal.Target(title, EXAM_DAY, null, 300, "쪽"),
            new Goal.Woop("a", "b", "c", "d"),
            Instant.EPOCH));
  }

  @Test
  @DisplayName("내 목표만, 만든 순서대로 나오고, 진행 중인 개수를 센다")
  void findAndCount() {
    save(1L, "영단어장");
    save(2L, "남의 목표");
    Goal second = save(1L, "수학 문제집");
    second.giveUp("바꿈");
    em.persistAndFlush(second);
    em.clear();

    assertThat(repository.findByUserIdOrderById(1L))
        .extracting(Goal::getTitle)
        .containsExactly("영단어장", "수학 문제집");
    assertThat(repository.countByUserIdAndStatus(1L, GoalStatus.ACTIVE)).isEqualTo(1);
    assertThat(repository.countByUserIdAndStatus(1L, GoalStatus.GIVEN_UP)).isEqualTo(1);
  }

  @Test
  @DisplayName("번호로 찾을 때 남의 목표는 나오지 않는다")
  void findByIdAndUserId() {
    Goal others = save(2L, "남의 목표");

    assertThat(repository.findByIdAndUserId(others.getId(), 1L)).isEmpty();
    assertThat(repository.findByIdAndUserId(others.getId(), 2L)).isPresent();
  }

  @Test
  @DisplayName("목표값을 두 번 고치면 고치기 전 값 두 줄이 순서대로 저장되고 다시 읽힌다")
  void historyIsStoredInOrder() {
    Goal goal = save(1L, "영단어장 1회독");
    goal.changeTarget(new Goal.Target("영단어장 1회독", EXAM_DAY, null, 200, "쪽"), "줄임", Instant.EPOCH);
    goal.changeTarget(
        new Goal.Target("영단어장 반", EXAM_DAY, null, 200, "쪽"), "이름 바꿈", Instant.ofEpochSecond(60));
    em.persistAndFlush(goal);
    em.clear();

    List<GoalTargetSnapshot> history = repository.findById(goal.getId()).orElseThrow().getHistory();

    assertThat(history).extracting(GoalTargetSnapshot::getReason).containsExactly("줄임", "이름 바꿈");
    assertThat(history.get(0).getTotalAmount()).isEqualTo(300);
    assertThat(history.get(1).getTotalAmount()).isEqualTo(200);
    assertThat(history.get(1).getDDay()).isEqualTo(EXAM_DAY);
  }
}
