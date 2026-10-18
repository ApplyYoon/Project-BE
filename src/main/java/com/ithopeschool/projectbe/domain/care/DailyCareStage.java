package com.ithopeschool.projectbe.domain.care;

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

// 날마다의 관리 단계 테이블. 한 줄 = 한 사람의 하루 관리 단계(0 ~ 4)와 그 이유. (이슈 #62, 「5.4 관리 단계」)
// 판정은 CareStageService 가 하루에 한 번 하고, 한 번 정한 줄은 바꾸지 않습니다.
// 이 기록이 쌓여야 "당근 주 vs 채찍 주" 비교(#47)를 할 수 있습니다.
//
// 같은 사람의 같은 날짜는 한 줄만 있습니다. (uk_daily_care_stages_user_id_stage_date)
@Entity
@Table(
    name = "daily_care_stages",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_daily_care_stages_user_id_stage_date",
            columnNames = {"user_id", "stage_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyCareStage {

  // 줄을 구분하는 고유 번호
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 누구의 단계인지 (users.id)
  @Column(nullable = false)
  private Long userId;

  // 어느 날의 단계인지 (하루는 새벽 4시 기준, DayRule)
  @Column(nullable = false)
  private LocalDate stageDate;

  // 관리 단계 0 ~ 4 (0 회복 · 1 당근 · 2 넛지 · 3 심리적 채찍 · 4 강제적 채찍, CareLevelInfo)
  @Column(nullable = false)
  private int stage;

  // 어느 규칙으로 정했는지 (SAFETY · RECOVERY · UP …, CareStage.Rule)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CareStage.Rule rule;

  // 계획을 줄여 보자고 제안할지
  @Column(nullable = false)
  private boolean suggestShrink;

  // 왜 이 단계인지 한 줄 (홈 · 통화에서 그대로 보여 줌)
  @Column(nullable = false, length = 200)
  private String reason;

  // 판정한 시각
  @Column(nullable = false, updatable = false)
  private Instant decidedAt;

  public DailyCareStage(
      Long userId, LocalDate stageDate, CareStage.Result result, Instant decidedAt) {
    this.userId = userId;
    this.stageDate = stageDate;
    this.stage = result.stage();
    this.rule = result.rule();
    this.suggestShrink = result.suggestShrink();
    this.reason = result.reason();
    this.decidedAt = decidedAt;
  }
}
