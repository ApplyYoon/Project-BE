package com.ithopeschool.projectbe.domain.routine;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineRepository extends JpaRepository<Routine, Long> {
  List<Routine> findByUserIdOrderById(Long userId);

  Optional<Routine> findByIdAndUserId(Long id, Long userId);
}
