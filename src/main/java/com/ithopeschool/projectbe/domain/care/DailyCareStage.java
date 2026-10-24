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
// 판정은 CareStageService 가 하루에 한 번 하고, 판정한 값(stage · rule · reason)은 바꾸지 않습니다.
// 4단계 칸(forcedCardId · forcedCardDate · releasedAt)만 그날 안에 바뀝니다. (이슈 #234, Stage4)
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

  // 4단계라서 그날 하루만 절대 카드로 보는 카드 번호 (cards.id). 4단계가 아니거나, 고를 카드가 없거나, 풀렸으면 비어 있습니다.
  // 카드의 우선순위(cards.priority)는 바꾸지 않아서, 풀 때 되돌릴 값이 따로 없습니다.
  private Long forcedCardId;

  // 위 카드를 절대로 보는 날짜 (반복 카드의 그날 하루만)
  private LocalDate forcedCardDate;

  // 4단계를 푼 시각 (증빙이 있는 실행 1회). 비어 있으면 아직 안 풀렸고, 있으면 3단계로 봅니다. (currentStage)
  private Instant releasedAt;

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

  // 4단계에서 다음 반복 카드 1장을 그날 하루만 절대 카드로 정합니다.
  public void forceAbsolute(Long cardId, LocalDate cardDate) {
    this.forcedCardId = cardId;
    this.forcedCardDate = cardDate;
  }

  // 4단계를 풉니다. 절대로 바꾼 카드도 원래대로(유연) 돌아갑니다.
  public void release(Instant releasedAt) {
    this.releasedAt = releasedAt;
    this.forcedCardId = null;
    this.forcedCardDate = null;
  }

  // 풀렸는지
  public boolean isReleased() {
    return releasedAt != null;
  }

  // 지금의 단계. 4단계를 풀었으면 3단계, 아니면 판정한 단계 그대로입니다.
  public int currentStage() {
    return isReleased() ? Stage4.RELEASED_STAGE : stage;
  }

  // 지금 보여 줄 이유 한 줄. 풀었으면 푼 이유입니다.
  public String currentReason() {
    return isReleased() ? Stage4.RELEASE_REASON : reason;
  }
}
