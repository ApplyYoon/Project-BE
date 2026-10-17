package com.ithopeschool.projectbe.domain.call.dto;

import com.ithopeschool.projectbe.domain.safety.HelpLine;
import java.util.List;

// 상담 채팅에서 AI 코치의 말을 돌려주는 모양입니다. (이슈 #238)
//   { "chatId": "12", "reply": "어제 순공 1시간 40분이었어. 어땠어?", "showHelp": false, "helpLines": [] }
//
// showHelp 가 true 면 학생의 말에서 위기 표현이 감지된 것입니다. reply 는 정해 둔 안내 문장이고,
// helpLines 에 도움받을 곳(청소년상담 1388 · 자살예방상담 109)이 들어 있습니다. 이 채팅은 거기서 끝납니다.
public record ChatReplyResponse(
    String chatId, String reply, boolean showHelp, List<HelpLine> helpLines) {

  // 보통의 답
  public static ChatReplyResponse of(Long chatId, String reply) {
    return of(String.valueOf(chatId), reply);
  }

  // 보통의 답 (대화 번호가 글자인 '목표 만들기' 대화, 이슈 #239)
  public static ChatReplyResponse of(String chatId, String reply) {
    return new ChatReplyResponse(chatId, reply, false, List.of());
  }

  // 위기 표현이 감지됐을 때의 답
  public static ChatReplyResponse crisis(Long chatId, String reply) {
    return crisis(String.valueOf(chatId), reply);
  }

  // 위기 표현이 감지됐을 때의 답 (대화 번호가 글자인 '목표 만들기' 대화)
  public static ChatReplyResponse crisis(String chatId, String reply) {
    return new ChatReplyResponse(chatId, reply, true, HelpLine.ALL);
  }
}
