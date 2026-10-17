package com.ithopeschool.projectbe.domain.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.auth.AccountRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 서버 전체를 진짜로 켜고(가짜 없이) 만 14세 확인 · 동의 기록 · "동의하지 않으면 저장 못 함" 을 처음부터 끝까지 불러 봅니다. (이슈 #71)
// 끝나면 만든 사용자를 탈퇴시켜서 데이터를 지웁니다. (탈퇴가 동의 기록까지 지우는지도 확인됩니다)
@SpringBootTest
@AutoConfigureMockMvc
class ConsentFlowTest {

  private static final String PASSWORD = "study2026";
  private static final String CONSENTS_OK =
      "{ \"terms\": true, \"privacy\": true, \"diaryAnalysis\": false, \"over14\": true, \"version\": \"v1\" }";
  private static final String AGREE_OK =
      "{\"terms\":true,\"privacy\":true,\"over14\":true,\"version\":\"v1\"}";
  // 저장 요청에 쓰는 올바른 본문들 (틀리면 동의 검사보다 먼저 400 이 나와서 403 을 볼 수 없습니다)
  private static final String ONBOARDING_BODY =
      "{\"grade\":\"고2\",\"targetExam\":\"수능\",\"targetExamDate\":\"2027-11-18\",\"dailyAvailableMinutes\":180}";

  @Autowired private MockMvc mockMvc;
  @Autowired private AccountRepository accountRepository;
  @Autowired private ConsentRecordRepository consentRecordRepository;

  @Test
  @DisplayName("만 14세 확인이 없거나 낡은 문구 버전이면 가입이 끝나지 않고, 계정이 하나도 안 생긴다")
  void signupNeedsAgeAndCurrentText() throws Exception {
    String email = newEmail();

    // 만 14세 이상이 아니라고 하면 400
    send(post("/api/v1/auth/signup"), null, signupBody(email, consents("false", "v1")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("만 14세 이상만 가입할 수 있어요"));
    // 낡은 문구 버전이면 409
    send(post("/api/v1/auth/signup"), null, signupBody(email, consents("true", "v0")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    // 두 번 모두 아무것도 만들어지지 않았습니다. (같은 이메일로 바로 다시 가입할 수 있어야 합니다)
    assertThat(accountRepository.existsByEmail(email)).isFalse();

    send(post("/api/v1/auth/signup"), null, signupBody(email, CONSENTS_OK))
        .andExpect(status().isCreated());
    String token = login(email);
    try {
      // 가입하자마자 최신 문구에 동의한 상태입니다.
      send(get("/api/v1/auth/consent"), token, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.currentVersion").value("v1"))
          .andExpect(jsonPath("$.agreedVersion").value("v1"))
          .andExpect(jsonPath("$.needsConsent").value(false));
    } finally {
      withdraw(token);
    }
  }

  @Test
  @DisplayName("실명 없이도 가입되며 호환용 name 응답은 닉네임이다 (이슈 #71)")
  void signupWithoutRealName() throws Exception {
    String email = newEmail();
    String body = signupBody(email, CONSENTS_OK).replace("\"name\": \"김티맥\",", "");

    send(post("/api/v1/auth/signup"), null, body).andExpect(status().isCreated());
    String token = login(email);
    try {
      send(get("/api/v1/auth/profile"), token, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.name").value("동의이"));
    } finally {
      withdraw(token);
    }
  }

  @Test
  @DisplayName("동의 문구가 바뀌면: 저장은 403, 보기·지우기·로그아웃은 되고, 다시 동의하면 저장이 다시 되며 옛 기록은 남는다")
  void outdatedConsentBlocksSavingUntilAgreeAgain() throws Exception {
    String email = newEmail();
    Long userId = signup(email);
    String token = login(email);
    try {
      // 가입 직후에는 저장이 됩니다.
      String subjectId = createSubject(token, "수학");

      // 문구가 바뀌었다고 칩니다: 이 사람의 가장 최근 동의가 옛 버전(v0)이 됩니다.
      consentRecordRepository.save(new ConsentRecord(userId, Instant.now(), "v0", true));

      // 저장하는 요청은 403 CONSENT_REQUIRED
      expectConsentRequired(
          post("/api/v1/subjects"), token, "{\"name\":\"영어\",\"color\":\"blue\"}");
      expectConsentRequired(
          post("/api/v1/study-plans"),
          token,
          "{\"date\":\"2026-10-12\",\"subjectId\":\"%s\",\"content\":\"수학 문제집\",\"targetMinutes\":60}"
              .formatted(subjectId));
      expectConsentRequired(put("/api/v1/onboarding"), token, ONBOARDING_BODY);
      expectConsentRequired(patch("/api/v1/subjects/" + subjectId), token, "{\"name\":\"수학2\"}");
      // 보기는 됩니다.
      send(get("/api/v1/subjects"), token, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1));

      // 동의 상태를 보면 다시 동의가 필요하다고 알려 줍니다.
      send(get("/api/v1/auth/consent"), token, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.agreedVersion").value("v0"))
          .andExpect(jsonPath("$.needsConsent").value(true));

      // 낡은 문구로는 다시 동의할 수 없고(409), 최신 문구로는 됩니다(201).
      send(post("/api/v1/auth/consent"), token, AGREE_OK.replace("\"v1\"", "\"v0\""))
          .andExpect(status().isConflict());
      send(post("/api/v1/auth/consent"), token, AGREE_OK)
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.agreedVersion").value("v1"))
          .andExpect(jsonPath("$.needsConsent").value(false));
      // 같은 문구에 한 번 더 동의하려 하면 409 (같은 줄이 쌓이지 않습니다)
      send(post("/api/v1/auth/consent"), token, AGREE_OK).andExpect(status().isConflict());

      // 다시 동의했으니 저장이 됩니다.
      createSubject(token, "영어");
      // 옛 기록은 남아 있습니다: 가입 때 v1, 문구가 바뀐 v0, 다시 동의 v1
      assertThat(consentRecordsOf(userId))
          .extracting(ConsentRecord::getTextVersion)
          .containsExactly("v1", "v0", "v1");
    } finally {
      withdraw(token);
    }
  }

  @Test
  @DisplayName("동의하지 않은 사람도 데이터를 지우고, 로그아웃하고, 탈퇴할 수 있다. 탈퇴하면 동의 기록도 지워진다")
  void unconsentedUserCanDeleteLogoutAndWithdraw() throws Exception {
    String email = newEmail();
    Long userId = signup(email);
    String token = login(email);
    String subjectId = createSubject(token, "수학");
    consentRecordRepository.save(new ConsentRecord(userId, Instant.now(), "v0", true));

    // 지우기(DELETE)는 됩니다.
    send(delete("/api/v1/subjects/" + subjectId), token, null).andExpect(status().isNoContent());
    // 로그아웃도 됩니다.
    send(post("/api/v1/auth/logout"), token, null).andExpect(status().isNoContent());

    // 다시 로그인해서 탈퇴합니다. 동의하지 않았어도 됩니다.
    String secondToken = login(email);
    assertThat(consentRecordsOf(userId)).hasSize(2);
    withdraw(secondToken);
    assertThat(consentRecordsOf(userId)).isEmpty();
  }

  @Test
  @DisplayName("토큰 없이 부르면 동의 상태·다시 동의하기도 401 이다 (403 이 아니다)")
  void consentApisNeedLogin() throws Exception {
    send(get("/api/v1/auth/consent"), null, null).andExpect(status().isUnauthorized());
    send(post("/api/v1/auth/consent"), null, AGREE_OK)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    // 저장 요청도 로그인부터 봅니다.
    send(post("/api/v1/subjects"), null, "{\"name\":\"수학\",\"color\":\"red\"}")
        .andExpect(status().isUnauthorized());
  }

  // ---- 도우미 ----

  private java.util.List<ConsentRecord> consentRecordsOf(Long userId) {
    return consentRecordRepository.findAll().stream()
        .filter(record -> record.getUserId().equals(userId))
        .toList();
  }

  private void expectConsentRequired(
      MockHttpServletRequestBuilder builder, String token, String body) throws Exception {
    send(builder, token, body)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"))
        .andExpect(jsonPath("$.error.message").value("동의 문구에 동의한 뒤에 저장할 수 있어요"));
  }

  private static String newEmail() {
    return "consent-" + UUID.randomUUID().toString().substring(0, 8) + "@starflow.com";
  }

  private static String consents(String over14, String version) {
    return "{ \"terms\": true, \"privacy\": true, \"diaryAnalysis\": false, \"over14\": "
        + over14
        + ", \"version\": \""
        + version
        + "\" }";
  }

  private static String signupBody(String email, String consents) {
    return """
        {
          "email": "%s",
          "password": "%s",
          "name": "김티맥",
          "nickname": "동의이",
          "schoolCode": null,
          "schoolName": null,
          "consents": %s
        }
        """
        .formatted(email, PASSWORD, consents);
  }

  private Long signup(String email) throws Exception {
    ResultActions result = send(post("/api/v1/auth/signup"), null, signupBody(email, CONSENTS_OK));
    result.andExpect(status().isCreated());
    return Long.valueOf(JsonPath.read(responseBody(result), "$.id"));
  }

  private String login(String email) throws Exception {
    ResultActions result =
        send(
            post("/api/v1/auth/login"),
            null,
            "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD));
    result.andExpect(status().isOk());
    return JsonPath.read(responseBody(result), "$.token");
  }

  private String createSubject(String token, String name) throws Exception {
    ResultActions result =
        send(post("/api/v1/subjects"), token, "{\"name\":\"" + name + "\",\"color\":\"red\"}");
    result.andExpect(status().isCreated());
    return JsonPath.read(responseBody(result), "$.id");
  }

  private void withdraw(String token) throws Exception {
    send(post("/api/v1/auth/withdraw"), token, "{\"password\":\"" + PASSWORD + "\"}")
        .andExpect(status().isNoContent());
  }

  // 이 토큰을 Authorization 헤더에 붙이고(null 이면 로그인 안 한 요청), 본문이 있으면 JSON 으로 담아 부릅니다.
  private ResultActions send(MockHttpServletRequestBuilder builder, String token, String body)
      throws Exception {
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    if (body != null) {
      builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return mockMvc.perform(builder);
  }

  private static String responseBody(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }
}
