package com.ithopeschool.projectbe.domain.insight;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ScoreStudyController.class)
class ScoreStudyControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean ScoreStudyService service;

  @Test
  void returnsEmptyListWithoutEnoughExams() throws Exception {
    when(service.getAnalysis()).thenReturn(List.of());
    mvc.perform(get("/api/v1/insights/score-study"))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
  }

  @Test
  void requiresLogin() throws Exception {
    when(service.getAnalysis()).thenThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));
    mvc.perform(get("/api/v1/insights/score-study")).andExpect(status().isUnauthorized());
  }
}
