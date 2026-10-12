package com.ithopeschool.projectbe.domain.message;

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

// AI 메시지 테이블. 한 줄 = AI 가 어떤 사용자에게 보낸 메시지 하나. (FE 의 AiMessage)
// "내 메시지를 최신순으로 찾는" 일이 가장 많아서 (user_id, id) 에 색인(찾기 쉽게 만든 목차)을 만들었습니다.
//
// ⚠️ 메시지 내용은 일기·기분 같은 마음 기록이 아니라 AI 가 쓴 문장이지만, 학생에게 한 말이라 로그에 찍지 않습니다.
@Entity
@Table(
    name = "messages",
    indexes = @Index(name = "idx_messages_user_id_id", columnList = "user_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {

  // 메시지를 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  // 나중에 보낸 메시지일수록 번호가 큽니다. 그래서 "최신순" 은 번호가 큰 순서입니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 메시지를 받은 사람(users.id). 다른 사람의 메시지가 섞이지 않게 하는 기준입니다.
  @Column(nullable = false)
  private Long userId;

  // 메시지 종류: CARROT(당근) · NUDGE(넛지) · STICK(채찍) · EVENT(이벤트 알림) · RECOVERY(회복)
  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private MessageKind kind;

  // 메시지 내용. 예: "계획 시간 10분 지났어. 지금 25분만 해 보자" (FE 의 text)
  @Column(nullable = false, length = 500)
  private String text;

  // 메시지를 보낸(= 저장한) 시각
  @Column(nullable = false, updatable = false)
  private Instant sentAt;

  // 처음 읽은 시각. 아직 안 읽었으면 비어 있습니다(null).
  // '메시지를 읽고 타이머를 켜기까지 걸린 시간' 분석(#47)이 이 값을 씁니다.
  private Instant readAt;

  public Message(Long userId, MessageKind kind, String text, Instant sentAt) {
    this.userId = userId;
    this.kind = kind;
    this.text = text;
    this.sentAt = sentAt;
  }

  // 읽었다고 표시합니다. 이미 읽은 메시지는 처음 읽은 시각을 그대로 둡니다. (다시 눌러도 분석 값이 바뀌지 않게)
  public void markRead(Instant now) {
    if (readAt == null) {
      readAt = now;
    }
  }

  // 읽었으면 true (FE 의 read)
  public boolean isRead() {
    return readAt != null;
  }
}
