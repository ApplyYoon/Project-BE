package com.ithopeschool.projectbe.domain.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.ai.dto.ChecklistResponse;
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
import org.springframework.test.web.servlet.ResultActions;

// Controller 만 띄워서 요청 모양 검사와 응답 모양을 확인합니다. 진짜 로직(ChecklistService)은 가짜입니다.
@WebMvcTest(AiController.class)
class AiControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ChecklistService checklistService;

  private ResultActions checklist(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/ai/checklist").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectRejected(String body, String message) throws Exception {
    checklist(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(checklistService);
  }

  @Test
  @DisplayName("보내면 200 과 항목 목록이다 (저장하지 않으므로 201 이 아니다)")
  void suggests() throws Exception {
    given(checklistService.suggest(any()))
        .willReturn(ChecklistResponse.of(List.of("42~45쪽 문제 풀기", "틀린 문제 표시하기", "채점하기")));

    checklist("{\"title\":\"수학 문제집\",\"goal\":\"6월 모의고사 수학 2등급\"}")
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"showHelp\":false,\"items\":[\"42~45쪽 문제 풀기\",\"틀린 문제 표시하기\",\"채점하기\"]}",
                    true));
  }

  @Test
  @DisplayName("AI 가 못 만들었으면 에러가 아니라 200 과 items: null 이다")
  void noItemsIsNull() throws Exception {
    given(checklistService.suggest(any())).willReturn(ChecklistResponse.of(null));

    checklist("{\"title\":\"수학 문제집\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":false,\"items\":null}", true));
  }

  @Test
  @DisplayName("위기 표현이면 200 과 showHelp: true, items: null 이다")
  void crisisShowsHelp() throws Exception {
    given(checklistService.suggest(any())).willReturn(ChecklistResponse.help());

    checklist("{\"title\":\"그냥 사라지고 싶어\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":true,\"items\":null}", true));
  }

  @Test
  @DisplayName("제목이 비었거나 200자를 넘거나, 목표가 100자를 넘으면 400 이고 서비스를 부르지 않는다")
  void rejectsBadRequests() throws Exception {
    expectRejected("{\"title\":\"\"}", "카드 제목을 적어 주세요");
    expectRejected("{\"title\":\"   \"}", "카드 제목을 적어 주세요");
    expectRejected("{}", "카드 제목을 적어 주세요");
    expectRejected("{\"title\":\"" + "가".repeat(201) + "\"}", "카드 제목은 200자 이하로 적어 주세요");
    expectRejected(
        "{\"title\":\"수학\",\"goal\":\"" + "가".repeat(101) + "\"}", "목표는 100자 이하로 적어 주세요");
  }

  @Test
  @DisplayName("로그인하지 않았으면 401 이다 (서비스가 던진 그대로)")
  void loginRequired() throws Exception {
    given(checklistService.suggest(any()))
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    checklist("{\"title\":\"수학 문제집\"}")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }
}
