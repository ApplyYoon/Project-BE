package com.ithopeschool.projectbe.domain.exam.dto;

import com.ithopeschool.projectbe.domain.exam.ExamScore;

// 시험 하나 안의 과목 하나의 점수를 FE 에 알려 주는 모양입니다. FE 의 ExamSubjectScore 와 같은 이름을 씁니다.
//   { "subjectId": "3", "expectedScore": 85, "actualScore": null }
// - subjectId 는 FE 타입이 글자(string)라서 숫자를 글자로 바꿔 보냅니다.
// - actualScore 는 시험 후에 적는 값이라 아직 안 적었으면 null 입니다.
// - 누구의 점수인지(userId)는 담지 않습니다.
public record ExamSubjectScoreResponse(String subjectId, int expectedScore, Integer actualScore) {

  public static ExamSubjectScoreResponse from(ExamScore score) {
    return new ExamSubjectScoreResponse(
        String.valueOf(score.getSubjectId()), score.getExpectedScore(), score.getActualScore());
  }
}
