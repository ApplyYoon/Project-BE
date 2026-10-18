package com.ithopeschool.projectbe.domain.experiment;

import com.ithopeschool.projectbe.domain.care.MissReason;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 자기 실험 테이블. 한 줄 = 한 사람이 시작한 실험 하나. (최종 기획서 「4.6 분석」 자기 실험, 이슈 #52 · #54)
//
// 가설 → 전략 하나 → 관련 카드에 '실험 규칙' 으로 붙임(새 카드가 아님) → 14일 → 판정.
// 시작 전에 정한 그만둘 기준(min_gain_pp)은 바꾸지 않습니다. (매몰 비용 대응: 판정일에는 미리 정한 기준만 봅니다)
// 판정이 끝나면 상태와 근거 숫자(기준선 · 실험 기간 실행률)를 남기고 더는 바꾸지 않습니다.
//
// 실험 규칙을 붙인 카드는 여러 장이라 딸린 테이블(@ElementCollection)에 담습니다.
//   experiment_cards : (experiment_id, card_id)  붙인 카드마다 한 줄
@Entity
@Table(name = "experiments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Experiment {

  // 줄을 구분하는 고유 번호
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 누구의 실험인지 (users.id)
  @Column(nullable = false)
  private Long userId;

  // 가설 한 줄 (예: "폰·SNS 때문에 놓치는 날이 많다")
  @Column(nullable = false, length = 100)
  private String hypothesis;

  // 가설의 원인 (카드 미실행 원인과 같은 값). 모르면 비어 있습니다.
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private MissReason cause;

  // 고른 전략 (ExperimentStrategy, FE 와는 "phone-flip" 같은 id 로 주고받음)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ExperimentStrategy strategy;

  // 시작한 날. 이날부터 14일입니다. (하루는 새벽 4시 기준, DayRule)
  @Column(nullable = false)
  private LocalDate startDate;

  // 그만둘 기준(%p): 기준선보다 실행률이 이만큼 넘게 올라야 효과 있음. 시작 전에 정하고 바꾸지 않습니다.
  @Column(nullable = false)
  private int minGainPp;

  // 지금 상태 (진행 중 · 효과 있음 · 효과 없음 · 알 수 없음)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ExperimentStatus status;

  // 판정 때 계산한 기준선(시작 전 14일) 평균 실행률 (0 ~ 1). 판정 전이거나 카드가 모자라면 비어 있습니다.
  private Double baselineRate;

  // 판정 때 계산한 실험 기간 평균 실행률 (0 ~ 1). 판정 전이거나 카드가 모자라면 비어 있습니다.
  private Double experimentRate;

  // 판정한 시각. 진행 중이면 비어 있습니다.
  private Instant judgedAt;

  // 실험을 시작한(저장한) 시각
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  // 실험 규칙을 붙인 카드들 (cards.id). EAGER: 실험을 보여 줄 때 늘 같이 씁니다.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "experiment_cards", joinColumns = @JoinColumn(name = "experiment_id"))
  @Column(name = "card_id", nullable = false)
  private Set<Long> cardIds = new LinkedHashSet<>();

  public Experiment(
      Long userId,
      String hypothesis,
      MissReason cause,
      ExperimentStrategy strategy,
      Set<Long> cardIds,
      LocalDate startDate,
      int minGainPp,
      Instant createdAt) {
    this.userId = userId;
    this.hypothesis = hypothesis;
    this.cause = cause;
    this.strategy = strategy;
    this.cardIds = new LinkedHashSet<>(cardIds);
    this.startDate = startDate;
    this.minGainPp = minGainPp;
    this.status = ExperimentStatus.RUNNING;
    this.createdAt = createdAt;
  }

  // 판정 결과를 남깁니다. 진행 중인 실험만 판정합니다. (한 번 판정하면 바꾸지 않음)
  public void judge(ExperimentRule.Judgement judgement, Instant now) {
    if (status != ExperimentStatus.RUNNING) {
      return;
    }
    this.status = judgement.status();
    this.baselineRate = judgement.baselineRate();
    this.experimentRate = judgement.experimentRate();
    this.judgedAt = now;
  }
}
