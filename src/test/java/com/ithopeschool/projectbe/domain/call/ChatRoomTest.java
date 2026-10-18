package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.call.dto.CallSessionRequest;
import com.ithopeschool.projectbe.domain.goalchat.GoalChatPrompt;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 상담 채팅의 방 3개(코치 · 목표 메이트 · 과목 쌤)의 [이번 방] 칸을 확인합니다. (2026-10-18)
class ChatRoomTest {

  @Test
  @DisplayName("요청에서 받는 방 이름(CallSessionRequest.ROOMS)은 ChatRoom 의 이름과 같다")
  void roomsMatchEnum() {
    String names =
        Arrays.stream(ChatRoom.values()).map(Enum::name).collect(Collectors.joining("|"));

    assertThat(CallSessionRequest.ROOMS).isEqualTo(names);
  }

  @Test
  @DisplayName("코치 방은 지금 대본 그대로라서 붙일 칸이 없다")
  void coachAddsNothing() {
    assertThat(ChatRoom.COACH.section()).isEmpty();
    assertThat(ChatRoom.COACH.usesPsychSection()).isTrue();
  }

  @Test
  @DisplayName("목표 메이트는 WOOP 순서(목표 만들기 대화와 같은 방해물 · 대처 규칙)와 make_plan 을 적는다")
  void goalMateSection() {
    assertThat(ChatRoom.GOAL_MATE.section())
        .startsWith("\n\n[이번 방: 목표 메이트]")
        .contains("위의 [통화 순서] 는 따르지 마")
        .contains("1) 바람")
        .contains(GoalChatPrompt.OBSTACLE_RULE)
        .contains(GoalChatPrompt.IF_THEN_RULE)
        .contains("make_plan")
        .contains("다음 주에 바꿀 것 하나");
  }

  @Test
  @DisplayName("과목 쌤은 답을 바로 주지 않고 단계별로 설명하며, 학생 성향 칸을 쓰지 않는다")
  void tutorSection() {
    assertThat(ChatRoom.TUTOR.section())
        .startsWith("\n\n[이번 방: 과목 쌤]")
        .contains("답을 바로 주지 마")
        .contains("풀이 과정을 같이 따라가")
        .contains("어디가 막혔는지 먼저 물어봐")
        .contains("start_quiz");
    assertThat(ChatRoom.TUTOR.usesPsychSection()).isFalse();
  }

  @Test
  @DisplayName("글자로 방을 찾고, 없거나 모르는 글자는 빈 값이다")
  void findsRoom() {
    assertThat(ChatRoom.from("GOAL_MATE")).contains(ChatRoom.GOAL_MATE);
    assertThat(ChatRoom.from(null)).isEmpty();
    assertThat(ChatRoom.from("tutor")).isEmpty();
  }
}
