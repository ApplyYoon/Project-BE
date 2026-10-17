package com.ithopeschool.projectbe.domain.exam;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 시험 테이블. 한 줄 = 시험 하나. (FE 의 Exam) (이슈 #16)
// 과목별 점수는 여기에 넣지 않고 exam_scores 테이블에 따로 적습니다.
// 시험마다 과목 수가 달라서입니다. (모의고사는 3과목, 쪽지시험은 1과목)
@Entity
@Table(name = "exams")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Exam {

  // 시험을 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 시험의 주인(users.id). 다른 사람의 시험이 섞이지 않게 하는 기준입니다.
  @Column(nullable = false)
  private Long userId;

  // 시험 이름. 예: "10월 모의고사"
  @Column(nullable = false, length = 50)
  private String name;

  // 시험 종류. 정해진 네 값만 들어갑니다. EnumType.STRING 이라 "MOCK" 같은 글자로 저장됩니다.
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private ExamType type;

  // 시험 날짜. 예: 2026-10-20 (FE 의 date. date 는 DB 예약어라 examDate 로 지었습니다)
  @Column(nullable = false)
  private LocalDate examDate;

  public Exam(Long userId, String name, ExamType type, LocalDate examDate) {
    this.userId = userId;
    this.name = name;
    this.type = type;
    this.examDate = examDate;
  }
}
