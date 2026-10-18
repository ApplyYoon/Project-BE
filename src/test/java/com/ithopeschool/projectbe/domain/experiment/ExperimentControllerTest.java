package com.ithopeschool.projectbe.domain.experiment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.experiment.dto.ExperimentResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller 테스트: 판정은 가짜 Service 로 두고, 입력 검사(@Valid)와 응답 모양만 확인합니다.
@WebMvcTest(ExperimentController.class)
class ExperimentControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ExperimentService experimentService;

  private void expectBadRequest(String body, String message) throws Exception {
    mockMvc
        .perform(post("/api/v1/experiments").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
  }

  @Test
  @DisplayName("시작하면 201 과 실험 모양(전략 글 · 끝나는 날 · 며칠째 · 이행률)을 돌려준다")
  void start() throws Exception {
    given(experimentService.start(any()))
        .willReturn(
            new ExperimentResponse(
                "3",
                "폰·SNS 때문에 놓치는 날이 많다",
                "phone",
                "phone-bag",
                "시작할 때 폰을 가방에 넣기",
                List.of("12"),
                LocalDate.of(2026, 10, 18),
                LocalDate.of(2026, 10, 31),
                1,
                10,
                "running",
                null,
                null,
                new ExperimentResponse.Fulfillment(100, "good")));

    mockMvc
        .perform(
            post("/api/v1/experiments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"hypothesis\":\"폰·SNS 때문에 놓치는 날이 많다\",\"strategyId\":\"phone-bag\",\"cardIds\":[\"12\"]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("3"))
        .andExpect(jsonPath("$.endDate").value("2026-10-31"))
        .andExpect(jsonPath("$.status").value("running"))
        .andExpect(jsonPath("$.fulfillment.rate").value(100))
        .andExpect(jsonPath("$.fulfillment.grade").value("good"));
  }

  @Test
  @DisplayName("가설 · 전략 · 카드가 비었거나, 카드가 6장 · 숫자가 아니거나, 그만둘 기준이 0 이면 400 이고 Service 를 부르지 않는다")
  void validation() throws Exception {
    expectBadRequest(
        "{\"hypothesis\":\" \",\"strategyId\":\"phone-bag\",\"cardIds\":[\"1\"]}", "가설을 적어 주세요");
    expectBadRequest(
        "{\"hypothesis\":\"폰\",\"strategyId\":\"\",\"cardIds\":[\"1\"]}", "전략을 골라 주세요");
    expectBadRequest(
        "{\"hypothesis\":\"폰\",\"strategyId\":\"phone-bag\",\"cardIds\":[]}",
        "실험 규칙을 붙일 카드를 하나 이상 골라 주세요");
    expectBadRequest(
        "{\"hypothesis\":\"폰\",\"strategyId\":\"phone-bag\",\"cardIds\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"]}",
        "카드는 5장까지 붙일 수 있어요");
    expectBadRequest(
        "{\"hypothesis\":\"폰\",\"strategyId\":\"phone-bag\",\"cardIds\":[\"a\"]}",
        "카드 번호는 숫자로 보내 주세요");
    expectBadRequest(
        "{\"hypothesis\":\"폰\",\"strategyId\":\"phone-bag\",\"cardIds\":[\"1\"],\"minGainPp\":0}",
        "그만둘 기준은 1~50%p 사이로 보내 주세요");
    verifyNoInteractions(experimentService);
  }
}
