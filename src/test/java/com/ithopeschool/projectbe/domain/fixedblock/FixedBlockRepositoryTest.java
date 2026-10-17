package com.ithopeschool.projectbe.domain.fixedblock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// FixedBlockRepository 가 진짜 DB(H2)에서 "내 것만" 정확히 찾는지 확인합니다.
// 두 사람(1번, 2번)의 일정을 섞어 넣어 두고, 남의 것이 딸려 나오지 않는지 봅니다.
@DataJpaTest
class FixedBlockRepositoryTest {

  @Autowired private FixedBlockRepository repository;
  @Autowired private TestEntityManager em;

  private FixedBlock save(Long userId, String title, LocalTime start, LocalTime end) {
    return em.persistAndFlush(
        new FixedBlock(userId, title, List.of(1, 3), start, end, Instant.EPOCH));
  }

  @Test
  @DisplayName("내 일정만, 만든 순서대로 나온다. 시각 · 요일도 그대로 다시 읽힌다")
  void findByUserIdOrderById() {
    save(1L, "학교", LocalTime.of(8, 0), LocalTime.of(16, 30));
    save(2L, "남의 학원", LocalTime.of(18, 0), LocalTime.of(21, 0));
    save(1L, "야간 자습", LocalTime.of(23, 0), LocalTime.of(1, 0));
    em.clear();

    List<FixedBlock> mine = repository.findByUserIdOrderById(1L);

    assertThat(mine).extracting(FixedBlock::getTitle).containsExactly("학교", "야간 자습");
    assertThat(mine.get(1).getEndTime()).isEqualTo(LocalTime.of(1, 0));
    assertThat(mine.get(0).repeatDayList()).containsExactly(1, 3);
  }

  @Test
  @DisplayName("번호로 찾을 때 남의 일정은 나오지 않는다")
  void findByIdAndUserId() {
    FixedBlock others = save(2L, "남의 학원", LocalTime.of(18, 0), LocalTime.of(21, 0));

    assertThat(repository.findByIdAndUserId(others.getId(), 1L)).isEmpty();
    assertThat(repository.findByIdAndUserId(others.getId(), 2L)).isPresent();
  }
}
