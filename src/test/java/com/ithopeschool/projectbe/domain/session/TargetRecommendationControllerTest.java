package com.ithopeschool.projectbe.domain.session;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TargetRecommendationController.class)
class TargetRecommendationControllerTest {
  @Autowired private MockMvc mvc;
  @MockitoBean private TargetRecommendationService service;
  private static final String URL = "/api/v1/study-sessions/target-recommendation";

  @Test
  void returnsRecommendation() throws Exception {
    when(service.recommend(3L, 50))
        .thenReturn(
            new TargetRecommendationService.Response(
                new TargetRecommend(60, TargetRecommend.Kind.RAISE, "3번 연속 완주! 60분 어때?")));
    mvc.perform(get(URL).param("subjectId", "3").param("currentTargetMinutes", "50"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recommendation.minutes").value(60));
  }

  @Test
  void missingParameterIsBadRequest() throws Exception {
    mvc.perform(get(URL).param("subjectId", "3")).andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void missingLoginIsUnauthorized() throws Exception {
    when(service.recommend(3L, 50))
        .thenThrow(new ApiException(ErrorCode.UNAUTHORIZED, "다시 로그인해 주세요"));
    mvc.perform(get(URL).param("subjectId", "3").param("currentTargetMinutes", "50"))
        .andExpect(status().isUnauthorized());
  }
}
