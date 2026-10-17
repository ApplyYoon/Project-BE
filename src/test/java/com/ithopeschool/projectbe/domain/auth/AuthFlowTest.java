package com.ithopeschool.projectbe.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.test.web.servlet.MvcResult;
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
  @Autowired private LoginTokenRepository loginTokenRepository;
  @Autowired private TokenHasher tokenHasher;
  @Autowired private Clock clock;

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

  // ---- 토큰 검사 (#20) ----

  // 가입하고 로그인해서 토큰을 받아 옵니다.
  private String signupAndLogin(String email, String nickname) throws Exception {
    send("/api/v1/auth/signup", signupBody(email, "flowPass2026").replace("티맥이", nickname))
        .andExpect(status().isCreated());
    MvcResult result =
        send("/api/v1/auth/login", loginBody(email, "flowPass2026"))
            .andExpect(status().isOk())
            .andReturn();
    // 응답 {"token":"...","user":{...}} 에서 토큰만 꺼냅니다. 토큰에는 따옴표가 들어가지 않습니다.
    String body = result.getResponse().getContentAsString();
    return body.split("\"token\":\"")[1].split("\"")[0];
  }

  private ResultActions me(String authorizationHeader) throws Exception {
    return authorizationHeader == null
        ? mockMvc.perform(get("/api/v1/auth/me"))
        : mockMvc.perform(get("/api/v1/auth/me").header("Authorization", authorizationHeader));
  }

  @Test
  @DisplayName("로그인해서 받은 토큰으로 내 정보를 부르면 내가 나오고, 남의 토큰으로는 남이 나온다")
  void tokenTellsWhoIAm() throws Exception {
    String tokenOfA = signupAndLogin("a@starflow.com", "에이");
    String tokenOfB = signupAndLogin("b@starflow.com", "비이");

    me("Bearer " + tokenOfA).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("에이"));
    me("Bearer " + tokenOfB).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("비이"));
  }

  @Test
  @DisplayName("토큰이 없거나 틀리면 내 정보는 401 이고, 상태 확인(health)은 토큰 없이도 200")
  void withoutTokenOnlyPublicApisWork() throws Exception {
    me(null)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.error.message").value("로그인이 필요해요"));
    me("Bearer not-a-real-token")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.message").value("다시 로그인해 주세요"));
    // FE 의 테스트 로그인이 쓰는 가짜 토큰도 서버에서는 통하지 않습니다.
    me("Bearer fake-token").andExpect(status().isUnauthorized());

    mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("기간이 끝난 토큰으로 부르면 401")
  void expiredTokenIsRejected() throws Exception {
    String token = tokenHasher.newToken();
    Instant now = Instant.now(clock);
    loginTokenRepository.save(
        new LoginToken(
            CurrentUserProvider.TEST_USER_ID,
            tokenHasher.hash(token),
            now.minus(Duration.ofMinutes(1)),
            now.minus(Duration.ofDays(31))));

    me("Bearer " + token)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.message").value("로그인한 지 오래됐어요. 다시 로그인해 주세요"));
  }

  @Test
  @DisplayName("내 컴퓨터(local)에서는 테스트 토큰으로 부르면 테스트 사용자(1번)가 나온다")
  void localTestTokenLogsInAsTheTestUser() throws Exception {
    // 값은 application-local.yml 의 app.auth.test-token 입니다. 테스트 사용자는 서버가 켜질 때 만들어집니다.
    me("Bearer local-test-token")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("1"))
        .andExpect(jsonPath("$.name").value("테스트 사용자"));
  }
}
