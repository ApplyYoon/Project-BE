package com.ithopeschool.projectbe.domain.focus;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LockEventRepository extends JpaRepository<LockEvent, Long> {
  List<LockEvent>
      findByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
          Long userId, Instant start, Instant end);
}
