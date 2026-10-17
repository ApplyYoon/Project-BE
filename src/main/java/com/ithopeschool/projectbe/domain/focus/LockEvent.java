package com.ithopeschool.projectbe.domain.focus;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "lock_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LockEvent {
  // 해제 기록 번호
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 기록의 주인
  @Column(nullable = false)
  private Long userId;

  // 해제 시각
  @Column(nullable = false)
  private Instant occurredAt;

  // 앱 이름
  @Column(nullable = false, length = 30)
  private String appName;

  // 한 줄 이유
  @Column(nullable = false, length = 50)
  private String reason;

  public LockEvent(Long userId, Instant occurredAt, String appName, String reason) {
    this.userId = userId;
    this.occurredAt = occurredAt;
    this.appName = appName;
    this.reason = reason;
  }
}
