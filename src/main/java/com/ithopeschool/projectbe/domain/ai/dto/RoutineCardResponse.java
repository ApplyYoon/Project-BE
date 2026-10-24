package com.ithopeschool.projectbe.domain.ai.dto;

import com.ithopeschool.projectbe.domain.ai.RoutineCardDraft;
import java.util.ArrayList;
import java.util.List;

// 루틴 카드 제안의 결과입니다. 아무것도 저장하지 않습니다. 학생이 고른 카드만 FE 가 카드로 저장합니다. (이슈 #239)
//   { "showHelp": false,
//     "cards": [ { "title": "영단어 30쪽", "repeatDays": [1, 2, 3, 4, 5], "startTime": "19:00",
//                  "durationMinutes": 50, "checklist": ["폰을 다른 방에 두고 오기", "30쪽 외우기"] } ] }
// - 카드 칸 이름은 카드 API(#222)의 같은 칸과 같습니다. repeatDays 는 0 = 일요일 … 6 = 토요일.
// - cards 가 null 이면 AI 가 만들지 못한 것입니다. (AI 꺼짐 · 실패 · 쓸 만한 카드가 2장보다 적음)
//   에러가 아니라 200 이고, FE 는 기본 제안 2장을 씁니다.
// - showHelp 가 true 면 글에서 위기 표현이 감지된 것입니다. cards 는 null 이고, FE 는 도움 연결 안내를 먼저 보여 줍니다.
public record RoutineCardResponse(boolean showHelp, List<Suggestion> cards) {

  // 제안 카드 한 장
  public record Suggestion(
      String title,
      List<Integer> repeatDays,
      String startTime,
      int durationMinutes,
      List<String> checklist) {}

  // 검사를 통과한 카드들 (만들지 못했으면 null)
  public static RoutineCardResponse of(List<RoutineCardDraft.RoutineCard> cards) {
    if (cards == null) {
      return new RoutineCardResponse(false, null);
    }
    List<Suggestion> suggestions = new ArrayList<>();
    for (RoutineCardDraft.RoutineCard card : cards) {
      suggestions.add(
          new Suggestion(
              card.title(),
              card.slot().repeatDays(),
              card.slot().startTime(),
              card.slot().durationMinutes(),
              card.checklist()));
    }
    return new RoutineCardResponse(false, suggestions);
  }

  // 위기 표현이 감지됐을 때의 응답입니다. AI 를 부르지 않습니다.
  public static RoutineCardResponse help() {
    return new RoutineCardResponse(true, null);
  }
}
