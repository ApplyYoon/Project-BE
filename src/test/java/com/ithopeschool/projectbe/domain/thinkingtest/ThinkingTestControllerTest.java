package com.ithopeschool.projectbe.domain.thinkingtest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.thinkingtest.dto.ThinkingTestResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 만 띄워서 요청 모양 검사와 응답 상태 번호를 확인합니다. 진짜 로직(ThinkingTestService)은 가짜입니다.
@WebMvcTest(ThinkingTestController.class)
class ThinkingTestControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ThinkingTestService thinkingTestService;

  // 습관 8개를 모두 average 로 채운 scores JSON
  private static String scoresJson(String average) {
    List<String> items = new ArrayList<>();
    for (CognitiveBias bias : CognitiveBias.values()) {
      items.add("{\"habit\":\"" + bias.name() + "\",\"average\":" + average + "}");
    }
    return "[" + String.join(",", items) + "]";
  }

  private ResultActions save(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/thinking-tests").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectRejected(String body, String message) throws Exception {
    save(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(thinkingTestService);
  }

  @Test
  @DisplayName("저장하면 201 과 결과(공부 스타일)를 돌려준다")
  void saves() throws Exception {
    given(thinkingTestService.save(any()))
        .willReturn(
            new ThinkingTestResponse(
                "3",
                Instant.parse("2026-10-15T09:00:00Z"),
                2,
                "낙관 · 균형 · 균형 · 균형",
                List.of(),
                List.of(),
                List.of("PLANNING_FALLACY"),
                List.of()));

    save("{\"testVersion\":2,\"scores\":" + scoresJson("4.0") + ",\"weaknesses\":[\"GAME\"]}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("3"))
        .andExpect(jsonPath("$.styleName").value("낙관 · 균형 · 균형 · 균형"));
  }

  @Test
  @DisplayName("점수가 8개가 아니거나, 1 ~ 7 밖이거나, 모르는 습관·약점이면 400 이고 서비스를 부르지 않는다")
  void rejectsBadRequests() throws Exception {
    expectRejected("{\"testVersion\":2}", "습관 점수(scores)를 보내 주세요");
    expectRejected(
        "{\"testVersion\":2,\"scores\":[{\"habit\":\"PLANNING_FALLACY\",\"average\":4.0}]}",
        "습관 점수(scores)는 8개여야 해요");
    expectRejected(
        "{\"testVersion\":2,\"scores\":" + scoresJson("7.5") + "}", "average 는 1 ~ 7 이에요");
    expectRejected(
        "{\"testVersion\":2,\"scores\":"
            + scoresJson("4.0").replace("PLANNING_FALLACY", "LAZY")
            + "}",
        "habit 은 정해진 생각 습관 이름이어야 해요");
    expectRejected(
        "{\"testVersion\":2,\"scores\":" + scoresJson("4.0") + ",\"weaknesses\":[\"웹툰\"]}",
        "weaknesses 는 정해진 약점 이름이어야 해요");
    expectRejected("{\"scores\":" + scoresJson("4.0") + "}", "테스트 모양 번호(testVersion)를 보내 주세요");
  }

  @Test
  @DisplayName("최근 결과가 없으면 404, 생각 vs 현실의 기간이 틀리면 400 (서비스가 던진 에러)")
  void errorsFromTheService() throws Exception {
    given(thinkingTestService.getLatest())
        .willThrow(new ApiException(ErrorCode.NOT_FOUND, "아직 생각 습관 테스트를 하지 않았어요"));
    given(thinkingTestService.getGap(5))
        .willThrow(new ApiException(ErrorCode.VALIDATION_ERROR, "days 는 7 ~ 60 이에요"));

    mockMvc.perform(get("/api/v1/thinking-tests/latest")).andExpect(status().isNotFound());
    mockMvc.perform(get("/api/v1/thinking-tests/gap?days=5")).andExpect(status().isBadRequest());
  }
}
