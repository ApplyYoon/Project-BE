package com.ithopeschool.projectbe.domain.onboarding;

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
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 온보딩 설문 답 테이블. 한 줄 = 설문 하나. (이슈 #21, FE 의 OnboardingAnswers) — 학생이 설계
//
// 고칠 때마다 원래 줄을 바꾸지 않고 새 줄을 더합니다. 그래서 한 사람에게 여러 줄이 쌓입니다.
//   - 지금 내 설문 = 답한 시각(answered_at)이 가장 늦은 줄
//   - 처음 답한 자기 인식 = 그 칸이 처음으로 채워진, 답한 시각이 가장 이른 줄 (#40 '아하' 인사이트의 재료)
// 한 번 저장한 줄은 바꾸지 않아서 값을 바꾸는 메서드가 없습니다.
//
// 처음에 꼭 받는 답은 학년 · 시험 · 시험 날짜 · 하루 공부 가능 시간뿐이고, 나머지는 비어 있어도 됩니다. (FE #239)
// 한 줄에 여러 개인 답은 딸린 테이블(@ElementCollection)에 담습니다.
//   onboarding_answer_failure_causes : (onboarding_answer_id, cause)                          고른
// 것마다 한 줄
//   onboarding_answer_subject_levels : (onboarding_answer_id, subject_id, grade, score, confidence)
// 과목마다 한 줄
@Entity
@Table(
    name = "onboarding_answers",
    indexes =
        @Index(
            name = "idx_onboarding_answers_user_id_answered_at",
            columnList = "user_id, answered_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OnboardingAnswer {

  // 설문을 구분하는 고유 번호
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 누구의 설문인지 (users.id)
  @Column(nullable = false)
  private Long userId;

  // 답한 시각 (서버에 저장한 시각). 가장 처음 줄과 가장 최근 줄을 이것으로 구분합니다.
  @Column(nullable = false, updatable = false)
  private Instant answeredAt;

  // 학년 (고1 · 고2 · 고3 · N수 · 대학생)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Grade grade;

  // 목표로 하는 시험 이름 (예: 수능)
  @Column(nullable = false, length = 30)
  private String targetExam;

  // 목표 시험 날짜 (D-day 계산용)
  @Column(nullable = false)
  private LocalDate targetExamDate;

  // 목표 대학 (선택)
  @Column(length = 30)
  private String targetUniversity;

  // 목표 점수 (선택, "1등급" 처럼 글자로 적을 수 있음)
  @Column(length = 30)
  private String targetScore;

  // 평소 기상 시각 (몇 시 몇 분)
  private LocalTime wakeUpTime;

  // 평소 취침 시각 (몇 시 몇 분)
  private LocalTime sleepTime;

  // 학교 끝나는 시각
  private LocalTime schoolEndTime;

  // 학원 끝나는 시각
  private LocalTime academyEndTime;

  // 주로 공부하는 장소
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private StudyPlace studyPlace;

  // [자기 인식] 아침형 / 밤형
  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private Chronotype chronotype;

  // [자기 인식] 한 번에 집중 가능한 시간(분) (30 · 50 · 90 · 120 중 하나)
  private Integer maxFocusMinutes;

  // [자기 인식] 가장 큰 방해 요소
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private Distraction biggestDistraction;

  // [자기 인식] 하루 공부 가능 시간(분) (60 · 120 · 180 · 240 · 300 중 하나)
  @Column(nullable = false)
  private int dailyAvailableMinutes;

  // 공부하는 이유 (한 줄, 50자까지)
  @Column(length = 50)
  private String motivationReason;

  // 목표를 이루면 하고 싶은 것 (한 줄, 50자까지)
  @Column(length = 50)
  private String goalReward;

  // 과거에 계획이 무너졌던 때 (여러 개). 딸린 테이블에 하나씩 한 줄로 들어갑니다.
  @ElementCollection
  @CollectionTable(
      name = "onboarding_answer_failure_causes",
      joinColumns = @JoinColumn(name = "onboarding_answer_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "cause", length = 20, nullable = false)
  private Set<FailureCause> pastFailureCauses = EnumSet.noneOf(FailureCause.class);

  // 과목별 최근 성적·자신감 (과목 수만큼). 딸린 테이블에 과목마다 한 줄로 들어갑니다.
  @ElementCollection
  @CollectionTable(
      name = "onboarding_answer_subject_levels",
      joinColumns = @JoinColumn(name = "onboarding_answer_id"))
  private List<SubjectLevel> subjectLevels = new ArrayList<>();

  // 설문 한 줄을 만듭니다. 칸이 많아서, 값은 Service 에서 하나씩 채운 Values 로 받습니다.
  public OnboardingAnswer(Long userId, Instant answeredAt, Values values) {
    this.userId = userId;
    this.answeredAt = answeredAt;
    this.grade = values.grade;
    this.targetExam = values.targetExam;
    this.targetExamDate = values.targetExamDate;
    this.targetUniversity = values.targetUniversity;
    this.targetScore = values.targetScore;
    this.wakeUpTime = values.wakeUpTime;
    this.sleepTime = values.sleepTime;
    this.schoolEndTime = values.schoolEndTime;
    this.academyEndTime = values.academyEndTime;
    this.studyPlace = values.studyPlace;
    this.chronotype = values.chronotype;
    this.maxFocusMinutes = values.maxFocusMinutes;
    this.biggestDistraction = values.biggestDistraction;
    this.dailyAvailableMinutes = values.dailyAvailableMinutes;
    this.motivationReason = values.motivationReason;
    this.goalReward = values.goalReward;
    this.pastFailureCauses =
        values.pastFailureCauses.isEmpty()
            ? EnumSet.noneOf(FailureCause.class)
            : EnumSet.copyOf(values.pastFailureCauses);
    this.subjectLevels = new ArrayList<>(values.subjectLevels);
  }

  // 이 설문의 답을 그대로 옮겨 적은 Values. 일부만 고칠 때(PATCH) 바로 전 답에서 시작하려고 씁니다.
  public Values copyValues() {
    Values values = new Values();
    values.grade = grade;
    values.targetExam = targetExam;
    values.targetExamDate = targetExamDate;
    values.targetUniversity = targetUniversity;
    values.targetScore = targetScore;
    values.wakeUpTime = wakeUpTime;
    values.sleepTime = sleepTime;
    values.schoolEndTime = schoolEndTime;
    values.academyEndTime = academyEndTime;
    values.studyPlace = studyPlace;
    values.chronotype = chronotype;
    values.maxFocusMinutes = maxFocusMinutes;
    values.biggestDistraction = biggestDistraction;
    values.dailyAvailableMinutes = dailyAvailableMinutes;
    values.motivationReason = motivationReason;
    values.goalReward = goalReward;
    values.pastFailureCauses = EnumSet.noneOf(FailureCause.class);
    values.pastFailureCauses.addAll(pastFailureCauses);
    values.subjectLevels = new ArrayList<>(subjectLevels);
    return values;
  }

  // 설문 한 줄에 들어갈 값을 모아 두는 상자입니다. 엔티티가 아니라 DB 에 저장되지 않습니다.
  // 생성자에 칸 18개를 순서대로 넘기면 순서를 헷갈리기 쉬워서, 이름으로 하나씩 채우게 했습니다.
  public static class Values {
    public Grade grade;
    public String targetExam;
    public LocalDate targetExamDate;
    public String targetUniversity;
    public String targetScore;
    public LocalTime wakeUpTime;
    public LocalTime sleepTime;
    public LocalTime schoolEndTime;
    public LocalTime academyEndTime;
    public StudyPlace studyPlace;
    public Chronotype chronotype;
    public Integer maxFocusMinutes;
    public Distraction biggestDistraction;
    public int dailyAvailableMinutes;
    public String motivationReason;
    public String goalReward;
    public Set<FailureCause> pastFailureCauses = EnumSet.noneOf(FailureCause.class);
    public List<SubjectLevel> subjectLevels = new ArrayList<>();
  }
}
