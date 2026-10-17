package com.ithopeschool.projectbe.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.auth.dto.ProfileResponse;
import com.ithopeschool.projectbe.domain.auth.dto.ProfileUpdateRequest;
import com.ithopeschool.projectbe.domain.auth.dto.WithdrawRequest;
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

// Controller 만 띄워서 "요청 모양 검사" 와 "응답 모양·상태 번호" 를 확인합니다. 진짜 로직(AccountService)은 가짜로 바꿉니다.
@WebMvcTest(AccountController.class)
class AccountControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AccountService accountService;

  private static final ProfileResponse PROFILE =
      new ProfileResponse("tmax@starflow.com", "김티맥", "티맥이", "7530560", "한국디지털미디어고등학교", false);

  private static final String VALID_UPDATE =
      """
      {
        "name": "김티맥",
        "nickname": "티맥이",
        "schoolCode": "7530560",
        "schoolName": "한국디지털미디어고등학교",
        "diaryAnalysis": true
      }
      """;

  private ResultActions putJson(String path, String body) throws Exception {
    return mockMvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private ResultActions postJson(String path, String body) throws Exception {
    return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectRejected(ResultActions result, String message) throws Exception {
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(accountService);
  }

  // ---- 내 정보 ----

  @Test
  @DisplayName("내 정보는 200 이고, 응답에 비밀번호가 없다 (필드는 정해진 6개뿐)")
  void getProfile() throws Exception {
    given(accountService.getProfile()).willReturn(PROFILE);

    mockMvc
        .perform(get("/api/v1/auth/profile"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("tmax@starflow.com"))
        .andExpect(jsonPath("$.name").value("김티맥"))
        .andExpect(jsonPath("$.nickname").value("티맥이"))
        .andExpect(jsonPath("$.schoolCode").value("7530560"))
        .andExpect(jsonPath("$.schoolName").value("한국디지털미디어고등학교"))
        .andExpect(jsonPath("$.diaryAnalysis").value(false))
        .andExpect(jsonPath("$.length()").value(6))
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  @Test
  @DisplayName("학교가 없으면 필드를 빼지 않고 null 로 돌려준다")
  void profileWithoutSchool() throws Exception {
    given(accountService.getProfile())
        .willReturn(new ProfileResponse("tmax@starflow.com", "김티맥", "티맥이", null, null, true));

    mockMvc
        .perform(get("/api/v1/auth/profile"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"email\":\"tmax@starflow.com\",\"name\":\"김티맥\",\"nickname\":\"티맥이\","
                        + "\"schoolCode\":null,\"schoolName\":null,\"diaryAnalysis\":true}",
                    true));
  }

  @Test
  @DisplayName("로그인하지 않았으면 401 과 에러 모양")
  void profileUnauthorized() throws Exception {
    given(accountService.getProfile())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    mockMvc
        .perform(get("/api/v1/auth/profile"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  // ---- 내 정보 고치기 ----

  @Test
  @DisplayName("고치기에 성공하면 200 과 고친 내 정보를 돌려주고, 이름·닉네임은 공백을 지운 값으로 넘어간다")
  void updateProfile() throws Exception {
    given(accountService.updateProfile(any())).willReturn(PROFILE);

    putJson(
            "/api/v1/auth/profile",
            VALID_UPDATE.replace("\"김티맥\"", "\"  김티맥 \"").replace("\"티맥이\"", "\" 티맥이  \""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nickname").value("티맥이"));

    ArgumentCaptor<ProfileUpdateRequest> captor =
        ArgumentCaptor.forClass(ProfileUpdateRequest.class);
    verify(accountService).updateProfile(captor.capture());
    assertThat(captor.getValue().name()).isEqualTo("김티맥");
    assertThat(captor.getValue().nickname()).isEqualTo("티맥이");
    assertThat(captor.getValue().diaryAnalysis()).isTrue();
  }

  @Test
  @DisplayName("학교를 null 로 보내도(학교 지우기) 통과한다")
  void updateProfileWithoutSchool() throws Exception {
    given(accountService.updateProfile(any())).willReturn(PROFILE);

    putJson(
            "/api/v1/auth/profile",
            VALID_UPDATE.replace("\"7530560\"", "null").replace("\"한국디지털미디어고등학교\"", "null"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("이름 2 ~ 20자, 닉네임 2 ~ 10자가 아니거나 동의 여부가 없으면 400")
  void updateProfileRejectsBadValues() throws Exception {
    expectRejected(
        putJson("/api/v1/auth/profile", VALID_UPDATE.replace("\"김티맥\"", "\"김\"")),
        "이름은 2자 이상 20자 이하로 적어 주세요");
    expectRejected(
        putJson("/api/v1/auth/profile", VALID_UPDATE.replace("\"티맥이\"", "\"   \"")),
        "닉네임은 2자 이상 10자 이하로 적어 주세요");
    expectRejected(
        putJson(
            "/api/v1/auth/profile", VALID_UPDATE.replace("\"티맥이\"", "\"" + "가".repeat(11) + "\"")),
        "닉네임은 2자 이상 10자 이하로 적어 주세요");
    expectRejected(
        putJson(
            "/api/v1/auth/profile",
            VALID_UPDATE.replace("\"diaryAnalysis\": true", "\"diaryAnalysis\": null")),
        "일기 분석 동의 여부를 보내 주세요");
  }

  @Test
  @DisplayName("실명은 선택이다: 안 보내거나 공백뿐이면 200 이고, 로직에는 null 로 넘어간다 (이슈 #71)")
  void updateProfileDoesNotRequireRealName() throws Exception {
    given(accountService.updateProfile(any())).willReturn(PROFILE);

    putJson("/api/v1/auth/profile", VALID_UPDATE.replace("\"name\": \"김티맥\",", ""))
        .andExpect(status().isOk());
    putJson("/api/v1/auth/profile", VALID_UPDATE.replace("\"김티맥\"", "\"   \""))
        .andExpect(status().isOk());

    ArgumentCaptor<ProfileUpdateRequest> captor =
        ArgumentCaptor.forClass(ProfileUpdateRequest.class);
    verify(accountService, org.mockito.Mockito.times(2)).updateProfile(captor.capture());
    assertThat(captor.getAllValues()).allSatisfy(request -> assertThat(request.name()).isNull());
  }

  // ---- 비밀번호 바꾸기 ----

  @Test
  @DisplayName("비밀번호를 바꾸면 204 이고 본문이 없다")
  void changePassword() throws Exception {
    putJson(
            "/api/v1/auth/password",
            "{\"currentPassword\":\"study2026\",\"newPassword\":\"newpass2026\"}")
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
  }

  @Test
  @DisplayName("새 비밀번호가 규칙(8 ~ 64자, 영문·숫자 포함)에 안 맞거나 지금 비밀번호가 비면 400")
  void changePasswordRejectsBadValues() throws Exception {
    expectRejected(
        putJson(
            "/api/v1/auth/password",
            "{\"currentPassword\":\"study2026\",\"newPassword\":\"abc1234\"}"),
        "비밀번호는 8자 이상 64자 이하로 적어 주세요");
    expectRejected(
        putJson(
            "/api/v1/auth/password",
            "{\"currentPassword\":\"study2026\",\"newPassword\":\"abcdefgh\"}"),
        "비밀번호에 영문과 숫자를 모두 넣어 주세요");
    expectRejected(
        putJson(
            "/api/v1/auth/password", "{\"currentPassword\":\"\",\"newPassword\":\"newpass2026\"}"),
        "지금 비밀번호를 입력해 주세요");
    expectRejected(
        putJson("/api/v1/auth/password", "{\"currentPassword\":\"study2026\"}"), "새 비밀번호를 입력해 주세요");
  }

  @Test
  @DisplayName("지금 비밀번호가 틀리면 400 과 그 문장 (401 이 아니다)")
  void changePasswordWrongCurrent() throws Exception {
    willThrow(new ApiException(ErrorCode.VALIDATION_ERROR, "비밀번호가 맞지 않아요"))
        .given(accountService)
        .changePassword(any());

    putJson(
            "/api/v1/auth/password",
            "{\"currentPassword\":\"wrong2026\",\"newPassword\":\"newpass2026\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("비밀번호가 맞지 않아요"));
  }

  // ---- 로그아웃 · 탈퇴 ----

  @Test
  @DisplayName("로그아웃은 204 이고 본문이 없다")
  void logout() throws Exception {
    mockMvc
        .perform(post("/api/v1/auth/logout"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    verify(accountService).logout();
  }

  @Test
  @DisplayName("탈퇴는 비밀번호나 구글 확인 정보 중 하나를 보내야 하고, 성공하면 204")
  void withdraw() throws Exception {
    expectRejected(postJson("/api/v1/auth/withdraw", "{\"password\":\"\"}"), "비밀번호를 입력해 주세요");
    expectRejected(postJson("/api/v1/auth/withdraw", "{}"), "비밀번호를 입력해 주세요");
    expectRejected(
        postJson("/api/v1/auth/withdraw", "{\"password\":\"  \",\"credential\":\"\"}"),
        "비밀번호를 입력해 주세요");

    postJson("/api/v1/auth/withdraw", "{\"password\":\"study2026\"}")
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    verify(accountService).withdraw(new WithdrawRequest("study2026", null));
  }

  @Test
  @DisplayName("구글 계정은 구글 확인 정보(credential)로 탈퇴를 부른다 (어느 계정인지는 Service 가 본다)")
  void withdrawWithGoogleCredential() throws Exception {
    postJson("/api/v1/auth/withdraw", "{\"credential\":\"eyJhbGciOi\"}")
        .andExpect(status().isNoContent());
    verify(accountService).withdraw(new WithdrawRequest(null, "eyJhbGciOi"));
  }
}
