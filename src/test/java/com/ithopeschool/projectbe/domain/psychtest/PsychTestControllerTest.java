package com.ithopeschool.projectbe.domain.psychtest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.psychtest.dto.PsychTestResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller 만 띄워서 상태 번호와 응답 모양을 확인합니다. 진짜 로직(PsychTestService)은 가짜입니다.
@WebMvcTest(PsychTestController.class)
class PsychTestControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PsychTestService psychTestService;

  private static final PsychTestResponse RESPONSE =
      new PsychTestResponse(
          "3",
          Instant.parse("2026-10-17T09:00:00Z"),
          Map.of("S1", 6),
          Map.of("startResistance", 5.5),
          Map.of("startResistance", "high"));

  @Test
  @DisplayName("저장하면 201 과 척도 점수 · 높낮이다")
  void saves() throws Exception {
    given(psychTestService.save(any())).willReturn(RESPONSE);

    mockMvc
        .perform(
            post("/api/v1/psych-tests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":{\"S1\":6}}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("3"))
        .andExpect(jsonPath("$.scores.startResistance").value(5.5))
        .andExpect(jsonPath("$.levels.startResistance").value("high"));
  }

  @Test
  @DisplayName("답(answers)이 없으면 400 이고 서비스를 부르지 않는다. 숫자가 아닌 답도 400 이다")
  void rejectsMissingAnswers() throws Exception {
    mockMvc
        .perform(post("/api/v1/psych-tests").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("심리검사 답을 보내 주세요"));
    mockMvc
        .perform(
            post("/api/v1/psych-tests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":{\"S1\":\"많이\"}}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(psychTestService);
  }

  @Test
  @DisplayName("가장 최근 결과는 200, 안 했으면 서비스가 던진 404 그대로다")
  void latest() throws Exception {
    given(psychTestService.getLatest()).willReturn(RESPONSE);
    mockMvc
        .perform(get("/api/v1/psych-tests/latest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.answers.S1").value(6));

    given(psychTestService.getLatest())
        .willThrow(new ApiException(ErrorCode.NOT_FOUND, "아직 심리검사를 하지 않았어요"));
    mockMvc
        .perform(get("/api/v1/psych-tests/latest"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.message").value("아직 심리검사를 하지 않았어요"));
  }
}
