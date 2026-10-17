package com.ithopeschool.projectbe.domain.routine;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.*;

@Entity
@Table(
    name = "routine_applications",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "routine_id", "plan_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoutineApplication {
  // 적용 기록 번호
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 기록 주인
  @Column(nullable = false)
  private Long userId;

  // 적용한 루틴 번호 (삭제되어도 기록 유지)
  @Column(nullable = false)
  private Long routineId;

  // 적용 날짜 (계획 삭제 후에도 유지)
  @Column(nullable = false)
  private LocalDate planDate;

  public RoutineApplication(Long userId, Long routineId, LocalDate planDate) {
    this.userId = userId;
    this.routineId = routineId;
    this.planDate = planDate;
  }
}
