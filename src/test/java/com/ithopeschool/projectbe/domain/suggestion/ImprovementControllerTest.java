package com.ithopeschool.projectbe.domain.suggestion;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.suggestion.dto.ImprovementSuggestionsResponse;
import com.ithopeschool.projectbe.domain.suggestion.dto.ImprovementSuggestionsResponse.Original;
import com.ithopeschool.projectbe.domain.suggestion.dto.ImprovementSuggestionsResponse.Suggestion;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller 만 띄워서 응답 모양을 확인합니다. 진짜 로직(ImprovementService)은 가짜입니다.
@WebMvcTest(ImprovementController.class)
class ImprovementControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ImprovementService improvementService;

  @Test
  @DisplayName("200 과 원인 · 근거 · 개선안(다듬은 문장 + 원래 문장)이다")
  void suggestions() throws Exception {
    given(improvementService.suggest())
        .willReturn(
            new ImprovementSuggestionsResponse(
                "phone",
                "recent",
                List.of(
                    new Suggestion(
                        "phone-easy-1",
                        "easy",
                        "화요일에 공부를 시작하면",
                        "알림을 모두 끈다",
                        true,
                        new Original("공부를 시작하면", "알림을 모두 끈다")))));

    mockMvc
        .perform(get("/api/v1/improvements/suggestions"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"cause\":\"phone\",\"basis\":\"recent\",\"suggestions\":[{\"key\":\"phone-easy-1\","
                        + "\"level\":\"easy\",\"ifText\":\"화요일에 공부를 시작하면\",\"thenText\":\"알림을 모두 끈다\","
                        + "\"polished\":true,\"original\":{\"ifText\":\"공부를 시작하면\",\"thenText\":\"알림을 모두 끈다\"}}]}",
                    true));
  }

  @Test
  @DisplayName("고를 원인이 없으면 cause · basis 가 null 이고 빈 목록이다")
  void none() throws Exception {
    given(improvementService.suggest()).willReturn(ImprovementSuggestionsResponse.none());

    mockMvc
        .perform(get("/api/v1/improvements/suggestions"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"cause\":null,\"basis\":null,\"suggestions\":[]}", true));
  }

  @Test
  @DisplayName("로그인하지 않았으면 401 이다 (서비스가 던진 그대로)")
  void loginRequired() throws Exception {
    given(improvementService.suggest())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    mockMvc
        .perform(get("/api/v1/improvements/suggestions"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }
}
