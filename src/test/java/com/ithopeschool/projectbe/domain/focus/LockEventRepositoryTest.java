package com.ithopeschool.projectbe.domain.focus;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class LockEventRepositoryTest {
  @Autowired LockEventRepository repository;

  @Test
  void excludesOtherUserAndEndBoundary() {
    Instant start = Instant.parse("2026-10-16T19:00:00Z");
    Instant end = start.plusSeconds(86400);
    LockEvent mine = repository.save(new LockEvent(1L, start, "app", "reason"));
    repository.save(new LockEvent(2L, start, "app", "reason"));
    repository.save(new LockEvent(1L, end, "app", "reason"));
    assertThat(
            repository
                .findByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
                    1L, start, end))
        .extracting(LockEvent::getId)
        .containsExactly(mine.getId());
  }
}
