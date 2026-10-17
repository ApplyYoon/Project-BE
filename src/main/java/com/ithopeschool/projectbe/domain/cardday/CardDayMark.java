package com.ithopeschool.projectbe.domain.cardday;

import com.ithopeschool.projectbe.domain.care.MissReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 카드 하루치 표시 테이블. 한 줄 = 카드 한 장 · 날짜 하나를 미뤘거나 실패로 기록한 것. (이슈 #225)
// FE 의 카드 결과 판정(src/lib/cardResult.ts 의 CardDay.mark)이 이 표시를 먼저 봅니다.
//
// 같은 카드의 같은 날짜에는 한 줄만 있습니다. (미루고 또 실패로 기록하거나, 두 번 미룰 수 없음)
// 반복 카드는 한 장이라 날짜(mark_date)로 "어느 날의 카드" 인지 구별합니다.
//
// 위기 표현이 감지된 이유 글은 저장하지 않고 crisis_detected 표시만 남깁니다. (통화 기록 call_records 와 같은 방식)
// "내 표시를 날짜로 찾는" 일이 많아서 (user_id, mark_date) 에 색인을 만들었습니다.
@Entity
@Table(
    name = "card_day_marks",
    indexes =
        @Index(name = "idx_card_day_marks_user_id_mark_date", columnList = "user_id, mark_date"),
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_card_day_marks_card_id_mark_date",
            columnNames = {"card_id", "mark_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardDayMark {

  // 표시를 구분하는 고유 번호
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 누구의 표시인지 (users.id)
  @Column(nullable = false)
  private Long userId;

  // 어느 카드인지 (cards.id)
  @Column(nullable = false)
  private Long cardId;

  // 어느 날의 카드인지 (미루거나 실패한 그 날짜)
  @Column(nullable = false)
  private LocalDate markDate;

  // 미루기(POSTPONED) / 실패(FAILED)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private CardDayMarkType mark;

  // 미루기: 왜 미루는지 + 어떻게 할지 (100 ~ 500자) / 실패: 직접 쓴 사유 (500자까지, 비어도 됨)
  // 위기 표현이 감지되면 저장하지 않습니다(비어 있음).
  @Column(length = 500)
  private String reason;

  // 실패 원인 태그 (실패일 때만, MissReason 8가지 중 하나)
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private MissReason cause;

  // 미룬 새 날짜 (미루기일 때만)
  private LocalDate newDate;

  // 미룬 새 시작 시각 (미루기일 때만)
  private LocalTime newStartTime;

  // 이유 글에 위기 표현이 있었는지 (#56). true 면 reason 은 비워 둡니다.
  @Column(nullable = false)
  private boolean crisisDetected;

  // 표시를 남긴 시각
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  // 미루기 표시를 만듭니다. 위기 표현이 있으면 이유 글은 남기지 않습니다.
  public static CardDayMark postponed(
      Long userId,
      Long cardId,
      LocalDate markDate,
      String reason,
      LocalDate newDate,
      LocalTime newStartTime,
      boolean crisisDetected,
      Instant createdAt) {
    CardDayMark mark =
        new CardDayMark(
            userId, cardId, markDate, CardDayMarkType.POSTPONED, reason, crisisDetected, createdAt);
    mark.newDate = newDate;
    mark.newStartTime = newStartTime;
    return mark;
  }

  // 실패 표시를 만듭니다. 위기 표현이 있으면 이유 글은 남기지 않습니다.
  public static CardDayMark failed(
      Long userId,
      Long cardId,
      LocalDate markDate,
      MissReason cause,
      String reason,
      boolean crisisDetected,
      Instant createdAt) {
    CardDayMark mark =
        new CardDayMark(
            userId, cardId, markDate, CardDayMarkType.FAILED, reason, crisisDetected, createdAt);
    mark.cause = cause;
    return mark;
  }

  private CardDayMark(
      Long userId,
      Long cardId,
      LocalDate markDate,
      CardDayMarkType mark,
      String reason,
      boolean crisisDetected,
      Instant createdAt) {
    this.userId = userId;
    this.cardId = cardId;
    this.markDate = markDate;
    this.mark = mark;
    this.reason = crisisDetected ? null : reason;
    this.crisisDetected = crisisDetected;
    this.createdAt = createdAt;
  }
}
