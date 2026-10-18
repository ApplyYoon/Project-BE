package com.ithopeschool.projectbe.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// 서버의 시연 코드가 8자보다 짧으면 쉽게 맞힐 수 있어서 기능을 켜지 않습니다. (DemoLoginService.MIN_CODE_LENGTH)
// 짧은 코드를 그대로 넣어도 404 이고 시연 계정이 생기지 않아야 합니다.
@SpringBootTest(properties = "app.auth.demo-login-code=short")
@AutoConfigureMockMvc
class DemoLoginShortCodeFlowTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AccountRepository accountRepository;

  @Test
  @DisplayName("코드가 8자보다 짧으면 맞는 코드를 넣어도 404")
  void shortCodeKeepsFeatureOff() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/demo-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"short\"}"))
        .andExpect(status().isNotFound());
    assertThat(accountRepository.findByEmail(DemoLoginService.DEMO_EMAIL)).isEmpty();
  }
}
