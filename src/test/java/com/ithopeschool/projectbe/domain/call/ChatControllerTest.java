package com.ithopeschool.projectbe.domain.call;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.call.dto.ChatReplyResponse;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 만 띄워서 요청 모양 검사와 응답 모양을 확인합니다. 진짜 로직(ChatService)은 가짜입니다. (이슈 #238)
@WebMvcTest(ChatController.class)
class ChatControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ChatService chatService;

  private ResultActions postJson(String url, String body) throws Exception {
    return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  @DisplayName("채팅을 열면 200 과 채팅 번호 · AI 의 첫 말을 돌려준다 (요청 모양은 통화 시작과 같음)")
  void starts() throws Exception {
    given(chatService.start(any(), any(), any()))
        .willReturn(ChatReplyResponse.of(12L, "안녕! 어제는 어땠어?"));

    postJson("/api/v1/chats", "{\"careLevel\":2,\"maxHarshness\":3}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chatId").value("12"))
        .andExpect(jsonPath("$.reply").value("안녕! 어제는 어땠어?"))
        .andExpect(jsonPath("$.showHelp").value(false))
        .andExpect(jsonPath("$.helpLines").isEmpty());
  }

  @Test
  @DisplayName("학년 · 반을 주소에 실으면 그대로 넘긴다 (오늘 시간표를 대본에 넣으려고)")
  void startsWithClass() throws Exception {
    given(chatService.start(any(), eq(2), eq(1))).willReturn(ChatReplyResponse.of(12L, "안녕!"));

    mockMvc
        .perform(
            post("/api/v1/chats")
                .param("grade", "2")
                .param("classNo", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"careLevel\":2,\"maxHarshness\":3}"))
        .andExpect(status().isOk());
    verify(chatService).start(any(), eq(2), eq(1));
  }

  @Test
  @DisplayName("위기 표현이면 showHelp 와 도움받을 곳(1388 · 109)이 담긴다")
  void crisisReplyHasHelpLines() throws Exception {
    given(chatService.send(eq(12L), any())).willReturn(ChatReplyResponse.crisis(12L, "안내"));

    postJson("/api/v1/chats/12/messages", "{\"lines\":[{\"speaker\":\"me\",\"text\":\"…\"}]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.showHelp").value(true))
        .andExpect(jsonPath("$.helpLines[0].number").value("1388"))
        .andExpect(jsonPath("$.helpLines[1].number").value("109"));
  }

  @Test
  @DisplayName("대화가 비었거나 61줄 이상이거나 말한 사람이 틀리면 400 이고 로직을 부르지 않는다")
  void rejectsBadMessages() throws Exception {
    postJson("/api/v1/chats/12/messages", "{\"lines\":[]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("대화(lines)는 1 ~ 60줄까지 보낼 수 있어요"));

    String line = "{\"speaker\":\"me\",\"text\":\"응\"}";
    String sixtyOne = "{\"lines\":[" + String.join(",", Collections.nCopies(61, line)) + "]}";
    postJson("/api/v1/chats/12/messages", sixtyOne).andExpect(status().isBadRequest());

    postJson("/api/v1/chats/12/messages", "{\"lines\":[{\"speaker\":\"teacher\",\"text\":\"응\"}]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("speaker 는 ai 또는 me 예요"));

    verifyNoInteractions(chatService);
  }
}
