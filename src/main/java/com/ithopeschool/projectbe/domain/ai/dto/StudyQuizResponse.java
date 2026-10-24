package com.ithopeschool.projectbe.domain.ai.dto;

import com.ithopeschool.projectbe.domain.ai.StudyQuiz;
import java.util.List;

// "확인 문제" 의 결과입니다. 아무것도 저장하지 않습니다. (사진 · 자료 글도 저장하지 않음)
//   { "showHelp": false,
//     "questions": [ { "question": "lim (n→∞) 1/n 의 값은?", "choices": ["0", "1", "∞", "없다"],
//                      "answerIndex": 0, "explanation": "n 이 한없이 커지면 1/n 은 0 에 한없이 가까워져." } ],
//     "reason": null }
// - questions 가 null 이면 만들지 못한 것입니다. (AI 꺼짐 · 실패 · 쓸 문제 없음) 에러가 아니라 200 입니다.
// - reason 이 "NOT_STUDY" 면 사진이나 주제가 공부 내용이 아니라서 문제를 내지 않은 것입니다. FE 는 다른 사진을 부탁합니다.
// - showHelp 가 true 면 주제 · 체크리스트 · 자료 글 · 사진 속 글에서 위기 표현이 감지된 것입니다.
//
// ⚠️ 정답(answerIndex)과 해설이 응답에 들어 있습니다. 채점은 FE 가 기기에서 합니다.
//    점수를 매기는 시험이 아니라 "제대로 이해했나" 스스로 확인하는 기능이라 괜찮습니다.
public record StudyQuizResponse(
    boolean showHelp, List<StudyQuiz.Question> questions, String reason) {

  // 공부 내용이 아닐 때의 reason
  public static final String NOT_STUDY = "NOT_STUDY";

  // 검사를 통과한 문제들. 하나도 없으면(null) 만들지 못한 것과 같습니다.
  public static StudyQuizResponse of(List<StudyQuiz.Question> questions) {
    return new StudyQuizResponse(false, questions, null);
  }

  // 만들지 못했을 때 (AI 꺼짐 · 실패)
  public static StudyQuizResponse notMade() {
    return new StudyQuizResponse(false, null, null);
  }

  // 사진이나 주제가 공부 내용이 아닐 때
  public static StudyQuizResponse notStudy() {
    return new StudyQuizResponse(false, null, NOT_STUDY);
  }

  // 위기 표현이 감지됐을 때. AI 를 부르지 않았거나, AI 가 사진 속 글에서 찾았습니다.
  public static StudyQuizResponse help() {
    return new StudyQuizResponse(true, null, null);
  }
}
