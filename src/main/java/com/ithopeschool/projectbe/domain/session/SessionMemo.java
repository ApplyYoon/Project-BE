package com.ithopeschool.projectbe.domain.session;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 딴생각 주차장에 쓴 한 줄 메모. 딸린 테이블 session_memos 의 한 줄입니다. (FE 의 SessionMemo, 이슈 #224)
// 엔티티가 아니라 공부 기록(StudySession)에 매달린 값이라서 @Embeddable 입니다.
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionMemo {

  // 쓴 시각 (FE 의 at. at 은 이름이 너무 짧아서 DB 에서는 written_at 으로 지었습니다)
  @Column(nullable = false)
  private Instant writtenAt;

  // 한 줄 (100자까지)
  @Column(nullable = false, length = 100)
  private String text;

  public SessionMemo(Instant writtenAt, String text) {
    this.writtenAt = writtenAt;
    this.text = text;
  }
}
