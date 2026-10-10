package com.ithopeschool.projectbe.domain.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 공부 계획 테이블. 한 줄 = 하루의 공부 계획 하나. (FE 의 StudyPlan)
// "내 계획을 날짜로 찾는" 일이 가장 많아서 (user_id, plan_date) 에 색인(찾기 쉽게 만든 목차)을 만들었습니다.
@Entity
@Table(
    name = "study_plans",
    indexes = @Index(name = "idx_study_plans_user_id_plan_date", columnList = "user_id, plan_date"))
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

  // 어떤 과목의 계획인지(subjects.id). 이 과목도 같은 사용자의 것이어야 합니다.
  @Column(nullable = false)
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

  public StudyPlan(
      Long userId, Long subjectId, LocalDate planDate, String content, int targetMinutes) {
    this.userId = userId;
    this.subjectId = subjectId;
    this.planDate = planDate;
    this.content = content;
    this.targetMinutes = targetMinutes;
    this.done = false;
  }
}
