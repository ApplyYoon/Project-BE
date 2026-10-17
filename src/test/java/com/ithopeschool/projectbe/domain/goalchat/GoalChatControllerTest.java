package com.ithopeschool.projectbe.domain.goalchat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.call.dto.ChatReplyResponse;
import com.ithopeschool.projectbe.domain.goalchat.dto.GoalChatFinishResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 만 띄워서 요청 모양 검사와 응답 모양을 확인합니다. 진짜 로직(GoalChatService)은 가짜입니다. (이슈 #239)
@WebMvcTest(GoalChatController.class)
class GoalChatControllerTest {

  private static final String LINES = "{\"lines\":[{\"speaker\":\"me\",\"text\":\"수학 3등급\"}]}";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GoalChatService goalChatService;

  private ResultActions postJson(String url, String body) throws Exception {
    return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  @DisplayName("열면 200 과 대화 번호(글자) · 첫 질문을 돌려준다 (본문 없음)")
  void starts() throws Exception {
    given(goalChatService.start())
        .willReturn(ChatReplyResponse.of("abc-1", GoalChatPrompt.OPENING));

    mockMvc
        .perform(post("/api/v1/goal-chats"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chatId").value("abc-1"))
        .andExpect(jsonPath("$.reply").value(GoalChatPrompt.OPENING))
        .andExpect(jsonPath("$.showHelp").value(false));
  }

  @Test
  @DisplayName("끝내면 목표 저장과 같은 칸 이름(dDay 등)으로 초안을 돌려준다")
  void finishReturnsDraft() throws Exception {
    GoalDraft draft =
        new GoalDraft(
            "score", "11월 학평 수학 3등급", "2026-11-19", "5등급", null, null, "좋은 점", "안 하면", "폰", "치운다");
    given(goalChatService.finish(eq("abc-1"), any())).willReturn(GoalChatFinishResponse.of(draft));

    postJson("/api/v1/goal-chats/abc-1/finish", LINES)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.goalDraft.kind").value("score"))
        .andExpect(jsonPath("$.goalDraft.dDay").value("2026-11-19"))
        .andExpect(jsonPath("$.goalDraft.currentScore").value("5등급"))
        .andExpect(jsonPath("$.goalDraft.totalAmount").isEmpty())
        .andExpect(jsonPath("$.goalDraft.plan").value("치운다"))
        .andExpect(jsonPath("$.showHelp").value(false));
  }

  @Test
  @DisplayName("위기 표현이면 goalDraft 없이 showHelp 와 도움받을 곳(1388 · 109)")
  void finishWithHelp() throws Exception {
    given(goalChatService.finish(eq("abc-1"), any())).willReturn(GoalChatFinishResponse.help());

    postJson("/api/v1/goal-chats/abc-1/finish", LINES)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.goalDraft").isEmpty())
        .andExpect(jsonPath("$.showHelp").value(true))
        .andExpect(jsonPath("$.helpLines[0].number").value("1388"));
  }

  @Test
  @DisplayName("대화가 비었거나 말한 사람이 틀리면 400 이고 로직을 부르지 않는다")
  void rejectsBadMessages() throws Exception {
    postJson("/api/v1/goal-chats/abc-1/messages", "{\"lines\":[]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("대화(lines)는 1 ~ 60줄까지 보낼 수 있어요"));
    postJson(
            "/api/v1/goal-chats/abc-1/finish",
            "{\"lines\":[{\"speaker\":\"teacher\",\"text\":\"응\"}]}")
        .andExpect(status().isBadRequest());

    verifyNoInteractions(goalChatService);
  }
}
