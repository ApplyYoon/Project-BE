package com.ithopeschool.projectbe.domain.message;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.message.dto.MessageResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller 만 띄워서 "요청 모양 검사" 와 "응답 모양·상태 번호" 를 확인합니다. 진짜 로직(MessageService)은 가짜로 바꿉니다.
@WebMvcTest(MessageController.class)
class MessageControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MessageService messageService;

  private static final MessageResponse UNREAD =
      new MessageResponse("15", "nudge", "지금 25분만 해 보자", 1790000000000L, false, null);
  private static final MessageResponse READ =
      new MessageResponse("14", "carrot", "어제보다 40분 빨랐어", 1789990000000L, true, 1789990060000L);

  // ---- 목록 ----

  @Test
  @DisplayName("목록: 200 과 메시지 배열을 돌려준다. 시각은 밀리초 숫자, id 는 글자, 안 읽은 것의 readAt 은 null")
  void listShape() throws Exception {
    given(messageService.getMessages(null, null)).willReturn(List.of(UNREAD, READ));

    mockMvc
        .perform(get("/api/v1/messages"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    [
                      { "id": "15", "kind": "nudge", "text": "지금 25분만 해 보자",
                        "sentAt": 1790000000000, "read": false, "readAt": null },
                      { "id": "14", "kind": "carrot", "text": "어제보다 40분 빨랐어",
                        "sentAt": 1789990000000, "read": true, "readAt": 1789990060000 }
                    ]
                    """,
                    true));
  }

  @Test
  @DisplayName("목록: 메시지가 없으면 404 가 아니라 빈 배열 []")
  void emptyList() throws Exception {
    given(messageService.getMessages(null, null)).willReturn(List.of());

    mockMvc
        .perform(get("/api/v1/messages"))
        .andExpect(status().isOk())
        .andExpect(content().json("[]", true));
  }

  @Test
  @DisplayName("목록: after 와 before 는 숫자로 읽어서 Service 에 넘긴다")
  void passesAfterAndBefore() throws Exception {
    given(messageService.getMessages(eq(10L), eq(30L))).willReturn(List.of());

    mockMvc
        .perform(get("/api/v1/messages").param("after", "10").param("before", "30"))
        .andExpect(status().isOk());

    verify(messageService).getMessages(10L, 30L);
  }

  @Test
  @DisplayName("목록: after 만 줘도 되고 before 만 줘도 된다")
  void onlyOneOfThem() throws Exception {
    given(messageService.getMessages(any(), any())).willReturn(List.of());

    mockMvc.perform(get("/api/v1/messages").param("after", "10")).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/messages").param("before", "30")).andExpect(status().isOk());

    verify(messageService).getMessages(10L, null);
    verify(messageService).getMessages(null, 30L);
  }

  @Test
  @DisplayName("목록: after 가 숫자가 아니면 400 이고 Service 를 부르지 않는다")
  void rejectsNonNumberAfter() throws Exception {
    mockMvc
        .perform(get("/api/v1/messages").param("after", "어제"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(messageService);
  }

  @Test
  @DisplayName("목록: before 가 숫자가 아니면 400 이고 Service 를 부르지 않는다")
  void rejectsNonNumberBefore() throws Exception {
    mockMvc
        .perform(get("/api/v1/messages").param("before", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(messageService);
  }

  @Test
  @DisplayName("목록: 로그인하지 않으면(Service 가 401 을 던지면) 401 UNAUTHORIZED")
  void listUnauthorized() throws Exception {
    given(messageService.getMessages(null, null))
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    mockMvc
        .perform(get("/api/v1/messages"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.error.message").value("로그인이 필요해요"));
  }

  // ---- 읽음 표시 ----

  @Test
  @DisplayName("읽음: 200 과 읽음으로 바뀐 메시지 하나를 돌려준다")
  void markReadShape() throws Exception {
    given(messageService.markRead(14L)).willReturn(READ);

    mockMvc
        .perform(patch("/api/v1/messages/14/read"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    { "id": "14", "kind": "carrot", "text": "어제보다 40분 빨랐어",
                      "sentAt": 1789990000000, "read": true, "readAt": 1789990060000 }
                    """,
                    true));
  }

  @Test
  @DisplayName("읽음: 없거나 다른 사람의 메시지면 404 NOT_FOUND")
  void markReadNotFound() throws Exception {
    given(messageService.markRead(99L))
        .willThrow(new ApiException(ErrorCode.NOT_FOUND, "메시지를 찾을 수 없어요"));

    mockMvc
        .perform(patch("/api/v1/messages/99/read"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.error.message").value("메시지를 찾을 수 없어요"));
  }

  @Test
  @DisplayName("읽음: 번호가 숫자가 아니면 400 이고 Service 를 부르지 않는다")
  void markReadBadId() throws Exception {
    mockMvc
        .perform(patch("/api/v1/messages/abc/read"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(messageService);
  }

  @Test
  @DisplayName("읽음: 로그인하지 않으면(Service 가 401 을 던지면) 401 UNAUTHORIZED")
  void markReadUnauthorized() throws Exception {
    given(messageService.markRead(14L))
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    mockMvc
        .perform(patch("/api/v1/messages/14/read"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  // ---- 약속하지 않은 주소 ----

  @Test
  @DisplayName("메시지를 만드는 주소는 없다 (POST 는 405). 지우는 주소도 없다 (DELETE 는 404)")
  void noCreateOrDelete() throws Exception {
    // /messages 는 GET 만 있는 주소라서, POST 로 부르면 "그 방법은 안 돼요" (405)
    mockMvc
        .perform(post("/api/v1/messages").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    // /messages/14 라는 주소 자체가 없어서 "그 주소가 없어요" (404)
    mockMvc.perform(delete("/api/v1/messages/14")).andExpect(status().isNotFound());
    verifyNoInteractions(messageService);
  }

  @Test
  @DisplayName("읽음 표시는 PATCH 만 된다 (GET·PUT 은 405)")
  void markReadOnlyPatch() throws Exception {
    mockMvc
        .perform(get("/api/v1/messages/14/read"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    verifyNoInteractions(messageService);
  }
}
