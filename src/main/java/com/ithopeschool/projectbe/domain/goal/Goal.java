package com.ithopeschool.projectbe.domain.goal;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 목표 테이블. 한 줄 = 목표 하나 = D-day 가 있는 장기 결과. (최종 기획서 「4.7 목표와 숲」, 이슈 #221, FE 의 Goal)
// 카드는 목표를 위한 일상 행동이고, 카드의 goalId 가 이 번호를 가리킵니다.
//
// WOOP 순서로 만듭니다.
//   W 소망   : title (목표 한 줄) · dDay · 지금 점수(점수형) 또는 전체 분량(완주형)
//   O 결과   : benefit (이루면 좋은 점) · cost (안 하면 생길 일)
//   O 방해물 : obstacle (내 안의 방해물 1개. 학원 · 날씨처럼 내가 바꿀 수 없는 것은 넣지 않음)
//   P 계획   : plan (방해물이 생겼을 때의 대처)
//
// 목표값(title · dDay · currentScore · totalAmount · amountUnit)을 고치면 고치기 전 값을 history 에 남깁니다.
//   goal_target_snapshots : (goal_id, position, title, d_day, …, changed_at, reason)  고칠 때마다 한 줄
// 목표는 지우는 API 가 없습니다. 그만두면 "포기(GIVEN_UP)" 로 상태만 바꿔서 기록이 남습니다. (탈퇴하면 같이 지워짐)
// 다짐 녹음은 서버에 보내지 않습니다. (미성년자 음성, 기기에만 — 2026-10-05 결정)
@Entity
@Table(name = "goals", indexes = @Index(name = "idx_goals_user_id", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Goal {

  // 목표를 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 목표의 주인(users.id). 다른 사람의 목표가 섞이지 않게 하는 기준입니다.
  @Column(nullable = false)
  private Long userId;

  // 유형. 점수형(SCORE) / 완주형(COMPLETION). 만든 뒤에는 바꾸지 않습니다.
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private GoalKind kind;

  // W 소망 — 목표 한 줄 (예: "11월 학평 수학 3등급"). 40자까지 (FE 와 같음)
  @Column(nullable = false, length = 40)
  private String title;

  // D-day
  @Column(nullable = false)
  private LocalDate dDay;

  // 점수형: 지금 점수 · 등급 (예: "5등급", "62점"). 20자까지. 완주형이면 비어 있습니다.
  @Column(length = 20)
  private String currentScore;

  // 완주형: 전체 분량 (예: 300). 점수형이면 비어 있습니다.
  private Integer totalAmount;

  // 완주형: 분량 단위 (예: "쪽", "강"). 5자까지. 점수형이면 비어 있습니다.
  @Column(length = 5)
  private String amountUnit;

  // O 결과 — 이루면 좋은 점 (100자까지)
  @Column(nullable = false, length = 100)
  private String benefit;

  // O 결과 — 안 하면 생길 일 (100자까지)
  @Column(nullable = false, length = 100)
  private String cost;

  // O 방해물 — 내 안의 방해물 1개 (100자까지)
  @Column(nullable = false, length = 100)
  private String obstacle;

  // P 계획 — 방해물이 생겼을 때의 대처 (100자까지)
  @Column(nullable = false, length = 100)
  private String plan;

  // 상태. 진행(ACTIVE) / 완료(DONE) / 포기(GIVEN_UP)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private GoalStatus status;

  // D-day 뒤에 적은 결과 (100자까지). 아직이면 비어 있습니다.
  @Column(length = 100)
  private String result;

  // 포기했을 때 적은 이유 (100자까지). 포기하지 않았으면 비어 있습니다.
  @Column(length = 100)
  private String givenUpReason;

  // 목표값을 고친 기록 (오래된 것부터). 지우지 않고 쌓기만 합니다. (GoalTargetSnapshot 설명)
  @ElementCollection
  @CollectionTable(name = "goal_target_snapshots", joinColumns = @JoinColumn(name = "goal_id"))
  @OrderColumn(name = "position")
  private List<GoalTargetSnapshot> history = new ArrayList<>();

  // 목표를 만든 시각 (서버에 저장한 시각)
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  // 목표를 만듭니다. 상태는 진행(ACTIVE)으로 시작하고, 고친 기록은 비어 있습니다.
  // 값은 Service 가 검사를 마친 것만 넘깁니다. (점수형이면 분량 칸, 완주형이면 점수 칸은 null)
  public Goal(Long userId, GoalKind kind, Target target, Woop woop, Instant createdAt) {
    this.userId = userId;
    this.kind = kind;
    applyTarget(target);
    changeWoop(woop);
    this.status = GoalStatus.ACTIVE;
    this.createdAt = createdAt;
  }

  // 목표값 다섯 칸 묶음 (title · dDay · 지금 점수 · 전체 분량 · 단위). 고칠 때 이 다섯 칸을 함께 비교합니다.
  public record Target(
      String title, LocalDate dDay, String currentScore, Integer totalAmount, String amountUnit) {}

  // WOOP 의 글 네 칸 묶음 (이루면 좋은 점 · 안 하면 생길 일 · 방해물 · 대처). 고쳐도 기록을 남기지 않습니다.
  public record Woop(String benefit, String cost, String obstacle, String plan) {}

  // ---- 읽기 도우미 ----

  // 지금 목표값 다섯 칸
  public Target target() {
    return new Target(title, dDay, currentScore, totalAmount, amountUnit);
  }

  // 지금 WOOP 글 네 칸
  public Woop woop() {
    return new Woop(benefit, cost, obstacle, plan);
  }

  // 진행 중인 목표인지
  public boolean isActive() {
    return status == GoalStatus.ACTIVE;
  }

  // ---- 바꾸기 (Service 가 검사를 마친 값만 넘깁니다) ----

  // 목표값을 바꿉니다. 바뀌기 전 값과 이유를 history 맨 뒤에 남깁니다. FE 의 changeGoalTarget 과 같습니다.
  //   { title: "수학 3등급", history: [] } 를 "수학 4등급" 으로, 이유 "시간이 모자라서"
  //     → { title: "수학 4등급", history: [{ title: "수학 3등급", reason: "시간이 모자라서", … }] }
  public void changeTarget(Target target, String reason, Instant now) {
    history.add(
        new GoalTargetSnapshot(title, dDay, currentScore, totalAmount, amountUnit, now, reason));
    applyTarget(target);
  }

  // WOOP 글 네 칸을 바꿉니다.
  public void changeWoop(Woop woop) {
    this.benefit = woop.benefit();
    this.cost = woop.cost();
    this.obstacle = woop.obstacle();
    this.plan = woop.plan();
  }

  // 완료로 바꿉니다. 포기 이유는 지웁니다.
  public void markDone() {
    this.status = GoalStatus.DONE;
    this.givenUpReason = null;
  }

  // 포기로 바꾸고 이유를 적습니다.
  public void giveUp(String reason) {
    this.status = GoalStatus.GIVEN_UP;
    this.givenUpReason = reason;
  }

  // 다시 진행 중으로 바꿉니다. 포기 이유는 지웁니다. (진행 중 3개를 넘지 않는지는 Service 가 먼저 봅니다)
  public void reopen() {
    this.status = GoalStatus.ACTIVE;
    this.givenUpReason = null;
  }

  // 결과를 적습니다. (D-day 뒤에)
  public void changeResult(String result) {
    this.result = result;
  }

  private void applyTarget(Target target) {
    this.title = target.title();
    this.dDay = target.dDay();
    this.currentScore = target.currentScore();
    this.totalAmount = target.totalAmount();
    this.amountUnit = target.amountUnit();
  }
}
