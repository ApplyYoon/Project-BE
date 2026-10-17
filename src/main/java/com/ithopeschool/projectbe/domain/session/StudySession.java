package com.ithopeschool.projectbe.domain.session;

import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
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

// 공부 기록 테이블. 한 줄 = 타이머로 한 번 잰 기록 하나(시작 ~ 정지). (FE 의 StudySession)
// "내 기록을 기간으로 찾는" 일이 많아서 (user_id, started_at) 에 색인(찾기 쉽게 만든 목차)을 만들었습니다.
//
// 공부 기록(STUDY)은 과목이 꼭 있어야 하고, 노는 기록(PLAY)만 과목이 비어도 됩니다. (이슈 #13, Project#12)
// 이 규칙은 Service 에서 먼저 검사하고, 혹시 빠뜨려도 DB 가 한 번 더 막도록 검사 규칙(check)으로도 걸어 두었습니다.
@Entity
@Table(
    name = "study_sessions",
    indexes =
        @Index(name = "idx_study_sessions_user_id_started_at", columnList = "user_id, started_at"),
    check =
        @CheckConstraint(
            name = "ck_study_sessions_study_has_subject",
            constraint = "kind = 'PLAY' or subject_id is not null"))
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

  // 어떤 과목을 공부했는지(subjects.id). 이 과목도 같은 사용자의 것이어야 합니다. 노는 기록이면 비어 있습니다.
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

  // 시작 ~ 끝 사이에 일시정지해 있던 시간의 합(밀리초). 없으면 0 입니다. 순공시간에서 이만큼 뺍니다. (StudyTime)
  @Column(nullable = false)
  private long pausedMs;

  // 어느 계획을 하려고 켠 타이머인지(study_plans.id). 계획 없이 켰으면 비어 있습니다.
  private Long planId;

  // 시작할 때 약속한 목표 시간(분). 목표 없이 켰으면 비어 있습니다. 나중에 계획을 고쳐도 이 값은 그대로입니다.
  private Integer targetMinutes;

  // 목표 전에 그만뒀을 때 고른 이유. 체크인의 "못 채운 이유" 7가지와 같습니다. 안 골랐으면 비어 있습니다.
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private MissReason stopReason;

  // 공부 기록(STUDY)인지 노는 기록(PLAY)인지. 정해진 두 값만 들어갑니다.
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private PlanKind kind;

  // 일시정지 시간·계획·목표·이유가 없는 공부 기록을 만듭니다. (테스트와 예전 코드가 쓰는 짧은 모양)
  public StudySession(
      Long userId, Long subjectId, Instant startedAt, Instant endedAt, int pauseCount) {
    this(userId, subjectId, startedAt, endedAt, pauseCount, 0, null, null, null, PlanKind.STUDY);
  }

  public StudySession(
      Long userId,
      Long subjectId,
      Instant startedAt,
      Instant endedAt,
      int pauseCount,
      long pausedMs,
      Long planId,
      Integer targetMinutes,
      MissReason stopReason,
      PlanKind kind) {
    this.userId = userId;
    this.subjectId = subjectId;
    this.startedAt = startedAt;
    this.endedAt = endedAt;
    this.pauseCount = pauseCount;
    this.pausedMs = pausedMs;
    this.planId = planId;
    this.targetMinutes = targetMinutes;
    this.stopReason = stopReason;
    this.kind = kind;
  }
}
