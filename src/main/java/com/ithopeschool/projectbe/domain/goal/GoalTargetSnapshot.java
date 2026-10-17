package com.ithopeschool.projectbe.domain.goal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 목표값을 고치기 "전" 의 값 한 줄. 딸린 테이블 goal_target_snapshots 의 한 줄입니다. (FE 의 GoalTargetSnapshot)
// 엔티티가 아니라 목표(Goal)에 매달린 값이라서 @Embeddable 입니다. (카드의 ChecklistItem 과 같은 방식)
//
// 왜 남기나요? 목표를 슬쩍 낮추면("3등급" → "4등급") 나중에 "원래 목표" 를 잊습니다. (기획서 3 · 7번 오류: 목표 하향 · 후견지명)
// 그래서 고칠 때마다 원래 값과 이유를 지우지 않고 쌓아 둡니다. 이 줄은 바꾸거나 지우는 API 가 없습니다.
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoalTargetSnapshot {

  // 고치기 전의 목표 한 줄
  @Column(nullable = false, length = 40)
  private String title;

  // 고치기 전의 D-day
  @Column(nullable = false)
  private LocalDate dDay;

  // 고치기 전의 지금 점수 · 등급 (점수형만)
  @Column(length = 20)
  private String currentScore;

  // 고치기 전의 전체 분량 (완주형만)
  private Integer totalAmount;

  // 고치기 전의 분량 단위 (완주형만)
  @Column(length = 5)
  private String amountUnit;

  // 이 값에서 바꾼 시각
  @Column(nullable = false)
  private Instant changedAt;

  // 왜 바꿨는지 (100자까지)
  @Column(nullable = false, length = 100)
  private String reason;

  public GoalTargetSnapshot(
      String title,
      LocalDate dDay,
      String currentScore,
      Integer totalAmount,
      String amountUnit,
      Instant changedAt,
      String reason) {
    this.title = title;
    this.dDay = dDay;
    this.currentScore = currentScore;
    this.totalAmount = totalAmount;
    this.amountUnit = amountUnit;
    this.changedAt = changedAt;
    this.reason = reason;
  }
}
