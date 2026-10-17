package com.ithopeschool.projectbe.domain.review.dto;

import com.ithopeschool.projectbe.domain.review.Review;
import java.time.LocalDate;
import java.util.List;

// 회고 하나를 FE 에 알려 주는 모양입니다.
//   { "id": "3", "type": "exam", "examId": "7", "weekStart": null,
//     "answers": ["4", "시간 분배를 못 했다", "시간 재고 풀기"], "savedAt": 1792234800000 }
// - id · examId 는 다른 API 와 같이 글자로 보냅니다. 종류에 맞지 않는 칸(examId 또는 weekStart)은 null 입니다.
// - answers 는 질문 순서대로 3개입니다.
// - savedAt 은 마지막으로 저장한 시각(밀리초 숫자)입니다. (docs/API.md 2-2)
// - 누구의 회고인지(userId)는 담지 않습니다.
public record ReviewResponse(
    String id,
    String type,
    String examId,
    LocalDate weekStart,
    List<String> answers,
    long savedAt) {

  public static ReviewResponse from(Review review) {
    return new ReviewResponse(
        String.valueOf(review.getId()),
        review.getType().toValue(),
        review.getExamId() == null ? null : String.valueOf(review.getExamId()),
        review.getWeekStart(),
        List.of(review.getAnswer1(), review.getAnswer2(), review.getAnswer3()),
        review.getSavedAt().toEpochMilli());
  }
}
