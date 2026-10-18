package com.ithopeschool.projectbe.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.auth.dto.LoginRequest;
import com.ithopeschool.projectbe.domain.auth.dto.LoginResponse;
import com.ithopeschool.projectbe.domain.auth.dto.SignupRequest;
import com.ithopeschool.projectbe.domain.auth.dto.UserResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 만 띄워서 "요청 모양 검사" 와 "응답 모양" 을 확인합니다. 진짜 로직(AuthService)은 가짜로 바꿉니다.
@WebMvcTest(AuthController.class)
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthService authService;
  // 시연 코드 로그인(2026-10-18)도 같은 Controller 에 있어서 함께 가짜로 둡니다.
  @MockitoBean private DemoLoginService demoLoginService;

  // 규칙에 맞는 가입 요청입니다. 테스트마다 한 군데만 바꿔서 보냅니다.
  private static final String VALID_SIGNUP =
      """
      {
        "email": "tmax@starflow.com",
        "password": "study2026",
        "name": "김티맥",
        "nickname": "티맥이",
        "schoolCode": "7530560",
        "schoolName": "한국디지털미디어고등학교",
        "consents": { "terms": true, "privacy": true, "diaryAnalysis": false, "over14": true, "version": "v1" }
      }
      """;

  private ResultActions signup(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private ResultActions login(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  // 틀린 요청은 400 과 그 문장이 나오고, 로직(AuthService)까지 가지 않아야 합니다.
  private void expectSignupRejected(String body, String message) throws Exception {
    signup(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(authService);
  }

  // ---- 가입 ----

  @Test
  @DisplayName("가입에 성공하면 201 과 { id, name, onboarded } 를 돌려준다")
  void signupReturns201() throws Exception {
    given(authService.signup(any())).willReturn(new UserResponse("1", "티맥이", false));

    signup(VALID_SIGNUP)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("1"))
        .andExpect(jsonPath("$.name").value("티맥이"))
        // 응답에는 id · name · onboarded 세 개만 있습니다. (이메일·실명·비밀번호는 돌려주지 않습니다)
        .andExpect(jsonPath("$.onboarded").value(false))
        .andExpect(jsonPath("$.length()").value(3));
  }

  @Test
  @DisplayName("이메일·이름·닉네임은 앞뒤 공백을 지운 값으로 로직에 넘어가고, 비밀번호는 그대로 넘어간다")
  void signupTrimsTextFields() throws Exception {
    given(authService.signup(any())).willReturn(new UserResponse("1", "티맥이", false));

    signup(
            VALID_SIGNUP
                .replace("\"tmax@starflow.com\"", "\"  tmax@starflow.com \"")
                .replace("\"김티맥\"", "\" 김티맥  \"")
                .replace("\"티맥이\"", "\"  티맥이 \"")
                .replace("\"study2026\"", "\" study2026 \""))
        .andExpect(status().isCreated());

    ArgumentCaptor<SignupRequest> captor = ArgumentCaptor.forClass(SignupRequest.class);
    verify(authService).signup(captor.capture());
    assertThat(captor.getValue().email()).isEqualTo("tmax@starflow.com");
    assertThat(captor.getValue().name()).isNull();
    assertThat(captor.getValue().nickname()).isEqualTo("티맥이");
    assertThat(captor.getValue().password()).isEqualTo(" study2026 ");
  }

  @Test
  @DisplayName("학교를 고르지 않아도(null) 가입 요청이 통과한다")
  void signupWithoutSchool() throws Exception {
    given(authService.signup(any())).willReturn(new UserResponse("1", "티맥이", false));

    signup(VALID_SIGNUP.replace("\"7530560\"", "null").replace("\"한국디지털미디어고등학교\"", "null"))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("이메일이 비었거나 모양이 틀리면 400")
  void signupRejectsBadEmail() throws Exception {
    expectSignupRejected(VALID_SIGNUP.replace("tmax@starflow.com", "  "), "이메일을 입력해 주세요");
    expectSignupRejected(VALID_SIGNUP.replace("tmax@starflow.com", "tmax"), "이메일 주소를 다시 확인해 주세요");
    expectSignupRejected(VALID_SIGNUP.replace("tmax@starflow.com", "tmax@"), "이메일 주소를 다시 확인해 주세요");
    expectSignupRejected(
        VALID_SIGNUP.replace("tmax@starflow.com", "@starflow.com"), "이메일 주소를 다시 확인해 주세요");
  }

  @Test
  @DisplayName("비밀번호가 8자 미만·64자 초과이거나 영문·숫자 중 하나가 없으면 400")
  void signupRejectsWeakPassword() throws Exception {
    String lengthMessage = "비밀번호는 8자 이상 64자 이하로 적어 주세요";
    String mixMessage = "비밀번호에 영문과 숫자를 모두 넣어 주세요";

    expectSignupRejected(VALID_SIGNUP.replace("study2026", "abc1234"), lengthMessage);
    expectSignupRejected(VALID_SIGNUP.replace("study2026", "a1".repeat(33)), lengthMessage);
    expectSignupRejected(VALID_SIGNUP.replace("study2026", "abcdefgh"), mixMessage);
    expectSignupRejected(VALID_SIGNUP.replace("study2026", "12345678"), mixMessage);
    expectSignupRejected(VALID_SIGNUP.replace("\"password\": \"study2026\",", ""), "비밀번호를 입력해 주세요");
  }

  @Test
  @DisplayName("비밀번호는 8자와 64자까지 통과한다 (경계값)")
  void signupAcceptsPasswordAtTheEdges() throws Exception {
    given(authService.signup(any())).willReturn(new UserResponse("1", "티맥이", false));

    signup(VALID_SIGNUP.replace("study2026", "abcd1234")).andExpect(status().isCreated());
    signup(VALID_SIGNUP.replace("study2026", "a1".repeat(32))).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("닉네임은 2 ~ 10자가 아니면 400 (닉네임은 꼭 있어야 한다)")
  void signupRejectsBadNames() throws Exception {

    String nicknameMessage = "닉네임은 2자 이상 10자 이하로 적어 주세요";

    expectSignupRejected(VALID_SIGNUP.replace("\"티맥이\"", "\"티\""), nicknameMessage);
    expectSignupRejected(
        VALID_SIGNUP.replace("\"티맥이\"", "\"" + "가".repeat(11) + "\""), nicknameMessage);
    expectSignupRejected(VALID_SIGNUP.replace("\"nickname\": \"티맥이\",", ""), "닉네임을 입력해 주세요");
  }

  @Test
  @DisplayName("실명은 보내거나 생략해도 무시하며, 로직에는 null만 넘어간다 (이슈 #71)")
  void signupDoesNotRequireRealName() throws Exception {
    given(authService.signup(any())).willReturn(new UserResponse("1", "티맥이", false));

    signup(VALID_SIGNUP).andExpect(status().isCreated());
    signup(VALID_SIGNUP.replace("\"김티맥\"", "\"김\"")).andExpect(status().isCreated());
    signup(VALID_SIGNUP.replace("\"name\": \"김티맥\",", "")).andExpect(status().isCreated());
    signup(VALID_SIGNUP.replace("\"김티맥\"", "\"   \"")).andExpect(status().isCreated());
    signup(VALID_SIGNUP.replace("\"김티맥\"", "null")).andExpect(status().isCreated());

    ArgumentCaptor<SignupRequest> captor = ArgumentCaptor.forClass(SignupRequest.class);
    verify(authService, org.mockito.Mockito.times(5)).signup(captor.capture());
    assertThat(captor.getAllValues()).allSatisfy(request -> assertThat(request.name()).isNull());
  }

  @Test
  @DisplayName("만 14세 이상 확인이 없거나 false 면 400 이고, 로직까지 가지 않는다 (이슈 #71)")
  void signupRequiresOver14() throws Exception {
    String message = "만 14세 이상만 가입할 수 있어요";

    expectSignupRejected(VALID_SIGNUP.replace("\"over14\": true", "\"over14\": false"), message);
    // 아예 안 보낸 경우: true/false 자리가 비어서 값을 읽을 수 없는 요청이라 400 과 기본 문장이 나옵니다.
    expectSignupRejected(VALID_SIGNUP.replace(", \"over14\": true", ""), "보낸 값을 다시 확인해 주세요");
  }

  @Test
  @DisplayName("동의 문구 버전이 없거나 비었거나 너무 길면 400 이다 (이슈 #71)")
  void signupRequiresConsentVersion() throws Exception {
    expectSignupRejected(
        VALID_SIGNUP.replace(", \"version\": \"v1\"", ""), "동의 문구 버전(version)을 보내 주세요");
    expectSignupRejected(
        VALID_SIGNUP.replace("\"version\": \"v1\"", "\"version\": \"   \""),
        "동의 문구 버전(version)을 보내 주세요");
    expectSignupRejected(
        VALID_SIGNUP.replace("\"version\": \"v1\"", "\"version\": \"" + "v".repeat(21) + "\""),
        "동의 문구 버전이 너무 길어요");
  }

  @Test
  @DisplayName("필수 동의(이용약관·개인정보)가 없으면 400, 선택 동의는 없어도 된다")
  void signupRequiresConsents() throws Exception {
    expectSignupRejected(
        VALID_SIGNUP.replace("\"terms\": true", "\"terms\": false"), "서비스 이용약관에 동의해 주세요");
    expectSignupRejected(
        VALID_SIGNUP.replace("\"privacy\": true", "\"privacy\": false"), "개인정보 수집·이용에 동의해 주세요");
    // 동의 내용을 아예 안 보낸 경우
    expectSignupRejected(
        VALID_SIGNUP.replace(
            "\"consents\": { \"terms\": true, \"privacy\": true, \"diaryAnalysis\": false, \"over14\": true, \"version\": \"v1\" }",
            "\"consents\": null"),
        "약관 동의 내용을 보내 주세요");
    // 필수 항목을 아예 빼고 보낸 경우: true/false 자리가 비어서 값을 읽을 수 없는 요청입니다. 400 과 기본 문장이 나옵니다.
    expectSignupRejected(VALID_SIGNUP.replace("\"terms\": true, ", ""), "보낸 값을 다시 확인해 주세요");
  }

  @Test
  @DisplayName("이미 가입한 이메일이면 409 와 에러 모양")
  void signupConflict() throws Exception {
    given(authService.signup(any()))
        .willThrow(new ApiException(ErrorCode.CONFLICT, "이미 가입한 이메일이에요"));

    signup(VALID_SIGNUP)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"))
        .andExpect(jsonPath("$.error.message").value("이미 가입한 이메일이에요"));
  }

  // ---- 로그인 ----

  @Test
  @DisplayName("로그인에 성공하면 200 과 { token, user: { id, name } } 을 돌려준다")
  void loginReturnsTokenAndUser() throws Exception {
    given(authService.login(new LoginRequest("tmax@starflow.com", "study2026")))
        .willReturn(new LoginResponse("token-abc", new UserResponse("1", "티맥이", false)));

    login("{\"email\":\"tmax@starflow.com\",\"password\":\"study2026\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value("token-abc"))
        .andExpect(jsonPath("$.user.id").value("1"))
        .andExpect(jsonPath("$.user.name").value("티맥이"))
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  @DisplayName("이메일이나 비밀번호가 비어 있으면 400")
  void loginRejectsBlank() throws Exception {
    login("{\"email\":\"\",\"password\":\"study2026\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("이메일을 입력해 주세요"));
    login("{\"email\":\"tmax@starflow.com\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("비밀번호를 입력해 주세요"));
    verifyNoInteractions(authService);
  }

  @Test
  @DisplayName("이메일·비밀번호가 틀리면 401 과 에러 모양")
  void loginUnauthorized() throws Exception {
    given(authService.login(any()))
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 맞지 않아요"));

    login("{\"email\":\"tmax@starflow.com\",\"password\":\"wrong2026\"}")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.error.message").value("이메일 또는 비밀번호가 맞지 않아요"));
  }

  // ---- 내 정보 ----

  @Test
  @DisplayName("내 정보는 200 과 { id, name, onboarded } 를 돌려준다")
  void meReturnsUser() throws Exception {
    given(authService.me()).willReturn(new UserResponse("1", "티맥이", false));

    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer token-abc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("1"))
        .andExpect(jsonPath("$.name").value("티맥이"))
        .andExpect(jsonPath("$.onboarded").value(false))
        .andExpect(jsonPath("$.length()").value(3));
  }

  @Test
  @DisplayName("로그인하지 않고 내 정보를 부르면 401 과 에러 모양")
  void meUnauthorized() throws Exception {
    given(authService.me()).willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    mockMvc
        .perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.error.message").value("로그인이 필요해요"));
  }

  @Test
  @DisplayName("JSON 모양이 깨진 요청은 400")
  void brokenJson() throws Exception {
    login("{\"email\":").andExpect(status().isBadRequest());
    signup("이건 JSON 이 아님").andExpect(status().isBadRequest());
  }

  // ---- 시연 코드 로그인 ----

  @Test
  @DisplayName("시연 코드 로그인은 X-Forwarded-For 의 첫 주소를 '요청한 곳' 으로 넘기고 토큰을 돌려준다")
  void demoLoginPassesFirstForwardedAddress() throws Exception {
    given(demoLoginService.login(any(), any()))
        .willReturn(new LoginResponse("demo-token", new UserResponse("9", "시연 학생", false)));

    mockMvc
        .perform(
            post("/api/v1/auth/demo-login")
                .header("X-Forwarded-For", "1.2.3.4, 10.0.0.1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"demo-code-2026\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value("demo-token"));
    verify(demoLoginService).login("demo-code-2026", "1.2.3.4");
  }

  @Test
  @DisplayName("시연 코드를 비워 보내면 400 이고 로직까지 가지 않는다")
  void demoLoginBlankCodeIs400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/demo-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("시연 코드를 입력해 주세요"));
    verifyNoInteractions(demoLoginService);
  }
}
