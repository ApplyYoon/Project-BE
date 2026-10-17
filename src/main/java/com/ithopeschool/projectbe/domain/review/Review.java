package com.ithopeschool.projectbe.domain.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 회고 테이블. 한 줄 = 회고 하나 (시험 후 회고 또는 주간 회고). (이슈 #17)
//
// 두 종류를 한 테이블에 담고 type 으로 구분합니다. 둘 다 "정해진 질문 3개에 짧게 답한 것" 이라 모양이 같기 때문입니다.
// - 시험 후 회고(EXAM): exam_id 가 차 있고 week_start 는 비어 있습니다.
// - 주간 회고(WEEKLY): week_start 가 차 있고 exam_id 는 비어 있습니다.
//
// 같은 시험·같은 주에 다시 쓰면 새 줄을 만들지 않고 그 줄을 덮어씁니다.
// 그래서 (user_id, exam_id) 와 (user_id, week_start) 가 겹치지 않게 막아 두었습니다.
// (비어 있는 칸(null)끼리는 겹친다고 보지 않아서, 주간 회고 여러 개의 비어 있는 exam_id 는 서로 부딪히지 않습니다)
@Entity
@Table(
    name = "reviews",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_reviews_user_id_exam_id",
          columnNames = {"user_id", "exam_id"}),
      @UniqueConstraint(
          name = "uk_reviews_user_id_week_start",
          columnNames = {"user_id", "week_start"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

  // 회고를 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 회고의 주인(users.id). 다른 사람의 회고가 섞이지 않게 하는 기준입니다.
  @Column(nullable = false)
  private Long userId;

  // 회고 종류. EXAM(시험 후) 또는 WEEKLY(주간). EnumType.STRING 이라 "EXAM" 같은 글자로 저장됩니다.
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private ReviewType type;

  // 어느 시험의 회고인지(exams.id). 시험 후 회고에만 있고, 주간 회고는 비어 있습니다.
  private Long examId;

  // 어느 주의 회고인지. 그 주의 월요일 날짜입니다. 주간 회고에만 있고, 시험 후 회고는 비어 있습니다.
  private LocalDate weekStart;

  // ① 번 질문의 답 (200자까지). 주간 회고에서 건너뛴 질문은 빈 글자("")입니다.
  @Column(nullable = false, length = 200)
  private String answer1;

  // ② 번 질문의 답 (200자까지)
  @Column(nullable = false, length = 200)
  private String answer2;

  // ③ 번 질문의 답 (200자까지)
  @Column(nullable = false, length = 200)
  private String answer3;

  // 마지막으로 저장한 시각. 덮어쓰면 그 시각으로 바뀝니다.
  @Column(nullable = false)
  private Instant savedAt;

  public Review(
      Long userId,
      ReviewType type,
      Long examId,
      LocalDate weekStart,
      String answer1,
      String answer2,
      String answer3,
      Instant savedAt) {
    this.userId = userId;
    this.type = type;
    this.examId = examId;
    this.weekStart = weekStart;
    overwrite(answer1, answer2, answer3, savedAt);
  }

  // 같은 시험·같은 주에 다시 썼을 때, 새로 받은 답으로 세 개 모두 바꿉니다.
  public void overwrite(String answer1, String answer2, String answer3, Instant savedAt) {
    this.answer1 = answer1;
    this.answer2 = answer2;
    this.answer3 = answer3;
    this.savedAt = savedAt;
  }
}
