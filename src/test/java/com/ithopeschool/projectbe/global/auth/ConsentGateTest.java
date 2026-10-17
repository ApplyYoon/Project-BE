package com.ithopeschool.projectbe.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ithopeschool.projectbe.domain.auth.LoginToken;
import com.ithopeschool.projectbe.domain.auth.LoginTokenRepository;
import com.ithopeschool.projectbe.domain.auth.TokenHasher;
import com.ithopeschool.projectbe.domain.consent.ConsentPolicy;
import com.ithopeschool.projectbe.domain.consent.ConsentRecord;
import com.ithopeschool.projectbe.domain.consent.ConsentRecordRepository;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.mock.web.MockHttpServletRequest;

// "최신 동의 문구에 동의하지 않은 사람은 데이터를 저장할 수 없다" 를 확인합니다. (이슈 #71)
// 저장하는 요청(POST · PUT · PATCH)만 막고, 보기(GET)와 지우기(DELETE)는 막지 않습니다.
// 동의 기록은 테스트용 DB(H2)에 실제로 넣고, 요청은 가짜(MockHttpServletRequest)로 만듭니다.
@DataJpaTest
class ConsentGateTest {

  private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");
  private static final String TEST_TOKEN = "local-test-token";
  private static final String CONSENT_MESSAGE = "동의 문구에 동의한 뒤에 저장할 수 있어요";

  @Autowired private LoginTokenRepository loginTokenRepository;
  @Autowired private ConsentRecordRepository consentRecordRepository;

  private final TokenHasher tokenHasher = new TokenHasher();

  // 사용자 userId 의 로그인 토큰을 만들어 DB 에 넣고, 그 토큰으로 method 요청을 보낸 것처럼 만든 provider 를 돌려줍니다.
  private CurrentUserProvider providerFor(Long userId, String method) {
    String token = tokenHasher.newToken();
    loginTokenRepository.save(
        new LoginToken(userId, tokenHasher.hash(token), NOW.plus(Duration.ofDays(30)), NOW));
    return providerWithHeader("Bearer " + token, method, "");
  }

  private CurrentUserProvider providerWithHeader(
      String authorization, String method, String configuredTestToken) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/anything");
    if (authorization != null) {
      request.addHeader("Authorization", authorization);
    }
    return new CurrentUserProvider(
        request,
        loginTokenRepository,
        consentRecordRepository,
        tokenHasher,
        Clock.fixed(NOW, ZoneOffset.UTC),
        configuredTestToken);
  }

  private void agree(Long userId, String version) {
    consentRecordRepository.save(new ConsentRecord(userId, NOW, version, true));
  }

  private static void assertConsentRequired(CurrentUserProvider provider) {
    assertThatThrownBy(provider::getCurrentUserId)
        .isInstanceOf(ApiException.class)
        .hasMessage(CONSENT_MESSAGE)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.CONSENT_REQUIRED);
  }

  @Test
  @DisplayName("동의한 적이 없는 사람이 저장하는 요청(POST · PUT · PATCH)을 하면 403 CONSENT_REQUIRED 다")
  void saveWithoutAnyConsentIsForbidden() {
    assertConsentRequired(providerFor(7L, "POST"));
    assertConsentRequired(providerFor(7L, "PUT"));
    assertConsentRequired(providerFor(7L, "PATCH"));
  }

  @Test
  @DisplayName("동의하지 않았어도 보기(GET)와 지우기(DELETE)는 된다 — 내 데이터를 보고 지울 수 있어야 한다")
  void readAndDeleteAreAlwaysAllowed() {
    assertThat(providerFor(7L, "GET").getCurrentUserId()).isEqualTo(7L);
    assertThat(providerFor(7L, "DELETE").getCurrentUserId()).isEqualTo(7L);
    assertThat(providerFor(7L, "HEAD").getCurrentUserId()).isEqualTo(7L);
  }

  @Test
  @DisplayName("최신 문구에 동의한 사람은 저장할 수 있다")
  void saveAfterAgreeingToCurrentTextIsAllowed() {
    agree(7L, "v1");

    assertThat(providerFor(7L, "POST").getCurrentUserId()).isEqualTo(7L);
    assertThat(providerFor(7L, "PUT").getCurrentUserId()).isEqualTo(7L);
    assertThat(providerFor(7L, "PATCH").getCurrentUserId()).isEqualTo(7L);
  }

  @Test
  @DisplayName("옛 문구에만 동의했으면 저장할 수 없다 (문구가 바뀌면 다시 동의받는다)")
  void oldTextOnlyIsForbidden() {
    agree(7L, "v0");

    assertConsentRequired(providerFor(7L, "POST"));
  }

  @Test
  @DisplayName("가장 최근 동의가 기준이다: 옛 문구 → 최신 문구로 다시 동의하면 저장할 수 있다")
  void latestConsentDecides() {
    agree(7L, "v0");
    assertConsentRequired(providerFor(7L, "POST"));

    agree(7L, "v1");
    assertThat(providerFor(7L, "POST").getCurrentUserId()).isEqualTo(7L);
  }

  @Test
  @DisplayName("가장 최근 동의가 기준이다: 최신 문구에 동의한 줄이 있어도 그 뒤에 더 옛 버전 줄이 생기면 막힌다")
  void aNewerOldRecordBlocksAgain() {
    agree(7L, "v1");
    agree(7L, "v0");

    assertConsentRequired(providerFor(7L, "POST"));
  }

  @Test
  @DisplayName("남의 동의는 내 동의가 아니다")
  void otherUsersConsentDoesNotCount() {
    agree(8L, "v1");

    assertConsentRequired(providerFor(7L, "POST"));
    assertThat(providerFor(8L, "POST").getCurrentUserId()).isEqualTo(8L);
  }

  @Test
  @DisplayName("내 컴퓨터 전용 테스트 토큰은 동의 기록이 없어도 저장할 수 있다 (배포에는 이 토큰이 없다)")
  void testTokenSkipsTheConsentCheck() {
    assertThat(providerWithHeader("Bearer " + TEST_TOKEN, "POST", TEST_TOKEN).getCurrentUserId())
        .isEqualTo(CurrentUserProvider.TEST_USER_ID);
  }

  @Test
  @DisplayName("동의 검사가 없는 쪽(getCurrentUserIdWithoutConsent)은 동의하지 않은 사람에게도 번호를 돌려준다")
  void withoutConsentCheckAlwaysIdentifies() {
    assertThat(providerFor(7L, "POST").getCurrentUserIdWithoutConsent()).isEqualTo(7L);
    assertThat(providerFor(7L, "PUT").getCurrentUserIdWithoutConsent()).isEqualTo(7L);
    assertThat(providerFor(7L, "PATCH").getCurrentUserIdWithoutConsent()).isEqualTo(7L);
  }

  @Test
  @DisplayName("로그인하지 않았으면 저장 요청도 403 이 아니라 401 이다 (먼저 누구인지부터 본다)")
  void notLoggedInIs401NotForbidden() {
    for (CurrentUserProvider provider :
        new CurrentUserProvider[] {
          providerWithHeader(null, "POST", ""),
          providerWithHeader("Bearer this-token-does-not-exist", "POST", "")
        }) {
      assertThatThrownBy(provider::getCurrentUserId)
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.UNAUTHORIZED);
      assertThatThrownBy(provider::getCurrentUserIdWithoutConsent)
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.UNAUTHORIZED);
    }
  }

  @Test
  void getCanReadButCannotAutomaticallySaveWithoutConsent() {
    CurrentUserProvider provider = providerFor(7L, "GET");
    assertThat(provider.getCurrentUserId()).isEqualTo(7L);
    assertThat(provider.canSaveForCurrentUser()).isFalse();
    agree(7L, ConsentPolicy.CURRENT_VERSION);
    assertThat(provider.canSaveForCurrentUser()).isTrue();
    assertThat(
            providerWithHeader("Bearer " + TEST_TOKEN, "GET", TEST_TOKEN).canSaveForCurrentUser())
        .isTrue();
  }
}
