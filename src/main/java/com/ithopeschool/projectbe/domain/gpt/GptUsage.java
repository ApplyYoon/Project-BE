package com.ithopeschool.projectbe.domain.gpt;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// GPT 호출 기록 테이블. 한 줄 = GPT 를 한 번 부른 것. (이슈 #33 "GPT 호출마다 사용자·종류·대략적인 비용을 기록한다")
//   코치 문단 한 번  →  한 줄 (비용은 바로 적음)
//   통화 한 번       →  시작할 때 한 줄 (통화 번호만), 끝나면 같은 줄에 통화 시간 · 비용을 채움
//
// 얼마나 썼는지 보는 기록입니다. (하루 한도는 2026-10-24 에 없앴습니다. GptUsageGuard)
// DB 에 남기 때문에 서버를 껐다 켜도 사라지지 않습니다.
// "오늘 내가 부른 것" 을 가장 자주 찾아서 (user_id, called_at) 에 색인(찾기 쉽게 만든 목차)을 만들었습니다.
@Entity
@Table(
    name = "gpt_usages",
    indexes = @Index(name = "idx_gpt_usages_user_id_called_at", columnList = "user_id, called_at"),
    check = {
      @CheckConstraint(
          name = "ck_gpt_usages_call_seconds",
          constraint = "call_seconds is null or call_seconds >= 0"),
      @CheckConstraint(
          name = "ck_gpt_usages_cost_won",
          constraint = "cost_won is null or cost_won >= 0")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GptUsage {

  // 기록 한 줄을 구분하는 고유 번호. 나중에 부른 것일수록 큽니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // GPT 를 부르게 한 사람(users.id)
  @Column(nullable = false)
  private Long userId;

  // 무엇 때문에 불렀는지 (CALL, CALL_REVIEW, COACH, PLAN_PARSE, CHECKLIST, ASSIST 중 하나)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private GptUsageKind kind;

  // 부른 시각 (통화는 시작한 시각)
  @Column(nullable = false)
  private Instant calledAt;

  // 어느 통화인지(call_records.id). 통화일 때만 있고, 요약·코치는 비어 있습니다.
  private Long callRecordId;

  // 통화한 시간(초). 통화가 아니거나 아직 끝나지 않았으면 비어 있습니다.
  private Integer callSeconds;

  // 대략적인 비용(원, 정수). 통화는 끝나야 알 수 있어서 그 전에는 비어 있습니다.
  private Integer costWon;

  private GptUsage(
      Long userId, GptUsageKind kind, Instant calledAt, Long callRecordId, Integer costWon) {
    this.userId = userId;
    this.kind = kind;
    this.calledAt = calledAt;
    this.callRecordId = callRecordId;
    this.costWon = costWon;
  }

  // 통화를 시작할 때의 기록. 통화 시간과 비용은 통화가 끝나면 finishCall 로 채웁니다.
  public static GptUsage callStarted(Long userId, Instant calledAt, Long callRecordId) {
    return new GptUsage(userId, GptUsageKind.CALL, calledAt, callRecordId, null);
  }

  // 글을 받는 호출(요약·코치)의 기록. 비용을 바로 적습니다.
  public static GptUsage textCall(Long userId, GptUsageKind kind, Instant calledAt, int costWon) {
    return new GptUsage(userId, kind, calledAt, null, costWon);
  }

  // 통화가 끝났을 때 통화 시간과 비용을 채웁니다.
  public void finishCall(int callSeconds, int costWon) {
    this.callSeconds = callSeconds;
    this.costWon = costWon;
  }

  // 통화 시간이 이미 적혀 있으면 true (마무리를 여러 번 불러도 처음 한 번만 적습니다)
  public boolean isCallFinished() {
    return callSeconds != null;
  }
}
