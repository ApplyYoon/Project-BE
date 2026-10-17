package com.ithopeschool.projectbe.domain.exam;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 시험 과목 점수 테이블. 한 줄 = 시험 하나의 과목 하나의 점수. (FE 의 ExamSubjectScore) (이슈 #16)
//   예: "1차 모의고사" 의 국어 → 예상 85점, 시험 후 실제 80점
//
// 같은 시험에 같은 과목은 한 번만 적을 수 있습니다. (exam_id, subject_id 가 같은 줄은 둘이 될 수 없음)
// 점수는 0 ~ 100 입니다. Service 와 요청 검사에서 먼저 막고, 혹시 빠뜨려도 DB 가 한 번 더 막도록 검사 규칙(check)도 걸어 두었습니다.
@Entity
@Table(
    name = "exam_scores",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_exam_scores_exam_id_subject_id",
            columnNames = {"exam_id", "subject_id"}),
    check = {
      @CheckConstraint(
          name = "ck_exam_scores_expected_score_range",
          constraint = "expected_score between 0 and 100"),
      @CheckConstraint(
          name = "ck_exam_scores_actual_score_range",
          constraint = "actual_score is null or actual_score between 0 and 100")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExamScore {

  // 점수 한 줄을 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 점수의 주인(users.id). 다른 사람의 점수가 섞이지 않게 하는 기준입니다.
  @Column(nullable = false)
  private Long userId;

  // 어느 시험의 점수인지(exams.id)
  @Column(nullable = false)
  private Long examId;

  // 어느 과목의 점수인지(subjects.id). 이 과목도 같은 사용자의 것이어야 합니다.
  @Column(nullable = false)
  private Long subjectId;

  // 시험 전에 적은 예상 점수 (0 ~ 100)
  @Column(nullable = false)
  private int expectedScore;

  // 시험 후에 적은 실제 점수 (0 ~ 100). 아직 안 적었으면 비어 있습니다.
  private Integer actualScore;

  // 예상 점수만 가진 점수를 만듭니다. 실제 점수는 시험이 끝난 뒤 changeActualScore 로 넣습니다.
  public ExamScore(Long userId, Long examId, Long subjectId, int expectedScore) {
    this.userId = userId;
    this.examId = examId;
    this.subjectId = subjectId;
    this.expectedScore = expectedScore;
  }

  // 실제 점수를 넣습니다. (@Setter 를 쓰지 않고, 바꿀 수 있는 값만 이름을 지어 메서드로 엽니다)
  public void changeActualScore(int actualScore) {
    this.actualScore = actualScore;
  }
}
