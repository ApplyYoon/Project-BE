package com.ithopeschool.projectbe.global.auth;

import com.ithopeschool.projectbe.domain.auth.LoginToken;
import com.ithopeschool.projectbe.domain.auth.LoginTokenRepository;
import com.ithopeschool.projectbe.domain.auth.TokenHasher;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// "지금 요청한 사용자가 누구인지" 알려 주는 곳입니다. 이 프로젝트에서 이 일은 여기 한 곳에서만 합니다.
//
// 요청 헤더에 담긴 로그인 토큰을 보고 사용자 번호를 돌려줍니다. (이슈 #20)
//   Authorization: Bearer 토큰     ← FE 가 로그인할 때 받은 토큰을 요청마다 이렇게 보냅니다
// 토큰이 없거나, 모르는 토큰이거나, 기간이 끝났으면 401 을 던집니다. 그러면 FE 가 로그인 화면으로 보냅니다.
//
// 다른 API 는 user_id 가 필요하면 숫자를 직접 쓰거나 요청에서 받지 말고, 반드시 getCurrentUserId() 를 부릅니다.
// 이 메서드를 부르는 것이 곧 그 API 를 "로그인한 사람만 쓸 수 있게" 만드는 방법입니다.
//
// 사용 예 (Service 에서):
//   private final CurrentUserProvider currentUserProvider;
//   Long userId = currentUserProvider.getCurrentUserId();
@Component
public class CurrentUserProvider {

  // 테스트 토큰으로 들어왔을 때의 사용자 번호입니다. (내 컴퓨터에서만 통합니다)
  // 로컬에서는 서버가 켜질 때 이 번호의 테스트 사용자를 만들어 둡니다. (LocalTestUserInitializer)
  public static final Long TEST_USER_ID = 1L;

  // 헤더 값은 "Bearer " 로 시작하고 그 뒤에 토큰이 옵니다.
  private static final String BEARER_PREFIX = "Bearer ";

  // 지금 처리하고 있는 요청입니다. Spring 이 요청마다 알맞은 것으로 바꿔 끼워 줍니다.
  private final HttpServletRequest request;
  private final LoginTokenRepository loginTokenRepository;
  private final TokenHasher tokenHasher;
  // "지금 몇 시인지" 는 시계에게 묻습니다. 테스트에서 시각을 고정할 수 있습니다. (global/config/ClockConfig)
  private final Clock clock;
  // 내 컴퓨터 전용 테스트 토큰. application-local.yml 에만 값이 있고, 배포 서버에서는 비어 있습니다.
  private final String testToken;

  public CurrentUserProvider(
      HttpServletRequest request,
      LoginTokenRepository loginTokenRepository,
      TokenHasher tokenHasher,
      Clock clock,
      @Value("${app.auth.test-token:}") String testToken) {
    this.request = request;
    this.loginTokenRepository = loginTokenRepository;
    this.tokenHasher = tokenHasher;
    this.clock = clock;
    this.testToken = testToken;
  }

  // 지금 요청한 사용자의 번호를 돌려줍니다. 로그인하지 않은 요청이면 401 을 던집니다.
  public Long getCurrentUserId() {
    String token = readToken();
    if (token == null) {
      throw new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
    }

    if (isTestToken(token)) {
      return TEST_USER_ID;
    }

    // DB 에는 토큰의 해시만 있습니다. 받은 토큰을 같은 방법으로 바꿔서 찾습니다.
    LoginToken loginToken =
        loginTokenRepository
            .findByTokenHash(tokenHasher.hash(token))
            .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "다시 로그인해 주세요"));

    // 끝나는 시각이 "지금보다 뒤" 일 때만 쓸 수 있습니다. 딱 그 시각이 되면 끝난 것으로 봅니다.
    if (!loginToken.getExpiresAt().isAfter(Instant.now(clock))) {
      throw new ApiException(ErrorCode.UNAUTHORIZED, "로그인한 지 오래됐어요. 다시 로그인해 주세요");
    }
    return loginToken.getUserId();
  }

  // 요청 헤더에서 토큰을 꺼냅니다. 헤더가 없거나 모양이 다르면 null 입니다.
  //   "Bearer abc123" -> "abc123"   /   헤더 없음 · "abc123" · "Bearer " -> null
  private String readToken() {
    String header = request.getHeader("Authorization");
    // regionMatches(true, ...): 앞부분이 "Bearer " 인지 대문자·소문자를 가리지 않고 봅니다.
    if (header == null
        || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
      return null;
    }
    String token = header.substring(BEARER_PREFIX.length()).trim();
    return token.isEmpty() ? null : token;
  }

  // 내 컴퓨터 전용 테스트 토큰인지 확인합니다. 설정이 비어 있으면(배포 서버) 어떤 값도 통하지 않습니다.
  private boolean isTestToken(String token) {
    if (testToken == null || testToken.isBlank()) {
      return false;
    }
    // equals 대신 isEqual: 몇 글자까지 맞았는지가 응답 시간에 드러나지 않게 합니다.
    return MessageDigest.isEqual(
        token.getBytes(StandardCharsets.UTF_8), testToken.getBytes(StandardCharsets.UTF_8));
  }
}
