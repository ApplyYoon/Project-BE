package com.ithopeschool.projectbe.domain.exam.dto;

import com.ithopeschool.projectbe.domain.exam.Exam;
import com.ithopeschool.projectbe.domain.exam.ExamScore;
import java.time.LocalDate;
import java.util.List;

// 시험 하나를 FE 에 알려 주는 모양입니다. FE 의 Exam 과 같은 이름을 씁니다.
//   { "id": "7", "type": "mock", "name": "10월 모의고사", "date": "2026-10-20",
//     "subjectScores": [ { "subjectId": "3", "expectedScore": 85, "actualScore": null } ] }
// - id 는 FE 타입이 글자(string)라서 숫자를 글자로 바꿔 보냅니다.
// - type 은 소문자(mock / school / quiz / etc)입니다.
// - 과목 점수는 적은 순서대로 담깁니다.
// - 시험 후 회고(reflection)는 이슈 #17 에서 만들어서 아직 없습니다.
// - 누구의 시험인지(userId)는 담지 않습니다.
public record ExamResponse(
    String id,
    String type,
    String name,
    LocalDate date,
    List<ExamSubjectScoreResponse> subjectScores) {

  // 시험 한 줄과, 그 시험의 점수들(적은 순서)로 응답을 만듭니다.
  public static ExamResponse from(Exam exam, List<ExamScore> scores) {
    return new ExamResponse(
        String.valueOf(exam.getId()),
        exam.getType().toValue(),
        exam.getName(),
        exam.getExamDate(),
        scores.stream().map(ExamSubjectScoreResponse::from).toList());
  }
}
