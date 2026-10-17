package com.ithopeschool.projectbe.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ithopeschool.projectbe.domain.auth.LoginToken;
import com.ithopeschool.projectbe.domain.auth.LoginTokenRepository;
import com.ithopeschool.projectbe.domain.auth.TokenHasher;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.mock.web.MockHttpServletRequest;

// 요청에 담긴 토큰으로 "누구인지" 를 제대로 가려내는지 확인합니다.
// 토큰은 테스트용 DB(H2)에 실제로 넣고, 요청은 가짜(MockHttpServletRequest)로 만듭니다.
@DataJpaTest
class CurrentUserProviderTest {

  private static final Instant NOW = Instant.parse("2026-10-11T06:00:00Z");
  private static final String TEST_TOKEN = "local-test-token";

  @Autowired private LoginTokenRepository loginTokenRepository;

  private final TokenHasher tokenHasher = new TokenHasher();

  // Authorization 헤더에 이 값을 담은 요청이 왔을 때의 CurrentUserProvider 를 만듭니다. (null 이면 헤더 없음)
  private CurrentUserProvider providerFor(String authorizationHeader, String configuredTestToken) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    if (authorizationHeader != null) {
      request.addHeader("Authorization", authorizationHeader);
    }
    return new CurrentUserProvider(
        request,
        loginTokenRepository,
        tokenHasher,
        Clock.fixed(NOW, ZoneOffset.UTC),
        configuredTestToken);
  }

  // 배포 서버처럼 테스트 토큰이 설정되지 않은 경우
  private CurrentUserProvider providerFor(String authorizationHeader) {
    return providerFor(authorizationHeader, "");
  }

  // 사용자 userId 의 토큰을 하나 만들어 DB 에 넣고, 토큰 원문을 돌려줍니다.
  private String saveToken(Long userId, Instant expiresAt) {
    String token = tokenHasher.newToken();
    loginTokenRepository.save(new LoginToken(userId, tokenHasher.hash(token), expiresAt, NOW));
    return token;
  }

  private static void assertUnauthorized(CurrentUserProvider provider, String message) {
    assertThatThrownBy(provider::getCurrentUserId)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.UNAUTHORIZED);
  }

  @Test
  @DisplayName("로그인할 때 받은 토큰을 보내면 그 사용자의 번호를 돌려준다")
  void validTokenGivesItsUser() {
    String token = saveToken(7L, NOW.plus(Duration.ofDays(30)));

    assertThat(providerFor("Bearer " + token).getCurrentUserId()).isEqualTo(7L);
  }

  @Test
  @DisplayName("토큰마다 주인이 다르다 (A 의 토큰으로는 A, B 의 토큰으로는 B)")
  void eachTokenBelongsToItsOwner() {
    String tokenOfA = saveToken(7L, NOW.plus(Duration.ofDays(30)));
    String tokenOfB = saveToken(8L, NOW.plus(Duration.ofDays(30)));

    assertThat(providerFor("Bearer " + tokenOfA).getCurrentUserId()).isEqualTo(7L);
    assertThat(providerFor("Bearer " + tokenOfB).getCurrentUserId()).isEqualTo(8L);
  }

  @Test
  @DisplayName("헤더가 없거나 'Bearer 토큰' 모양이 아니면 401 (로그인이 필요해요)")
  void missingOrMalformedHeaderIsRejected() {
    String token = saveToken(7L, NOW.plus(Duration.ofDays(30)));

    assertUnauthorized(providerFor(null), "로그인이 필요해요");
    assertUnauthorized(providerFor(""), "로그인이 필요해요");
    assertUnauthorized(providerFor("Bearer "), "로그인이 필요해요");
    assertUnauthorized(providerFor("Bearer    "), "로그인이 필요해요");
    // 앞에 Bearer 를 안 붙이면 맞는 토큰이어도 받지 않습니다.
    assertUnauthorized(providerFor(token), "로그인이 필요해요");
    assertUnauthorized(providerFor("Basic " + token), "로그인이 필요해요");
  }

  @Test
  @DisplayName("'bearer' 를 소문자로 적어도 받는다")
  void bearerIsCaseInsensitive() {
    String token = saveToken(7L, NOW.plus(Duration.ofDays(30)));

    assertThat(providerFor("bearer " + token).getCurrentUserId()).isEqualTo(7L);
    assertThat(providerFor("BEARER " + token).getCurrentUserId()).isEqualTo(7L);
  }

  @Test
  @DisplayName("모르는 토큰은 401 (다시 로그인해 주세요)")
  void unknownTokenIsRejected() {
    saveToken(7L, NOW.plus(Duration.ofDays(30)));

    assertUnauthorized(providerFor("Bearer " + tokenHasher.newToken()), "다시 로그인해 주세요");
    assertUnauthorized(providerFor("Bearer fake-token"), "다시 로그인해 주세요");
  }

  @Test
  @DisplayName("DB 에 저장된 해시를 그대로 보내도 로그인되지 않는다 (DB 를 들여다봐도 쓸 수 없다)")
  void storedHashCannotBeUsedAsToken() {
    String token = saveToken(7L, NOW.plus(Duration.ofDays(30)));

    assertUnauthorized(providerFor("Bearer " + tokenHasher.hash(token)), "다시 로그인해 주세요");
  }

  @Test
  @DisplayName("기간이 끝난 토큰은 401 이고, 끝나기 1초 전까지는 쓸 수 있다")
  void expiredTokenIsRejected() {
    String expiredLongAgo = saveToken(7L, NOW.minus(Duration.ofDays(1)));
    String expiresRightNow = saveToken(7L, NOW);
    String oneSecondLeft = saveToken(7L, NOW.plusSeconds(1));

    assertUnauthorized(providerFor("Bearer " + expiredLongAgo), "로그인한 지 오래됐어요. 다시 로그인해 주세요");
    assertUnauthorized(providerFor("Bearer " + expiresRightNow), "로그인한 지 오래됐어요. 다시 로그인해 주세요");
    assertThat(providerFor("Bearer " + oneSecondLeft).getCurrentUserId()).isEqualTo(7L);
  }

  // ---- 내 컴퓨터 전용 테스트 토큰 ----

  @Test
  @DisplayName("테스트 토큰이 설정된 서버(local)에서는 그 토큰이 테스트 사용자(1번)로 통한다")
  void testTokenWorksWhenConfigured() {
    CurrentUserProvider provider = providerFor("Bearer " + TEST_TOKEN, TEST_TOKEN);

    assertThat(provider.getCurrentUserId()).isEqualTo(CurrentUserProvider.TEST_USER_ID);
    assertThat(CurrentUserProvider.TEST_USER_ID).isEqualTo(1L);
  }

  @Test
  @DisplayName("테스트 토큰과 한 글자라도 다르면 통하지 않는다")
  void almostTestTokenIsRejected() {
    assertUnauthorized(providerFor("Bearer local-test-toke", TEST_TOKEN), "다시 로그인해 주세요");
    assertUnauthorized(providerFor("Bearer local-test-token2", TEST_TOKEN), "다시 로그인해 주세요");
    assertUnauthorized(providerFor("Bearer LOCAL-TEST-TOKEN", TEST_TOKEN), "다시 로그인해 주세요");
  }

  @Test
  @DisplayName("테스트 토큰이 설정되지 않은 서버(배포)에서는 그 글자가 통하지 않는다")
  void testTokenIsOffWhenNotConfigured() {
    assertUnauthorized(providerFor("Bearer " + TEST_TOKEN, ""), "다시 로그인해 주세요");
    assertUnauthorized(providerFor("Bearer " + TEST_TOKEN, "   "), "다시 로그인해 주세요");
    assertUnauthorized(providerFor("Bearer " + TEST_TOKEN, null), "다시 로그인해 주세요");
  }

  @Test
  @DisplayName("배포 설정 파일에는 테스트 토큰이 없고, 공통 설정에는 비어 있고, 로컬 설정에만 값이 있다")
  void testTokenIsOnlyInLocalSettings() throws IOException {
    Path resources = Path.of("src", "main", "resources");

    assertThat(Files.readString(resources.resolve("application-prod.yml")))
        .doesNotContain("test-token");
    assertThat(Files.readString(resources.resolve("application.yml"))).contains("test-token: \"\"");
    assertThat(Files.readString(resources.resolve("application-local.yml")))
        .contains("test-token: " + TEST_TOKEN);
  }
}
