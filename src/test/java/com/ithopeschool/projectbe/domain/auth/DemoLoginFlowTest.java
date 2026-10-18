package com.ithopeschool.projectbe.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.consent.ConsentRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// 서버 전체를 켜고 시연 코드 로그인을 처음부터 끝까지 해 봅니다. (DemoLoginService)
// 코드는 이 테스트에서만 넣는 가짜 값입니다. 배포 서버의 진짜 코드는 환경변수에만 있습니다.
// 틀린 횟수는 서버 메모리에서 세기 때문에, 테스트마다 다른 곳(X-Forwarded-For)에서 부른 것으로 해서 서로 섞이지 않게 합니다.
@SpringBootTest(properties = "app.auth.demo-login-code=" + DemoLoginFlowTest.CODE)
@AutoConfigureMockMvc
class DemoLoginFlowTest {

  static final String CODE = "demo-code-2026";

  @Autowired private MockMvc mockMvc;
  @Autowired private AccountRepository accountRepository;
  @Autowired private ConsentRecordRepository consentRecordRepository;

  private ResultActions demoLogin(String code, String place) throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/demo-login")
            .header("X-Forwarded-For", place)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + code + "\"}"));
  }

  // 응답 {"token":"...","user":{...}} 에서 토큰만 꺼냅니다.
  private static String tokenOf(ResultActions result) throws Exception {
    String body = result.andReturn().getResponse().getContentAsString();
    return body.split("\"token\":\"")[1].split("\"")[0];
  }

  @Test
  @DisplayName("맞는 코드면 토큰을 주고, 그 토큰으로 로그인이 필요한 API 를 부를 수 있다")
  void rightCodeGivesWorkingToken() throws Exception {
    ResultActions result =
        demoLogin(CODE, "1.0.0.1")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isString())
            .andExpect(jsonPath("$.user.name").value(DemoLoginService.DEMO_NAME));

    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokenOf(result)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value(DemoLoginService.DEMO_NAME));
  }

  @Test
  @DisplayName("여러 번 로그인해도 시연 계정은 하나이고, 가입할 때처럼 동의 기록도 한 줄만 있다")
  void sameDemoAccountEveryTime() throws Exception {
    String firstId =
        demoLogin(CODE, "1.0.0.2")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString()
            .split("\"id\":\"")[1]
            .split("\"")[0];
    String secondId =
        demoLogin(CODE, "1.0.0.2")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString()
            .split("\"id\":\"")[1]
            .split("\"")[0];

    assertThat(secondId).isEqualTo(firstId);
    Account account = accountRepository.findByEmail(DemoLoginService.DEMO_EMAIL).orElseThrow();
    assertThat(account.hasPassword()).isFalse();
    assertThat(account.getGoogleSubject()).isNull();
    assertThat(consentRecordRepository.findAll())
        .filteredOn(record -> record.getUserId().equals(account.getUserId()))
        .hasSize(1);
  }

  @Test
  @DisplayName("틀린 코드는 401, 같은 곳에서 5번 틀리면 그 뒤로는 맞는 코드도 429")
  void wrongCodeThenTooMany() throws Exception {
    for (int i = 0; i < DemoLoginLimiter.MAX_FAILS_PER_PLACE; i++) {
      demoLogin("wrong-code-" + i, "1.0.0.3")
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error.message").value("시연 코드가 맞지 않아요"));
    }

    demoLogin(CODE, "1.0.0.3")
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"));
    // 다른 곳은 그대로 됩니다.
    demoLogin(CODE, "1.0.0.4").andExpect(status().isOk());
  }

  @Test
  @DisplayName("코드를 비워 보내면 400")
  void blankCodeIs400() throws Exception {
    demoLogin("  ", "1.0.0.5")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }
}
