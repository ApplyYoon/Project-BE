package com.ithopeschool.projectbe.domain.answercheck.dto;

import com.ithopeschool.projectbe.domain.answercheck.AnswerCheck;
import com.ithopeschool.projectbe.domain.answercheck.AnswerCheckItem;
import com.ithopeschool.projectbe.domain.answercheck.AnswerCheckMode;
import com.ithopeschool.projectbe.domain.answercheck.GradeResult;
import com.ithopeschool.projectbe.domain.answercheck.MarkRead;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 사진 채점 기록 하나를 FE 에 알려 주는 모양입니다. 올리기 · 목록 · 고치기가 모두 이 모양입니다.
//   { "showHelp": false, "graded": true, "reason": null,
//     "id": "12", "date": "2026-10-24", "mode": "UNMARKED", "subjectText": "수학",
//     "counts": { "correct": 2, "wrong": 1, "unsure": 1 },
//     "items": [ { "id": "31", "number": "1", "questionText": "x + 3 = 8 일 때 x 의 값은?",
//                  "unit": "문자와 식", "myAnswer": "x = 5", "aiAnswer": "x = 5",
//                  "markRead": null, "result": "CORRECT", "correctAnswer": null,
//                  "editedByUser": false } ],
//     "createdAt": "2026-10-24T11:02:00Z" }
// - id 들은 다른 API 와 같이 글자로 보냅니다.
// - graded 가 false 면 채점하지 못한 것이고 아무것도 저장하지 않았습니다. 에러가 아니라 200 입니다.
//     reason: "NOT_PROBLEM"(문제 사진이 아님 · 못 읽음) 또는 "UNAVAILABLE"(AI 꺼짐 · 실패 · 시간 초과)
//     이때 id · subjectText · counts · items · createdAt 은 null 이고, date · mode 는 보낸 값 그대로입니다.
// - showHelp 가 true 면 사진 속 글에서 위기 표현이 감지된 것입니다. 저장하지 않았고, FE 는 도움 연결 안내를 먼저 보여 줍니다.
// - counts 는 저장하지 않고 items 의 result(지금 판정)를 세어서 만듭니다.
// - editedByUser 는 학생이 직접 고친 문제인지입니다.
// - questionText 는 AI 가 사진에서 읽은 문제 글(200자까지), unit 은 교육과정 영역 단원입니다. 못 읽었으면 null. (BE #401)
// - 누구의 기록인지(userId)는 담지 않습니다.
public record AnswerCheckResponse(
    boolean showHelp,
    boolean graded,
    String reason,
    String id,
    LocalDate date,
    AnswerCheckMode mode,
    String subjectText,
    Counts counts,
    List<Item> items,
    Instant createdAt) {

  // 채점하지 못한 이유
  public static final String NOT_PROBLEM = "NOT_PROBLEM";
  public static final String UNAVAILABLE = "UNAVAILABLE";

  // 맞음 · 틀림 · 애매 개수
  public record Counts(int correct, int wrong, int unsure) {}

  // 문제 하나
  public record Item(
      String id,
      String number,
      String questionText,
      String unit,
      String myAnswer,
      String aiAnswer,
      MarkRead markRead,
      GradeResult result,
      String correctAnswer,
      boolean editedByUser) {

    static Item from(AnswerCheckItem item) {
      return new Item(
          String.valueOf(item.getId()),
          item.getProblemNumber(),
          item.getQuestionText(),
          item.getUnit(),
          item.getMyAnswer(),
          item.getAiAnswer(),
          item.getMarkRead(),
          item.getResult(),
          item.getCorrectAnswer(),
          item.getEditedAt() != null);
    }
  }

  // 저장된 기록 하나와 그 문제들(사진 속 순서대로)
  public static AnswerCheckResponse of(AnswerCheck check, List<AnswerCheckItem> items) {
    List<Item> itemResponses = items.stream().map(Item::from).toList();
    return new AnswerCheckResponse(
        false,
        true,
        null,
        String.valueOf(check.getId()),
        check.getCheckDate(),
        check.getMode(),
        check.getSubjectText(),
        countsOf(itemResponses),
        itemResponses,
        check.getCreatedAt());
  }

  // 채점하지 못했을 때 (reason 은 NOT_PROBLEM 또는 UNAVAILABLE)
  public static AnswerCheckResponse notGraded(String reason, LocalDate date, AnswerCheckMode mode) {
    return new AnswerCheckResponse(false, false, reason, null, date, mode, null, null, null, null);
  }

  // 위기 표현이 감지됐을 때
  public static AnswerCheckResponse help(LocalDate date, AnswerCheckMode mode) {
    return new AnswerCheckResponse(true, false, null, null, date, mode, null, null, null, null);
  }

  private static Counts countsOf(List<Item> items) {
    int correct = 0;
    int wrong = 0;
    int unsure = 0;
    for (Item item : items) {
      switch (item.result()) {
        case CORRECT -> correct++;
        case WRONG -> wrong++;
        case UNSURE -> unsure++;
      }
    }
    return new Counts(correct, wrong, unsure);
  }
}
