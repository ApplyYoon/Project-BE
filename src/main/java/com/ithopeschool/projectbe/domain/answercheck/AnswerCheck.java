package com.ithopeschool.projectbe.domain.answercheck;

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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 사진 채점 기록 테이블. 한 줄 = 학생이 문제집 사진을 한 번 올려 채점한 기록 하나. (2026-10-24, 학생이 설계)
// 문제 하나하나의 결과는 answer_check_items(AnswerCheckItem)에 한 줄씩 있습니다.
// 맞음 · 틀림 개수는 저장하지 않고 문제 결과를 세어서 보여 줍니다. (학생이 고치면 개수도 바로 바뀌게)
//
// ⚠️ 사진은 저장하지 않습니다. 메모리에서 OpenAI 로 보내고 버리며, 결과만 남깁니다. (AnswerCheckService)
@Entity
// 목록은 늘 "내 기록 · 그날" 로 찾아서 (user_id, check_date) 색인을 둡니다. PC 가 3초마다 부릅니다.
@Table(
    name = "answer_checks",
    indexes =
        @Index(name = "idx_answer_checks_user_id_check_date", columnList = "user_id, check_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnswerCheck {

  // AI 가 읽은 과목 이름의 최대 글자 수 (DB 칸 길이와 같은 값)
  public static final int MAX_SUBJECT_LENGTH = 30;

  // 줄을 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 기록의 주인(users.id)
  @Column(nullable = false)
  private Long userId;

  // 어느 날 공부한 것인지. 학생이 올릴 때 보낸 날짜(보통 오늘)입니다.
  @Column(nullable = false)
  private LocalDate checkDate;

  // 채점 모드 (MARKED 채점했어요 · UNMARKED 아직 안 했어요)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private AnswerCheckMode mode;

  // AI 가 사진에서 읽은 과목 (예: 수학). 못 읽었으면 비어 있습니다.
  @Column(length = MAX_SUBJECT_LENGTH)
  private String subjectText;

  // 채점한(사진을 올린) 시각
  @Column(nullable = false)
  private Instant createdAt;

  public AnswerCheck(
      Long userId,
      LocalDate checkDate,
      AnswerCheckMode mode,
      String subjectText,
      Instant createdAt) {
    this.userId = userId;
    this.checkDate = checkDate;
    this.mode = mode;
    this.subjectText = subjectText;
    this.createdAt = createdAt;
  }
}
