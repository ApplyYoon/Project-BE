package com.ithopeschool.projectbe.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// 서버에 시연 코드를 넣지 않은 기본 상태에서는 시연 코드 로그인이 꺼져서 404 입니다. (DemoLoginService)
// 아무 코드나 넣어 봐도 시연 계정이 생기지 않아야 합니다.
@SpringBootTest
@AutoConfigureMockMvc
class DemoLoginOffFlowTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AccountRepository accountRepository;

  @Test
  @DisplayName("코드가 없으면 404 이고 시연 계정을 만들지 않는다")
  void offWithoutCode() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/demo-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"anything-123\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.message").value("시연 코드 로그인이 꺼져 있어요"));
    assertThat(accountRepository.findByEmail(DemoLoginService.DEMO_EMAIL)).isEmpty();
  }
}
