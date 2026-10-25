package com.ithopeschool.projectbe.domain.manual;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.manual.dto.ManualItemResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller 테스트: 항목을 모으는 일은 가짜 Service 로 두고, 응답 모양(FE 의 ManualItem)만 확인합니다.
@WebMvcTest(ManualController.class)
class ManualControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ManualService manualService;

  @Test
  @DisplayName("200 과 FE 의 ManualItem 모양(id · category · title · confirmedDate · evidence)을 돌려준다")
  void list() throws Exception {
    given(manualService.list())
        .willReturn(
            List.of(
                new ManualItemResponse(
                    "experiment-3",
                    "effectiveMethod",
                    "시작할 때 폰을 가방에 넣기",
                    LocalDate.of(2026, 10, 15),
                    "14일 실험: 카드 실행률 50% → 70% (+20%p)")));

    mockMvc
        .perform(get("/api/v1/manual"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value("experiment-3"))
        .andExpect(jsonPath("$[0].category").value("effectiveMethod"))
        .andExpect(jsonPath("$[0].title").value("시작할 때 폰을 가방에 넣기"))
        .andExpect(jsonPath("$[0].confirmedDate").value("2026-10-15"))
        .andExpect(jsonPath("$[0].evidence").value("14일 실험: 카드 실행률 50% → 70% (+20%p)"));
  }

  @Test
  @DisplayName("항목이 없으면 404 가 아니라 200 과 빈 배열이다")
  void emptyList() throws Exception {
    given(manualService.list()).willReturn(List.of());

    mockMvc
        .perform(get("/api/v1/manual"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("숨기면 204 이고, 주소의 이름표를 Service 에 그대로 넘긴다")
  void hide() throws Exception {
    mockMvc
        .perform(put("/api/v1/manual/hidden-items/insight-morning-vs-night"))
        .andExpect(status().isNoContent());

    verify(manualService).hide("insight-morning-vs-night");
  }

  @Test
  @DisplayName("내 목록에 없는 이름표면 404 NOT_FOUND 에러 모양으로 돌려준다")
  void hideUnknown() throws Exception {
    willThrow(new ApiException(ErrorCode.NOT_FOUND, "사용설명서 항목을 찾을 수 없어요"))
        .given(manualService)
        .hide("experiment-999");

    mockMvc
        .perform(put("/api/v1/manual/hidden-items/experiment-999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.error.message").value("사용설명서 항목을 찾을 수 없어요"));
  }
}
