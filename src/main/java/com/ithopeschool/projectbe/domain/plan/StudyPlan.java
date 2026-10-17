package com.ithopeschool.projectbe.domain.plan;

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
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 공부 계획 테이블. 한 줄 = 하루의 계획 하나 (공부 계획 또는 노는 계획). (FE 의 StudyPlan)
// "내 계획을 날짜로 찾는" 일이 가장 많아서 (user_id, plan_date) 에 색인(찾기 쉽게 만든 목차)을 만들었습니다.
//
// 공부 계획(STUDY)은 과목이 꼭 있어야 하고, 노는 계획(PLAY)만 과목이 비어도 됩니다. (Project#12)
// 이 규칙은 Service 에서 먼저 검사하고, 혹시 빠뜨려도 DB 가 한 번 더 막도록 검사 규칙(check)으로도 걸어 두었습니다.
@Entity
@Table(
    name = "study_plans",
    indexes = @Index(name = "idx_study_plans_user_id_plan_date", columnList = "user_id, plan_date"),
    check =
        @CheckConstraint(
            name = "ck_study_plans_study_has_subject",
            constraint = "kind = 'PLAY' or subject_id is not null"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyPlan {

  // 계획을 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 계획의 주인(users.id). 다른 사람의 계획이 섞이지 않게 하는 기준입니다.
  @Column(nullable = false)
  private Long userId;

  // 어떤 과목의 계획인지(subjects.id). 이 과목도 같은 사용자의 것이어야 합니다. 노는 계획이면 비어 있습니다.
  private Long subjectId;

  // 계획한 날짜. 예: 2026-09-28 (FE 의 date. date 는 DB 예약어라 planDate 로 지었습니다)
  @Column(nullable = false)
  private LocalDate planDate;

  // 할 일 내용. 예: "수학 문제집 3단원 풀기"
  @Column(nullable = false, length = 200)
  private String content;

  // 목표 공부 시간(분 단위). 예: 90
  @Column(nullable = false)
  private int targetMinutes;

  // 다 했으면 true, 아직이면 false. 새 계획은 항상 false 로 시작합니다.
  @Column(nullable = false)
  private boolean done;

  // 시작할 시각. 예: 19:00 (몇 시 몇 분만, 날짜는 planDate). 안 정했으면 비어 있습니다. ("시간 미정")
  private LocalTime startTime;

  // 공부 계획(STUDY)인지 노는 계획(PLAY)인지. 정해진 두 값만 들어갑니다.
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private PlanKind kind;

  // 어떤 루틴이 만든 계획인지(루틴 번호). 직접 만든 계획이면 비어 있습니다.
  // 원본 루틴을 지워도 이 번호와 계획은 유지합니다.
  private Long routineId;

  // 시작 시각을 정하지 않은 공부 계획을 만듭니다. (테스트와 예전 코드가 쓰는 짧은 모양)
  public StudyPlan(
      Long userId, Long subjectId, LocalDate planDate, String content, int targetMinutes) {
    this(userId, subjectId, planDate, content, targetMinutes, null, PlanKind.STUDY);
  }

  public StudyPlan(
      Long userId,
      Long subjectId,
      LocalDate planDate,
      String content,
      int targetMinutes,
      LocalTime startTime,
      PlanKind kind) {
    this.userId = userId;
    this.subjectId = subjectId;
    this.planDate = planDate;
    this.content = content;
    this.targetMinutes = targetMinutes;
    this.done = false;
    this.startTime = startTime;
    this.kind = kind;
  }

  public void markRoutine(Long routineId) {
    this.routineId = routineId;
  }

  // 할 일 내용을 바꿉니다.
  public void changeContent(String content) {
    this.content = content;
  }

  // 목표 공부 시간(분)을 바꿉니다.
  public void changeTargetMinutes(int targetMinutes) {
    this.targetMinutes = targetMinutes;
  }

  // 다 했는지를 바꿉니다. (완료 체크 / 체크 풀기)
  public void changeDone(boolean done) {
    this.done = done;
  }

  // 시작 시각을 바꿉니다. null 이면 "시간 미정" 이 됩니다.
  public void changeStartTime(LocalTime startTime) {
    this.startTime = startTime;
  }

  // 종류와 과목을 함께 바꿉니다. 둘은 같이 맞아야 해서(공부 계획은 과목 필수) 한 번에 바꿉니다.
  public void changeKindAndSubject(PlanKind kind, Long subjectId) {
    this.kind = kind;
    this.subjectId = subjectId;
  }
}
