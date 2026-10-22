package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest;
import java.util.ArrayList;
import java.util.List;

// AI 채팅의 답이 직전에 한 말을 되풀이하지 않게 돕는 도구입니다. (팀 점검 「채팅 수정 3」)
// 학생이 "피곤해" 다음에 "미적분 때문에 불안해" 라고 했는데 두 번 다 "오늘은 10분만 해도 충분해" 로 시작하면,
// 학생은 자기 말을 읽지 않았다고 느낍니다. 그래서
//   1. 말 보내기마다 직전 AI 답 3개의 첫 문장을 지시 끝에 [이미 한 말] 칸으로 붙이고 (section)
//   2. 새 답이 직전 AI 답 3개 중 하나와 같은 구절이면 한 번만 다시 받습니다. (repeats, ChatService.send)
public final class RepeatCheck {

  // 직전 AI 답을 몇 개까지 볼지
  static final int RECENT_COUNT = 3;
  // 이 글자 수 이상 이어서 같으면 같은 구절로 봅니다. (공백 · 문장부호를 지운 뒤 센 글자 수)
  static final int SAME_LENGTH = 12;
  // [이미 한 말] 칸에 넣는 첫 문장은 이 글자 수까지 자릅니다. (짧은 구절이면 충분합니다)
  static final int MAX_PHRASE_LENGTH = 40;

  private RepeatCheck() {}

  // 대화에서 AI(ai) 줄의 마지막 3개를 앞에서부터 순서대로 꺼냅니다.
  //   [ai "A", me "x", ai "B", me "y", ai "C", me "z", ai "D", me "w"]  →  ["B", "C", "D"]
  static List<String> recentAiReplies(List<CallFinishRequest.Line> lines) {
    List<String> aiTexts = new ArrayList<>();
    for (CallFinishRequest.Line line : lines) {
      if ("ai".equals(line.speaker()) && line.text() != null && !line.text().isBlank()) {
        aiTexts.add(line.text());
      }
    }
    int from = Math.max(0, aiTexts.size() - RECENT_COUNT);
    return aiTexts.subList(from, aiTexts.size());
  }

  // 말의 첫 문장입니다. 처음 나오는 . ? ! 또는 줄바꿈까지이고, 40자가 넘으면 40자에서 자릅니다.
  //   "오늘은 10분만 해도 충분해. 피곤하면 쉬어도 돼."  →  "오늘은 10분만 해도 충분해."
  //   "그랬구나"                                          →  "그랬구나"
  static String firstSentence(String text) {
    String trimmed = text == null ? "" : text.trim();
    int end = trimmed.length();
    for (int i = 0; i < trimmed.length(); i++) {
      char c = trimmed.charAt(i);
      if (c == '.' || c == '?' || c == '!') {
        end = i + 1;
        break;
      }
      if (c == '\n') {
        end = i;
        break;
      }
    }
    String sentence = trimmed.substring(0, end).trim();
    return sentence.length() > MAX_PHRASE_LENGTH
        ? sentence.substring(0, MAX_PHRASE_LENGTH)
        : sentence;
  }

  // 말 보내기마다 지시 끝에 붙이는 [이미 한 말] 칸입니다. 대화에 AI 줄이 없으면 빈 글자입니다.
  // ⚠️ 고정 문장만 넣고, FE 가 보낸 lines 의 글자는 넣지 않습니다. (PR #349 리뷰)
  //    ai 줄도 FE 가 채워 보내서 학생이 지어낼 수 있는데, 지시(instructions)는 입력(input)보다 AI 가 더 잘 따르기 때문입니다.
  //    직전 말은 이미 입력의 대화 기록("코치: …")에 들어 있어서 AI 가 거기서 봅니다.
  public static final String ALREADY_SAID_SECTION =
      "\n\n[이미 한 말]\n- 대화 기록에서 네가 직전에 한 말 3개와 같은 표현을 다시 쓰거나 같은 첫 문장으로 시작하지 마."
          + " 학생이 방금 한 말에 맞춰 새로 답해.";

  public static String section(List<CallFinishRequest.Line> lines) {
    return recentAiReplies(lines).isEmpty() ? "" : ALREADY_SAID_SECTION;
  }

  // 반복이라 한 번 더 받을 때만 지시 끝에 더하는 줄입니다. 서버가 받은 첫 번째 답의 첫 문장을 넣습니다.
  // 같은 지시 · 같은 입력으로 다시 부르면 같은 답이 다시 나오기 쉬워서, 무엇이 겹쳤는지 알려 줍니다.
  // 이 글은 FE 가 아니라 AI 가 만든 것이라 지시에 넣어도 됩니다. (서버 고정 문구 SAFE_REPLY 면 부르는 쪽이 넣지 않음)
  //   "오늘은 10분만 해도 충분해! 미적분도 천천히 하자."
  //   →  [다시 답하기]
  //      - 방금 만든 답 "오늘은 10분만 해도 충분해!" 이 직전 말과 겹쳤어. 다른 문장으로 시작해.
  public static String retryHint(String firstReply) {
    return "\n\n[다시 답하기]\n- 방금 만든 답 \""
        + firstSentence(firstReply)
        + "\" 이 직전 말과 겹쳤어. 다른 문장으로 시작해.";
  }

  // 새 답이 직전 AI 답 3개 중 하나와 "같은 구절" 인지 봅니다.
  // 공백 · 문장부호를 지우고(글자와 숫자만 남기고) 비교해서, 둘 중 하나면 반복입니다.
  //   1. 첫 문장이 같다
  //      "오늘은 10분만 해도 충분해. 미적분은…" vs "오늘은 10분만 해도 충분해! 피곤하면…"  →  반복
  //   2. 12글자 이상이 이어서 같다
  //      "많이 피곤했구나 오늘은 10분만 해도 충분해" vs "오늘은 10분만 해도 충분해"
  //      → "오늘은10분만해도충분해" 12글자가 같아서 반복
  //   "미적분이 불안하구나. 어느 단원이 걱정돼?" vs "오늘은 10분만 해도 충분해."  →  반복 아님
  public static boolean repeats(String reply, List<CallFinishRequest.Line> lines) {
    String newAll = lettersOnly(reply);
    String newFirst = lettersOnly(firstSentence(reply));
    for (String previous : recentAiReplies(lines)) {
      String oldFirst = lettersOnly(firstSentence(previous));
      if (!newFirst.isEmpty() && newFirst.equals(oldFirst)) {
        return true;
      }
      if (sharesLongPiece(newAll, lettersOnly(previous))) {
        return true;
      }
    }
    return false;
  }

  // a 의 어느 12글자 조각이 b 안에 그대로 있는지 봅니다.
  private static boolean sharesLongPiece(String a, String b) {
    for (int start = 0; start + SAME_LENGTH <= a.length(); start++) {
      if (b.contains(a.substring(start, start + SAME_LENGTH))) {
        return true;
      }
    }
    return false;
  }

  // 글자(한글 · 영어 등)와 숫자만 남깁니다. 공백 · 문장부호 · 이모지는 지웁니다.
  //   "오늘은 10분만 해도 충분해!"  →  "오늘은10분만해도충분해"
  static String lettersOnly(String text) {
    return text == null ? "" : text.replaceAll("[^\\p{L}\\p{N}]", "");
  }
}
