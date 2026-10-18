package com.ithopeschool.projectbe.domain.goal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 이번 주 목표 테이블. 한 줄 = 한 사람의 한 주 목표 하나. (이슈 #88)
// 목표를 고르는 규칙과 판정 규칙은 WeeklyGoal(값만 계산하는 도구)에 있고, 이 표는 고른 목표와 판정 결과를 적어 둡니다.
// 한 사람의 한 주(월요일)에는 한 줄만 있습니다. 다시 고르면 같은 줄을 덮어씁니다. (user_id, week_start 는 겹칠 수 없음)
//
// 판정은 주가 끝난 뒤 예약 작업(WeekGoalJudgeJob)이 그 주의 기록으로 채웁니다. 판정 전이면 status 가 비어 있습니다.
@Entity
@Table(
    name = "weekly_goals",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_weekly_goals_user_id_week_start",
            columnNames = {"user_id", "week_start"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeekGoal {

  // 목표 한 줄을 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 목표의 주인(users.id). 다른 사람의 목표가 섞이지 않게 하는 기준입니다.
  @Column(nullable = false)
  private Long userId;

  // 어느 주의 목표인지. 그 주의 월요일 날짜입니다. (하루는 새벽 4시 기준, WeeklyFacts.mondayOf)
  @Column(nullable = false)
  private LocalDate weekStart;

  // 목표 종류. 완주율 · 시작 지연 · 계획 채우기 · 지난주만큼 (WeeklyGoal.Kind)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15)
  private WeeklyGoal.Kind kind;

  // 목표 숫자. 시작 지연이면 분, 나머지는 % 입니다.
  @Column(nullable = false)
  private int target;

  // 제안할 때 본 지난주 숫자. 목표 숫자와 같은 단위입니다.
  @Column(nullable = false)
  private int baseline;

  // 판정 결과. 달성 · 미달 · 알 수 없음. 주가 끝나 판정하기 전이면 비어 있습니다(null).
  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private WeeklyGoal.Status status;

  // 판정할 때 본 그 주의 숫자. 계산할 수 없었거나 판정 전이면 비어 있습니다.
  private Integer resultValue;

  // 판정한 시각. 판정 전이면 비어 있습니다.
  private Instant judgedAt;

  // 목표를 정한(마지막으로 덮어쓴) 시각
  @Column(nullable = false)
  private Instant createdAt;

  public WeekGoal(Long userId, LocalDate weekStart, WeeklyGoal goal, Instant createdAt) {
    this.userId = userId;
    this.weekStart = weekStart;
    this.createdAt = createdAt;
    change(goal, createdAt);
  }

  // 같은 주에 다시 고르면 목표를 덮어씁니다. 판정 결과도 지웁니다. (아직 끝나지 않은 주만 고를 수 있음)
  public void change(WeeklyGoal goal, Instant changedAt) {
    this.kind = goal.kind();
    this.target = goal.target();
    this.baseline = goal.baseline();
    this.createdAt = changedAt;
    this.status = null;
    this.resultValue = null;
    this.judgedAt = null;
  }

  // 이 줄의 목표를 판정 규칙(WeeklyGoal)의 모양으로 돌려줍니다.
  public WeeklyGoal toGoal() {
    return new WeeklyGoal(kind, target, baseline);
  }

  // 주가 끝난 뒤의 판정 결과를 적습니다. 같은 기록으로 다시 판정하면 같은 값이 다시 적힙니다.
  public void judge(WeeklyGoal.Progress progress, Instant judgedAt) {
    this.status = progress.status();
    this.resultValue = progress.current();
    this.judgedAt = judgedAt;
  }
}
