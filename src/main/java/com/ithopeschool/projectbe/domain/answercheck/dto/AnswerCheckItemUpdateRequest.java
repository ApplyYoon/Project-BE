package com.ithopeschool.projectbe.domain.answercheck.dto;

import com.ithopeschool.projectbe.domain.answercheck.AnswerCheckItem;
import com.ithopeschool.projectbe.domain.answercheck.GradeResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 문제 하나의 판정을 학생이 고치는 요청입니다.   { "result": "CORRECT", "correctAnswer": "x = 7" }
// - result: CORRECT(맞음) · WRONG(틀림) · UNSURE(애매) 중 하나
// - correctAnswer: (선택) 학생이 적은 정답. 100자까지. 비우면 지웁니다.
public record AnswerCheckItemUpdateRequest(
    @NotNull(message = "판정(CORRECT · WRONG · UNSURE)을 골라 주세요") GradeResult result,
    @Size(max = AnswerCheckItem.MAX_ANSWER_LENGTH, message = "정답은 100자까지 쓸 수 있어요")
        String correctAnswer) {

  // 값이 들어오는 순간에 정답의 앞뒤 공백을 지우고, 비었으면 없는 것(null)으로 둡니다.
  public AnswerCheckItemUpdateRequest {
    correctAnswer = correctAnswer == null || correctAnswer.isBlank() ? null : correctAnswer.trim();
  }
}
