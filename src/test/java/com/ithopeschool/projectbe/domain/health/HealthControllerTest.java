package com.ithopeschool.projectbe.domain.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

// @WebMvcTest 는 서버 전체가 아니라 Controller 하나만 띄워서 빠르게 검사합니다.
@WebMvcTest(HealthController.class)
class HealthControllerTest {

  // MockMvc 는 진짜 서버를 켜지 않고 API 를 불러 보는 도구입니다.
  @Autowired private MockMvc mockMvc;

  @Test
  void healthReturnsOk() throws Exception {
    mockMvc
        .perform(get("/api/v1/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"));
  }
}
