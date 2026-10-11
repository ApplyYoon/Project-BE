package com.ithopeschool.projectbe.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// 서버 전체를 진짜로 켜고(가짜 없이) 가입 → 로그인을 이어서 해 봅니다.
// 앞의 테스트들이 부품을 따로 봤다면, 이건 FE 가 실제로 부르는 순서 그대로입니다.
// 다른 테스트와 DB 를 같이 쓰기 때문에, 이 테스트만 쓰는 이메일로 가입합니다.
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AuthFlowTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AccountRepository accountRepository;

  private static String signupBody(String email, String password) {
    return """
        {
          "email": "%s",
          "password": "%s",
          "name": "김티맥",
          "nickname": "티맥이",
          "schoolCode": null,
          "schoolName": null,
          "consents": { "terms": true, "privacy": true, "diaryAnalysis": true }
        }
        """
        .formatted(email, password);
  }

  // JSON 본문을 담아 POST 로 부릅니다.
  private ResultActions send(String path, String body) throws Exception {
    return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private static String loginBody(String email, String password) {
    return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
  }

  @Test
  @DisplayName("가입 → 같은 이메일로 다시 가입(409) → 틀린 비밀번호(401) → 맞는 비밀번호(200, 토큰)")
  void signupThenLogin(CapturedOutput output) throws Exception {
    send("/api/v1/auth/signup", signupBody("flow@starflow.com", "flowPass2026"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isString())
        .andExpect(jsonPath("$.name").value("티맥이"));

    send("/api/v1/auth/signup", signupBody("FLOW@starflow.com", "another2026"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"));

    send("/api/v1/auth/login", loginBody("flow@starflow.com", "wrongPass2026"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.message").value("이메일 또는 비밀번호가 맞지 않아요"));

    send("/api/v1/auth/login", loginBody("flow@starflow.com", "flowPass2026"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isString())
        .andExpect(jsonPath("$.user.name").value("티맥이"));

    // 두 번째 가입(409)은 계정을 바꾸지 못했습니다. 처음 비밀번호가 그대로입니다.
    assertThat(accountRepository.findByEmail("flow@starflow.com")).isPresent();
    send("/api/v1/auth/login", loginBody("flow@starflow.com", "another2026"))
        .andExpect(status().isUnauthorized());

    // 비밀번호와 이메일은 서버 로그에 남지 않습니다. (SQL 로그에는 값 대신 ? 만 찍힙니다)
    assertThat(output).doesNotContain("flowPass2026");
    assertThat(output).doesNotContain("flow@starflow.com");
  }

  @Test
  @DisplayName("가입하지 않은 이메일로 로그인하면 401 (비밀번호가 틀렸을 때와 같은 답)")
  void loginWithUnknownEmail() throws Exception {
    send("/api/v1/auth/login", loginBody("nobody@starflow.com", "flowPass2026"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.error.message").value("이메일 또는 비밀번호가 맞지 않아요"));
  }

  @Test
  @DisplayName("가입·로그인 주소는 다른 주소(GET)로 부르면 405")
  void onlyPostIsAllowed() throws Exception {
    mockMvc.perform(get("/api/v1/auth/login")).andExpect(status().isMethodNotAllowed());
  }
}
