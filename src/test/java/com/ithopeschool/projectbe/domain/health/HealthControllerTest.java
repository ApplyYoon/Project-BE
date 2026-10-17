package com.ithopeschool.projectbe.domain.health;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// @WebMvcTest 는 서버 전체가 아니라 Controller 하나만 띄워서 빠르게 검사합니다.
@WebMvcTest(HealthController.class)
class HealthControllerTest {

  // MockMvc 는 진짜 서버를 켜지 않고 API 를 불러 보는 도구입니다.
  @Autowired private MockMvc mockMvc;

  // 진짜 Service 대신 가짜(Mock)를 끼워 넣습니다. 가짜가 무엇을 돌려줄지는 테스트마다 정합니다.
  @MockitoBean private HealthService healthService;

  @Test
  void healthReturnsOkAndDbConnected() throws Exception {
    given(healthService.check()).willReturn(new HealthResponse("ok", "connected"));

    mockMvc
        .perform(get("/api/v1/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"))
        .andExpect(jsonPath("$.db").value("connected"));
  }

  @Test
  void healthReturnsOkEvenWhenDbError() throws Exception {
    given(healthService.check()).willReturn(new HealthResponse("ok", "error"));

    // DB 가 안 붙어도 서버는 살아 있으므로 응답 코드는 200 입니다.
    mockMvc
        .perform(get("/api/v1/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"))
        .andExpect(jsonPath("$.db").value("error"));
  }
}
