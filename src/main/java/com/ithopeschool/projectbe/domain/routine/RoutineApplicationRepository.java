package com.ithopeschool.projectbe.domain.routine;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineApplicationRepository extends JpaRepository<RoutineApplication, Long> {
  List<RoutineApplication> findByUserIdAndPlanDate(Long userId, LocalDate date);
}
