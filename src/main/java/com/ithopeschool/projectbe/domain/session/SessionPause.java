package com.ithopeschool.projectbe.domain.session;

import com.ithopeschool.projectbe.domain.session.SessionTags.PauseTag;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 일시정지 한 번. 딸린 테이블 session_pauses 의 한 줄입니다. (FE 의 SessionPause, 이슈 #224)
// 엔티티가 아니라 공부 기록(StudySession)에 매달린 값이라서 @Embeddable 입니다. 그래서 고유 번호(id)가 없습니다.
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionPause {

  // 일시정지를 누른 시각
  @Column(nullable = false)
  private Instant startedAt;

  // 다시 시작한 시각. 일시정지한 채로 끝냈으면 세션이 끝난 시각
  @Column(nullable = false)
  private Instant endedAt;

  // 이유 (화장실 · 물/간식 · 폰 확인 · 딴생각 · 막힘 · 외부 방해 · 기타)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15)
  private PauseTag tag;

  // '기타' 를 골랐을 때 쓴 글 (100자까지). 안 썼으면 비어 있습니다.
  @Column(length = 100)
  private String note;

  public SessionPause(Instant startedAt, Instant endedAt, PauseTag tag, String note) {
    this.startedAt = startedAt;
    this.endedAt = endedAt;
    this.tag = tag;
    this.note = note;
  }

  // 이 일시정지의 길이(밀리초). 끝이 시작보다 빠른 잘못된 값은 0 으로 칩니다. (FE summarizePauses 와 같음)
  public long millis() {
    return Math.max(0, endedAt.toEpochMilli() - startedAt.toEpochMilli());
  }
}
