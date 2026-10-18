package com.ithopeschool.projectbe.domain.auth;

import com.ithopeschool.projectbe.domain.auth.dto.LoginResponse;
import com.ithopeschool.projectbe.domain.consent.ConsentPolicy;
import com.ithopeschool.projectbe.domain.consent.ConsentService;
import com.ithopeschool.projectbe.domain.user.User;
import com.ithopeschool.projectbe.domain.user.UserRepository;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 시연 코드 로그인의 로직입니다. (2026-10-18 멘토 결정)
// 앱(Project-App) 안의 웹 화면에서는 구글 로그인이 막혀 있어서, 시연 때 진짜 서버 · GPT 를 쓰려고 만든 "임시 문" 입니다.
// 정해 둔 코드를 넣으면 "시연 계정" 하나로 로그인합니다. 누가 넣어도 같은 계정이라 기록이 이어집니다.
//
//   코드     : 환경변수 DEMO_LOGIN_CODE (application-prod.yml). 값은 코드 · 설정 파일에 절대 적지 않습니다.
//   꺼짐     : 코드가 비어 있거나 8자보다 짧으면 이 기능은 없는 것처럼 404 입니다. 시연이 끝나면 환경변수를 지워서 끕니다.
//   틀리면   : 401. 같은 곳(IP)에서 1분에 5번, 모두 합쳐 1분에 30번 넘게 틀리면 429 (DemoLoginLimiter)
//   시연 계정 : 처음 맞힐 때 한 번만 만듭니다. 이메일 · 이름은 가짜인 것이 분명한 값입니다. (새 테이블 · 칸 없이 users · accounts 그대로)
//
// ⚠️ 코드 값은 로그에 찍지 않습니다. 비교는 "걸리는 시간이 같은 비교" 로 합니다.
//    (보통 비교는 앞 글자가 다르면 바로 끝나서, 걸린 시간으로 몇 글자가 맞았는지 알아낼 수 있기 때문입니다)
@Service
public class DemoLoginService {

  private static final Logger log = LoggerFactory.getLogger(DemoLoginService.class);

  // 이보다 짧은 코드는 쉽게 맞힐 수 있어서 기능을 켜지 않습니다.
  static final int MIN_CODE_LENGTH = 8;
  // 시연 계정. ".invalid" 는 실제로 쓸 수 없는 주소로 정해진 끝말이라 진짜 사람의 메일과 겹치지 않습니다.
  static final String DEMO_EMAIL = "demo@starflow.invalid";
  static final String DEMO_NAME = "시연 학생";

  private static final String WRONG_CODE_MESSAGE = "시연 코드가 맞지 않아요";
  private static final String TOO_MANY_MESSAGE = "너무 많이 틀렸어요. 1분 뒤에 다시 해 주세요";

  // 맞는 코드를 SHA-256 으로 바꾼 값. 기능이 꺼져 있으면 null 입니다.
  private final byte[] codeDigest;
  private final DemoLoginLimiter limiter;
  private final AccountRepository accountRepository;
  private final UserRepository userRepository;
  private final ConsentService consentService;
  private final AuthService authService;
  private final Clock clock;

  public DemoLoginService(
      @Value("${app.auth.demo-login-code:}") String code,
      DemoLoginLimiter limiter,
      AccountRepository accountRepository,
      UserRepository userRepository,
      ConsentService consentService,
      AuthService authService,
      Clock clock) {
    String trimmed = code == null ? "" : code.trim();
    if (!trimmed.isEmpty() && trimmed.length() < MIN_CODE_LENGTH) {
      // 코드 값은 찍지 않고, 왜 꺼졌는지만 남깁니다.
      log.warn("DEMO_LOGIN_CODE 가 {}자보다 짧아서 시연 코드 로그인을 켜지 않았어요", MIN_CODE_LENGTH);
    }
    this.codeDigest = trimmed.length() >= MIN_CODE_LENGTH ? sha256(trimmed) : null;
    this.limiter = limiter;
    this.accountRepository = accountRepository;
    this.userRepository = userRepository;
    this.consentService = consentService;
    this.authService = authService;
    this.clock = clock;
  }

  // 코드가 맞으면 시연 계정의 로그인 토큰을 돌려줍니다. (응답 모양은 이메일 · 구글 로그인과 같습니다)
  //   place : 요청한 곳(IP). 틀린 횟수를 셀 때 씁니다. (placeOf)
  @Transactional
  public LoginResponse login(String code, String place) {
    if (codeDigest == null) {
      throw new ApiException(ErrorCode.NOT_FOUND, "시연 코드 로그인이 꺼져 있어요");
    }
    if (limiter.isBlocked(place)) {
      throw new ApiException(ErrorCode.TOO_MANY_REQUESTS, TOO_MANY_MESSAGE);
    }
    if (!MessageDigest.isEqual(codeDigest, sha256(code.trim()))) {
      limiter.recordFail(place);
      throw new ApiException(ErrorCode.UNAUTHORIZED, WRONG_CODE_MESSAGE);
    }
    return authService.issueToken(findOrCreateDemoUser());
  }

  // 요청한 곳(IP). Render 는 앞에 중계 서버가 있어서 진짜 주소가 X-Forwarded-For 의 첫 값에 옵니다.
  // 이 머리말은 보내는 쪽이 바꿀 수도 있지만, 모두 합친 한도(1분 30번)가 따로 있어서 괜찮습니다.
  //   placeOf("1.2.3.4, 10.0.0.1", "10.0.0.1") -> "1.2.3.4"
  //   placeOf(null, "10.0.0.1")               -> "10.0.0.1"
  public static String placeOf(String forwardedFor, String remoteAddr) {
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return remoteAddr == null ? "" : remoteAddr;
  }

  // 시연 계정을 찾고, 없으면 만듭니다. 만들 때 가입과 같이 동의 기록도 한 줄 남깁니다.
  // (동의 기록이 있어야 저장하는 요청을 쓸 수 있습니다. CurrentUserProvider 의 동의 검사)
  private User findOrCreateDemoUser() {
    Optional<Account> found = accountRepository.findByEmail(DEMO_EMAIL);
    if (found.isPresent()) {
      return userRepository
          .findById(found.get().getUserId())
          // 계정은 있는데 사용자가 없는 것은 데이터가 깨진 경우입니다.
          .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT, "시연 계정을 찾지 못했어요"));
    }

    User user = userRepository.save(new User(DEMO_NAME));
    // 비밀번호도 구글 번호도 없는 계정이라 이메일 · 구글 로그인으로는 들어올 수 없고, 시연 코드로만 들어옵니다.
    // 학교는 비워 둡니다. 시연할 때 설정의 내 정보에서 고르면 시간표 · 급식이 나옵니다.
    Account account =
        new Account(
            user.getId(), DEMO_EMAIL, null, DEMO_NAME, null, null, false, Instant.now(clock));
    try {
      accountRepository.saveAndFlush(account);
    } catch (DataIntegrityViolationException e) {
      // 처음 두 사람이 동시에 맞힌 경우입니다. 다른 쪽이 먼저 만들었으니 다시 눌러 달라고 합니다.
      throw new ApiException(ErrorCode.CONFLICT, "시연 계정을 만드는 중이에요. 다시 눌러 주세요");
    }
    consentService.recordAtSignup(user.getId(), ConsentPolicy.CURRENT_VERSION);
    return user;
  }

  // 글자를 SHA-256 으로 바꿉니다. 길이가 늘 32바이트라서 MessageDigest.isEqual 로 비교할 때 길이가 드러나지 않습니다.
  private static byte[] sha256(String text) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      // 자바에는 SHA-256 이 항상 들어 있어서 실제로는 생기지 않습니다.
      throw new IllegalStateException(e);
    }
  }
}
