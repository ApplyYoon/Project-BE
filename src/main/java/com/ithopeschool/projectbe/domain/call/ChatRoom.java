package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.goalchat.GoalChatPrompt;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// 상담 채팅의 "방" 입니다. 같은 채팅 API 로 세 명의 AI 와 이야기합니다. (2026-10-18)
// 사용자: "방 3개: 코치 · 목표 메이트 · 과목 쌤", "다양한 세션에서 채팅 가능해야"
//
//   COACH     코치       지금까지의 상담 채팅 그대로 (통화와 같은 대본)
//   GOAL_MATE 목표 메이트 큰 목표(시험 · 대회 · 진로)를 같이 세우고 매주 점검하는 친구
//   TUTOR     과목 쌤    과외 선생님. 답을 바로 주지 않고 어디까지 아는지 묻고 단계별로 설명
//
// 방은 대본 끝에 [이번 방] 칸 하나를 붙여서 바꿉니다. (section) 통화 대본의 [말투] 의 깎아내리지 않기 · [꼭 지킬 것] ·
// 위로하는 날 규칙은 어느 방이든 그대로 지킵니다. 채팅 기록(call_records)의 channel 은 방과 상관없이 CHAT 입니다.
//
// ⚠️ 방을 더하면 dto/CallSessionRequest.ROOMS 에도 더해야 합니다. (ChatRoomTest 가 확인)
public enum ChatRoom {
  COACH("코치"),
  GOAL_MATE("목표 메이트"),
  TUTOR("과목 쌤");

  private final String label;

  ChatRoom(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  // 글자 → 방. 없거나 모르는 글자면 빈 값입니다. (요청은 CallSessionRequest 의 @Pattern 이 먼저 거릅니다)
  public static Optional<ChatRoom> from(String name) {
    for (ChatRoom room : values()) {
      if (room.name().equals(name)) {
        return Optional.of(room);
      }
    }
    return Optional.empty();
  }

  // 과목 쌤은 학생 성향 칸을 붙이지 않습니다. 공부 내용 설명이 중심이라 말 거는 방법은 짧게만 씁니다.
  public boolean usesPsychSection() {
    return this != TUTOR;
  }

  // 대본 끝에 붙이는 [이번 방] 칸. 코치는 지금 대본 그대로라서 빈 글자입니다.
  public String section() {
    if (this == COACH) {
      return "";
    }
    List<String> lines = new ArrayList<>();
    lines.add("");
    lines.add("");
    lines.add("[이번 방: " + label + "]");
    lines.add(
        "- 위의 [통화 순서] 는 따르지 마. 입력에 '[통화 순서] 1번부터' 라고 있어도 이 칸이 먼저야."
            + " [말투] 의 깎아내리지 않기 · [꼭 지킬 것] · 위로하는 날 규칙은 그대로 지켜.");
    if (this == GOAL_MATE) {
      lines.add("- 너는 이 방에서 '목표 메이트' 야. 큰 목표(시험 · 대회 · 진로)를 같이 세우고 매주 점검하는 친구처럼 말해.");
      lines.add("- 목표가 아직 없으면 WOOP 순서로 한 번에 하나씩 짧게 물어. 학생이 이미 답한 것은 다시 묻지 마.");
      lines.add("  1) 바람: 이루고 싶은 것 한 줄. 점수형(예: 11월 학평 수학 3등급)인지 완주형(예: 영단어장 1회독)인지 알 수 있게.");
      lines.add("  2) 최고의 결과: 이루면 좋은 점 하나.");
      lines.add("  3) 걸림돌: " + GoalChatPrompt.OBSTACLE_RULE);
      lines.add("  4) 걸림돌이 오면 할 행동: " + GoalChatPrompt.IF_THEN_RULE);
      lines.add(
          "- 마감 날이 정해지면 make_plan 행동 버튼(목표 한 줄 · 마감 날)을 줘."
              + " 마감이 30일보다 멀면 date 는 null 로 두고, 30일 안의 중간 목표를 같이 정해.");
      lines.add("- 이번 주 숫자가 오면([이번 대화의 주제]) 숫자 하나를 짚고, 다음 주에 바꿀 것 하나를 같이 정해.");
      lines.add("- 목표가 너무 크면 \"조금 줄여 볼까?\" 정도만 말하고, 정하는 건 학생이야.");
    } else {
      lines.add("- 너는 이 방에서 '과목 쌤'(과외 선생님)이야. 공부 내용 질문에 답해. 관리 강도 · 학생 성향은 말투에만 살짝 써.");
      lines.add("- 답을 바로 주지 마. 먼저 학생이 어디까지 아는지 물어보고, 한 단계씩 설명하면서 중간에 학생이 직접 말해 보게 해.");
      lines.add("- 숙제를 대신 풀어 달라고 하면 정답만 주지 말고 풀이 과정을 같이 따라가. 다음 단계는 학생이 먼저 해 보게 해.");
      lines.add("- 사진이 오면 사진 속 문제 · 필기를 먼저 읽고, 어디가 막혔는지 먼저 물어봐.");
      lines.add("- 수식은 일반 글자로 써(x^2, √, lim). 확실하지 않으면 모른다고 말하고 지어내지 마.");
      lines.add("- 이해했으면 끝에 비슷한 문제 하나를 말로 내 줘. 더 풀고 싶어 하면 start_quiz(주제).");
    }
    return String.join("\n", lines);
  }
}
