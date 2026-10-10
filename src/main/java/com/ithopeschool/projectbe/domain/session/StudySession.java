package com.ithopeschool.projectbe.domain.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 공부 기록 테이블. 한 줄 = 타이머로 한 번 공부한 기록 하나(시작 ~ 정지). (FE 의 StudySession)
// "내 기록을 기간으로 찾는" 일이 많아서 (user_id, started_at) 에 색인(찾기 쉽게 만든 목차)을 만들었습니다.
@Entity
@Table(
    name = "study_sessions",
    indexes =
        @Index(name = "idx_study_sessions_user_id_started_at", columnList = "user_id, started_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudySession {

  // 기록을 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 기록의 주인(users.id). 다른 사람의 기록이 섞이지 않게 하는 기준입니다.
  @Column(nullable = false)
  private Long userId;

  // 어떤 과목을 공부했는지(subjects.id). 이 과목도 같은 사용자의 것이어야 합니다.
  @Column(nullable = false)
  private Long subjectId;

  // 공부를 시작한 시각 (FE 는 Date.now() 숫자로 보내고, 서버가 Instant 로 바꿉니다)
  @Column(nullable = false)
  private Instant startedAt;

  // 공부를 끝낸 시각. 시작보다 뒤여야 합니다.
  @Column(nullable = false)
  private Instant endedAt;

  // 이 기록 동안 일시정지를 누른 횟수. 안 눌렀으면 0 입니다. (기획서 v2 의 집중 지속 분석에 씁니다)
  @Column(nullable = false)
  private int pauseCount;

  public StudySession(
      Long userId, Long subjectId, Instant startedAt, Instant endedAt, int pauseCount) {
    this.userId = userId;
    this.subjectId = subjectId;
    this.startedAt = startedAt;
    this.endedAt = endedAt;
    this.pauseCount = pauseCount;
  }
}
