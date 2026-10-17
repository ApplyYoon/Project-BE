package com.ithopeschool.projectbe.domain.routine;

import com.ithopeschool.projectbe.domain.plan.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.*;

@Entity
@Table(name = "routines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Routine {
  // 루틴 번호
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 루틴 주인
  @Column(nullable = false)
  private Long userId;

  // 반복 요일을 쉼표로 구분 (0~6)
  @Column(nullable = false, length = 13)
  private String weekdays;

  // 공부 또는 놀이
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private PlanKind kind;

  // 공부 과목 번호
  private Long subjectId;

  // 할 일
  @Column(nullable = false, length = 200)
  private String content;

  // 목표 분
  @Column(nullable = false)
  private int targetMinutes;

  // 시작 시각 (미정이면 null)
  private LocalTime startTime;

  public Routine(Long userId) {
    this.userId = userId;
  }

  public List<Integer> days() {
    return Arrays.stream(weekdays.split(",")).map(Integer::valueOf).toList();
  }

  public void change(
      List<Integer> days,
      PlanKind kind,
      Long subjectId,
      String content,
      int targetMinutes,
      LocalTime startTime) {
    this.weekdays =
        days.stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(","));
    this.kind = kind;
    this.subjectId = subjectId;
    this.content = content;
    this.targetMinutes = targetMinutes;
    this.startTime = startTime;
  }

  public StudyPlan planFor(LocalDate date) {
    StudyPlan plan =
        new StudyPlan(userId, subjectId, date, content, targetMinutes, startTime, kind);
    plan.markRoutine(id);
    return plan;
  }
}
