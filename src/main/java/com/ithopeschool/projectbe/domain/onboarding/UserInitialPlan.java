package com.ithopeschool.projectbe.domain.onboarding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 처음 계획 테이블. 한 줄 = 한 사람의 처음 계획. (이슈 #21, FE 의 InitialPlan, FE #240) — 학생이 설계
//
// 설문 답과 규칙이 달라서 따로 둡니다.
//   - 한 사람에 한 줄만 있습니다. (user_id 중복 불가)
//   - 온보딩을 다시 해도 바뀌지 않고, 사용자가 "다시 세우기" 를 할 때만 이 줄을 고칩니다.
// "처음의 나" 와 지금을 비교하는 기준입니다. (stats/ExpectationGap.ofInitialPlan)
//
// 클래스 이름이 InitialPlan 이 아닌 이유: routine/InitialPlan 은 첫 주 루틴 초안을 계산하는 도구라서, 이름이 겹치면 헷갈립니다.
@Entity
@Table(
    name = "initial_plans",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_initial_plans_user_id", columnNames = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserInitialPlan {

  // 처음 계획을 구분하는 고유 번호
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 누구의 처음 계획인지 (users.id). 한 사람에 한 줄입니다.
  @Column(nullable = false)
  private Long userId;

  // 처음 계획을 세운(다시 세운) 날짜 (새벽 4시에 날짜가 바뀜)
  @Column(nullable = false)
  private LocalDate createdDate;

  // 하루에 공부하기로 한 시간(분) (60 · 120 · 180 · 240 · 300 중 하나)
  @Column(nullable = false)
  private int dailyMinutes;

  // 일주일에 며칠 하기로 했는지 (3 · 5 · 6 · 7 중 하나)
  @Column(nullable = false)
  private int daysPerWeek;

  public UserInitialPlan(Long userId, LocalDate createdDate, int dailyMinutes, int daysPerWeek) {
    this.userId = userId;
    this.createdDate = createdDate;
    this.dailyMinutes = dailyMinutes;
    this.daysPerWeek = daysPerWeek;
  }

  // "다시 세우기": 세운 날짜와 약속을 새 값으로 바꿉니다. 이 메서드 말고는 값을 바꿀 길이 없습니다.
  public void remake(LocalDate createdDate, int dailyMinutes, int daysPerWeek) {
    this.createdDate = createdDate;
    this.dailyMinutes = dailyMinutes;
    this.daysPerWeek = daysPerWeek;
  }
}
