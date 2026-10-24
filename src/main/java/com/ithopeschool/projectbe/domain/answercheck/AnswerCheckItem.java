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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 사진 채점의 문제 결과 테이블. 한 줄 = 채점 기록(answer_checks) 하나 안의 문제 하나. (2026-10-24, 학생이 설계)
//
// AI 의 처음 판정(aiResult)은 바꾸지 않고 남깁니다. 학생이 고치면 result 와 correctAnswer 만 바뀌고 editedAt 이 찹니다.
// answerCheckId 는 다른 테이블처럼 번호만 적습니다. (DB 외래 키 제약 없음, docs/DB.md "그림 읽는 법")
// user_id 가 따로 있어서 탈퇴하면 이 줄도 같이 지워집니다. (AccountService)
@Entity
// 기록 여러 개의 문제를 한 번에 꺼내서 (user_id, answer_check_id) 색인을 둡니다.
@Table(
    name = "answer_check_items",
    indexes =
        @Index(
            name = "idx_answer_check_items_user_id_answer_check_id",
            columnList = "user_id, answer_check_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnswerCheckItem {

  // 문제 번호의 최대 글자 수 (예: "2", "3-1", "서술형 2")
  public static final int MAX_NUMBER_LENGTH = 20;
  // 학생 답 · AI 답 · 학생이 적은 정답의 최대 글자 수 (DB 칸 길이와 요청 검사가 같은 값을 씁니다)
  public static final int MAX_ANSWER_LENGTH = 100;

  // 줄을 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 어느 채점 기록의 문제인지 (answer_checks.id)
  @Column(nullable = false)
  private Long answerCheckId;

  // 이 줄의 주인(users.id). 남의 문제를 고치지 못하게 하는 기준입니다.
  @Column(nullable = false)
  private Long userId;

  // 사진 속 순서 (0 부터). 화면에 이 순서대로 보여 줍니다.
  @Column(nullable = false)
  private Integer sortOrder;

  // 문제 번호 (예: "2", "3-1"). 글자로 둡니다.
  @Column(nullable = false, length = MAX_NUMBER_LENGTH)
  private String problemNumber;

  // AI 가 읽은 학생 답. 못 읽었으면 비어 있습니다.
  @Column(length = MAX_ANSWER_LENGTH)
  private String myAnswer;

  // AI 가 직접 푼 답 ("아직 안 했어요" UNMARKED 모드에서만). 그 밖에는 비어 있습니다.
  @Column(length = MAX_ANSWER_LENGTH)
  private String aiAnswer;

  // AI 가 읽은 학생의 채점 표시 O · X ("채점했어요" MARKED 모드에서만). 못 읽었으면 비어 있습니다.
  @Enumerated(EnumType.STRING)
  @Column(length = 1)
  private MarkRead markRead;

  // AI 의 처음 판정. 학생이 고쳐도 바뀌지 않습니다.
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private GradeResult aiResult;

  // 지금 판정. 처음에는 aiResult 와 같고, 학생이 고치면 바뀝니다. 맞음 · 틀림 개수는 이 값으로 셉니다.
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private GradeResult result;

  // 학생이 직접 적은 정답. 적지 않았으면 비어 있습니다.
  @Column(length = MAX_ANSWER_LENGTH)
  private String correctAnswer;

  // 학생이 직접 고친 시각. 비어 있으면 AI 판정 그대로이고, 값이 있으면 "직접 고침" 입니다.
  private Instant editedAt;

  public AnswerCheckItem(
      Long answerCheckId,
      Long userId,
      Integer sortOrder,
      String problemNumber,
      String myAnswer,
      String aiAnswer,
      MarkRead markRead,
      GradeResult aiResult) {
    this.answerCheckId = answerCheckId;
    this.userId = userId;
    this.sortOrder = sortOrder;
    this.problemNumber = problemNumber;
    this.myAnswer = myAnswer;
    this.aiAnswer = aiAnswer;
    this.markRead = markRead;
    this.aiResult = aiResult;
    // 처음 판정은 AI 판정과 같습니다.
    this.result = aiResult;
  }

  // 학생이 판정을 고칩니다. AI 의 처음 판정(aiResult)은 그대로 둡니다.
  public void edit(GradeResult newResult, String newCorrectAnswer, Instant editedAt) {
    this.result = newResult;
    this.correctAnswer = newCorrectAnswer;
    this.editedAt = editedAt;
  }
}
