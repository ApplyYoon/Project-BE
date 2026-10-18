package com.ithopeschool.projectbe.domain.call.dto;

import com.ithopeschool.projectbe.domain.safety.HelpLine;
import java.util.List;

// 상담 채팅에서 AI 코치의 말을 돌려주는 모양입니다. (이슈 #238)
//   { "chatId": "12", "reply": "어제 순공 1시간 40분이었어. 어땠어?", "showHelp": false, "helpLines": [] }
//
// showHelp 가 true 면 학생의 말에서 위기 표현이 감지된 것입니다. reply 는 정해 둔 안내 문장이고,
// helpLines 에 도움받을 곳(청소년상담 1388 · 자살예방상담 109)이 들어 있습니다.
// 상담 채팅은 거기서 끝나지 않고 이어서 이야기할 수 있습니다. (2026-10-18) '목표 만들기' 대화는 거기서 끝납니다.
//
// quickReplies · action (2026-10-18): AI 가 답과 같이 준 "학생이 누를 짧은 답"(3개까지)과 "행동 하나" 입니다.
//   { …, "quickReplies": ["좋아, 해 볼게", "10분만 할래"], "action": { "type": "shrink_today", "minutes":
// 10, "title": null } }
//   action 이 null 이면 행동 버튼이 없습니다. 서버가 모양 · 길이 · 말투를 검사한 뒤에만 넣습니다. (ChatService.cleanTurn)
//   make_plan 만 date(마감 날 "YYYY-MM-DD")를 씁니다. 다른 행동은 date 가 null 입니다. (2026-10-18)
//   { …, "action": { "type": "make_plan", "minutes": null, "title": "중간고사 미적분", "date":
// "2026-11-02" } }
//
// tags (2026-10-18 "코치랑 이야기하기"): "공부 끝 후기" · "못 한 카드" 주제로 연 채팅에서 AI 가 학생 말에서 짐작한 이유 값입니다.
//   { …, "tags": ["phone", "tired"] }
//   값은 FE 의 ShortfallReason(후기) · CardFailCause(못 한 카드)와 같은 글자이고, 서버가 그 주제의 값만 남깁니다. (ChatTopic)
//   FE 는 "이유: 폰 · 피곤 — 맞아?" 로 학생에게 확인만 받습니다. 다른 주제 · 주제 없는 채팅은 늘 빈 목록입니다.
public record ChatReplyResponse(
    String chatId,
    String reply,
    boolean showHelp,
    List<HelpLine> helpLines,
    List<String> quickReplies,
    Action action,
    List<String> tags) {

  // 행동 버튼 하나. 학생이 눌렀을 때만 FE 가 실행합니다.
  //   type: start_timer(공부 시작) · shrink_today(오늘 목표를 minutes 분으로 줄여 시작) · create_card(새 할 일 title,
  // minutes 분)
  //         make_plan(긴 계획 짜기: 목표 title, 마감 날 date → FE 가 /ai/long-plan) ·
  //         start_quiz(확인 문제: 주제 title → FE 가 /ai/quiz)
  public record Action(String type, Integer minutes, String title, String date) {

    // date 가 없는 행동 (start_timer · shrink_today · create_card · start_quiz)
    public Action(String type, Integer minutes, String title) {
      this(type, minutes, title, null);
    }
  }

  // 보통의 답
  public static ChatReplyResponse of(Long chatId, String reply) {
    return of(String.valueOf(chatId), reply);
  }

  // 보통의 답 (대화 번호가 글자인 '목표 만들기' 대화, 이슈 #239)
  public static ChatReplyResponse of(String chatId, String reply) {
    return new ChatReplyResponse(chatId, reply, false, List.of(), List.of(), null, List.of());
  }

  // 빠른 답 · 행동 버튼이 붙은 보통의 답 (상담 채팅)
  public static ChatReplyResponse withButtons(
      Long chatId, String reply, List<String> quickReplies, Action action) {
    return withButtons(chatId, reply, quickReplies, action, List.of());
  }

  // 빠른 답 · 행동 버튼 · 짐작한 이유 값이 붙은 보통의 답 (주제가 있는 상담 채팅)
  public static ChatReplyResponse withButtons(
      Long chatId, String reply, List<String> quickReplies, Action action, List<String> tags) {
    return new ChatReplyResponse(
        String.valueOf(chatId), reply, false, List.of(), quickReplies, action, tags);
  }

  // 위기 표현이 감지됐을 때의 답
  public static ChatReplyResponse crisis(Long chatId, String reply) {
    return crisis(String.valueOf(chatId), reply);
  }

  // 위기 표현이 감지됐을 때의 답 (대화 번호가 글자인 '목표 만들기' 대화)
  public static ChatReplyResponse crisis(String chatId, String reply) {
    return new ChatReplyResponse(chatId, reply, true, HelpLine.ALL, List.of(), null, List.of());
  }
}
