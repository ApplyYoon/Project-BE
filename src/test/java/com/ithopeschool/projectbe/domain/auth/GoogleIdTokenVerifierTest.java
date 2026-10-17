package com.ithopeschool.projectbe.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ithopeschool.projectbe.domain.auth.GoogleIdTokenVerifier.GoogleProfile;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// 구글을 진짜로 부르지 않는 테스트입니다. 가짜 구글 서버를 끼워 넣고,
// "구글의 답을 보고 받아도 되는 토큰인지 제대로 가르는지" 를 봅니다.
// ⚠️ 진짜 구글과 맞는지는 이 테스트로 알 수 없습니다. 브라우저에서 직접 구글로 시작해 봐야 합니다.
class GoogleIdTokenVerifierTest {

  // 테스트용 가짜 클라이언트 ID 와 토큰입니다.
  private static final String CLIENT_ID = "test-client.apps.googleusercontent.com";
  private static final String TOKEN = "header.payload.signature";

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer google = MockRestServiceServer.bindTo(builder).build();

  private GoogleIdTokenVerifier verifierWith(String clientId) {
    return new GoogleIdTokenVerifier(clientId, builder);
  }

  // 구글 tokeninfo 의 답 모양입니다. (구글 문서의 예시에서 우리가 읽는 부분)
  private static String answerOf(String aud, String iss, String emailVerified) {
    return """
        {
          "aud": "%s",
          "iss": "%s",
          "sub": "110169484474386276334",
          "email": "tmax@gmail.com",
          "email_verified": "%s",
          "name": "김티맥",
          "exp": "1760500000",
          "alg": "RS256"
        }
        """
        .formatted(aud, iss, emailVerified);
  }

  private void googleAnswers(String body) {
    google
        .expect(requestTo(Matchers.startsWith(GoogleIdTokenVerifier.TOKENINFO_URL)))
        .andExpect(method(HttpMethod.GET))
        .andExpect(queryParam("id_token", TOKEN))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
  }

  @Test
  @DisplayName("클라이언트 ID 가 있어야 켜진다")
  void enabledOnlyWithClientId() {
    assertThat(verifierWith(CLIENT_ID).isEnabled()).isTrue();
    assertThat(verifierWith("").isEnabled()).isFalse();
    assertThat(verifierWith("   ").isEnabled()).isFalse();
    assertThat(verifierWith(null).isEnabled()).isFalse();
  }

  @Test
  @DisplayName("꺼져 있거나 토큰이 비어 있으면 구글을 부르지 않고 빈 값을 돌려준다")
  void doesNotCallWhenDisabledOrBlank() {
    google.expect(never(), anything());

    assertThat(verifierWith("").verify(TOKEN)).isEmpty();
    assertThat(verifierWith(CLIENT_ID).verify("  ")).isEmpty();
    assertThat(verifierWith(CLIENT_ID).verify(null)).isEmpty();
    google.verify();
  }

  @Test
  @DisplayName("구글이 우리 앱용의 확인된 토큰이라고 답하면, 고유 번호·이메일·이름을 돌려준다")
  void returnsProfileForValidToken() {
    googleAnswers(answerOf(CLIENT_ID, "https://accounts.google.com", "true"));

    assertThat(verifierWith(CLIENT_ID).verify(TOKEN))
        .contains(new GoogleProfile("110169484474386276334", "tmax@gmail.com", ""));
    google.verify();
  }

  @Test
  @DisplayName("iss 는 https 가 없는 accounts.google.com 도 받는다")
  void acceptsIssuerWithoutScheme() {
    googleAnswers(answerOf(CLIENT_ID, "accounts.google.com", "true"));

    assertThat(verifierWith(CLIENT_ID).verify(TOKEN)).isPresent();
  }

  @Test
  @DisplayName("다른 앱을 위한 토큰(aud 가 다름)은 받지 않는다")
  void rejectsTokenForOtherApp() {
    googleAnswers(answerOf("other-app.apps.googleusercontent.com", "accounts.google.com", "true"));

    assertThat(verifierWith(CLIENT_ID).verify(TOKEN)).isEmpty();
  }

  @Test
  @DisplayName("구글이 만든 토큰이 아니면(iss 가 다름) 받지 않는다")
  void rejectsOtherIssuer() {
    googleAnswers(answerOf(CLIENT_ID, "https://evil.example.com", "true"));

    assertThat(verifierWith(CLIENT_ID).verify(TOKEN)).isEmpty();
  }

  @Test
  @DisplayName("구글이 확인하지 않은 이메일이면 받지 않는다")
  void rejectsUnverifiedEmail() {
    googleAnswers(answerOf(CLIENT_ID, "accounts.google.com", "false"));

    assertThat(verifierWith(CLIENT_ID).verify(TOKEN)).isEmpty();
  }

  @Test
  @DisplayName("가짜이거나 끝난 토큰이라 구글이 400 을 주면 빈 값을 돌려준다")
  void returnsEmptyWhenGoogleRejects() {
    google
        .expect(requestTo(Matchers.startsWith(GoogleIdTokenVerifier.TOKENINFO_URL)))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    "{ \"error\": \"invalid_token\", \"error_description\": \"Invalid Value\" }"));

    assertThat(verifierWith(CLIENT_ID).verify(TOKEN)).isEmpty();
  }
}
