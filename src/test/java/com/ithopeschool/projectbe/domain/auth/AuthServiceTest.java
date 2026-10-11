package com.ithopeschool.projectbe.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ithopeschool.projectbe.domain.auth.dto.LoginRequest;
import com.ithopeschool.projectbe.domain.auth.dto.LoginResponse;
import com.ithopeschool.projectbe.domain.auth.dto.SignupRequest;
import com.ithopeschool.projectbe.domain.auth.dto.UserResponse;
import com.ithopeschool.projectbe.domain.user.User;
import com.ithopeschool.projectbe.domain.user.UserRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.mock.web.MockHttpServletRequest;

// 가입·로그인 로직을 테스트용 DB(H2)에 실제로 넣고 꺼내 보며 확인합니다.
// 시계는 한 시각에 고정해서, "토큰이 30일 뒤에 끝난다" 같은 것을 정확히 비교합니다.
@DataJpaTest
class AuthServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-11T06:00:00Z");

  @Autowired private UserRepository userRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private LoginTokenRepository loginTokenRepository;

  // 테스트가 빨리 끝나게 되풀이 횟수는 가장 작은 값으로 둡니다. (횟수와 상관없는 로직을 보는 테스트입니다)
  private final PasswordHasher passwordHasher = new PasswordHasher(PasswordHasher.MIN_ITERATIONS);
  private final TokenHasher tokenHasher = new TokenHasher();
  // 가짜 요청입니다. 테스트에서 Authorization 헤더를 넣어 "이 토큰으로 부른 요청" 을 흉내 냅니다.
  private final MockHttpServletRequest request = new MockHttpServletRequest();
  private AuthService authService;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    CurrentUserProvider currentUserProvider =
        new CurrentUserProvider(request, loginTokenRepository, tokenHasher, clock, "");
    authService =
        new AuthService(
            userRepository,
            accountRepository,
            loginTokenRepository,
            passwordHasher,
            tokenHasher,
            currentUserProvider,
            clock);
  }

  // 테스트마다 쓰는 가입 요청입니다. 이메일만 바꿔 가며 씁니다.
  private static SignupRequest signupRequest(String email) {
    return new SignupRequest(
        email,
        "study2026",
        "김티맥",
        "티맥이",
        "7530560",
        "한국디지털미디어고등학교",
        new SignupRequest.Consents(true, true, false));
  }

  // ---- 가입 ----

  @Test
  @DisplayName("가입하면 users 와 accounts 에 한 줄씩 생기고, 번호(글자)와 별명을 돌려준다")
  void signupCreatesUserAndAccount() {
    UserResponse response = authService.signup(signupRequest("tmax@starflow.com"));

    User user = userRepository.findAll().get(0);
    assertThat(response.id()).isEqualTo(String.valueOf(user.getId()));
    assertThat(response.name()).isEqualTo("티맥이");
    assertThat(user.getNickname()).isEqualTo("티맥이");

    Account account = accountRepository.findByEmail("tmax@starflow.com").orElseThrow();
    assertThat(account.getUserId()).isEqualTo(user.getId());
    assertThat(account.getName()).isEqualTo("김티맥");
    assertThat(account.getSchoolCode()).isEqualTo("7530560");
    assertThat(account.getSchoolName()).isEqualTo("한국디지털미디어고등학교");
    assertThat(account.isDiaryAnalysisAgreed()).isFalse();
    assertThat(account.getAgreedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("비밀번호는 그대로 저장되지 않고, 해시로 저장된다")
  void passwordIsStoredAsHash() {
    authService.signup(signupRequest("tmax@starflow.com"));

    Account account = accountRepository.findByEmail("tmax@starflow.com").orElseThrow();
    assertThat(account.getPasswordHash()).doesNotContain("study2026");
    assertThat(passwordHasher.matches("study2026", account.getPasswordHash())).isTrue();
  }

  @Test
  @DisplayName("이메일은 소문자로 바꿔 저장한다")
  void emailIsStoredInLowerCase() {
    authService.signup(signupRequest("Tmax@StarFlow.com"));

    assertThat(accountRepository.existsByEmail("tmax@starflow.com")).isTrue();
    assertThat(accountRepository.existsByEmail("Tmax@StarFlow.com")).isFalse();
  }

  @Test
  @DisplayName("학교를 고르지 않아도(null) 가입된다")
  void signupWithoutSchool() {
    SignupRequest request =
        new SignupRequest(
            "tmax@starflow.com",
            "study2026",
            "김티맥",
            "티맥이",
            null,
            null,
            new SignupRequest.Consents(true, true, true));

    authService.signup(request);

    Account account = accountRepository.findByEmail("tmax@starflow.com").orElseThrow();
    assertThat(account.getSchoolCode()).isNull();
    assertThat(account.getSchoolName()).isNull();
    assertThat(account.isDiaryAnalysisAgreed()).isTrue();
  }

  @Test
  @DisplayName("같은 이메일로 다시 가입하면 409 이고, 사용자가 더 생기지 않는다 (대문자·소문자만 달라도 같은 이메일)")
  void duplicateEmailIsRejected() {
    authService.signup(signupRequest("tmax@starflow.com"));

    assertThatThrownBy(() -> authService.signup(signupRequest("TMAX@starflow.com")))
        .isInstanceOf(ApiException.class)
        .hasMessage("이미 가입한 이메일이에요")
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.CONFLICT);

    assertThat(userRepository.count()).isEqualTo(1);
    assertThat(accountRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("별명이 같은 두 사람이 다른 이메일로 가입할 수 있다")
  void sameNicknameDifferentEmail() {
    UserResponse first = authService.signup(signupRequest("one@starflow.com"));
    UserResponse second = authService.signup(signupRequest("two@starflow.com"));

    assertThat(first.id()).isNotEqualTo(second.id());
    assertThat(accountRepository.count()).isEqualTo(2);
  }

  // ---- 로그인 ----

  @Test
  @DisplayName("맞는 이메일·비밀번호로 로그인하면 토큰과 내 정보를 돌려준다")
  void loginReturnsTokenAndUser() {
    UserResponse signedUp = authService.signup(signupRequest("tmax@starflow.com"));

    LoginResponse response = authService.login(new LoginRequest("tmax@starflow.com", "study2026"));

    assertThat(response.token()).hasSize(43);
    assertThat(response.user()).isEqualTo(signedUp);
  }

  @Test
  @DisplayName("DB 에는 토큰 원문이 아니라 해시가 저장되고, 30일 뒤에 끝난다")
  void tokenIsStoredAsHashWithExpiry() {
    UserResponse signedUp = authService.signup(signupRequest("tmax@starflow.com"));

    LoginResponse response = authService.login(new LoginRequest("tmax@starflow.com", "study2026"));

    List<LoginToken> tokens = loginTokenRepository.findAll();
    assertThat(tokens).hasSize(1);
    LoginToken saved = tokens.get(0);
    assertThat(saved.getTokenHash()).isNotEqualTo(response.token());
    assertThat(saved.getTokenHash()).isEqualTo(tokenHasher.hash(response.token()));
    assertThat(String.valueOf(saved.getUserId())).isEqualTo(signedUp.id());
    assertThat(saved.getCreatedAt()).isEqualTo(NOW);
    assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
  }

  @Test
  @DisplayName("로그인할 때는 이메일의 대문자·소문자와 앞뒤 공백을 가리지 않는다")
  void loginIgnoresEmailCaseAndSpaces() {
    authService.signup(signupRequest("tmax@starflow.com"));

    LoginResponse response =
        authService.login(new LoginRequest("  TMAX@Starflow.com ", "study2026"));

    assertThat(response.token()).isNotBlank();
  }

  @Test
  @DisplayName("로그인할 때마다 새 토큰을 준다 (휴대폰과 컴퓨터에서 따로 로그인할 수 있다)")
  void everyLoginGivesANewToken() {
    authService.signup(signupRequest("tmax@starflow.com"));

    LoginResponse first = authService.login(new LoginRequest("tmax@starflow.com", "study2026"));
    LoginResponse second = authService.login(new LoginRequest("tmax@starflow.com", "study2026"));

    assertThat(first.token()).isNotEqualTo(second.token());
    assertThat(loginTokenRepository.count()).isEqualTo(2);
  }

  @Test
  @DisplayName("비밀번호가 틀리면 401 이고 토큰을 만들지 않는다")
  void wrongPasswordIsRejected() {
    authService.signup(signupRequest("tmax@starflow.com"));

    assertThatThrownBy(() -> authService.login(new LoginRequest("tmax@starflow.com", "study2025")))
        .isInstanceOf(ApiException.class)
        .hasMessage("이메일 또는 비밀번호가 맞지 않아요")
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.UNAUTHORIZED);

    assertThat(loginTokenRepository.count()).isZero();
  }

  @Test
  @DisplayName("가입하지 않은 이메일도 비밀번호가 틀렸을 때와 똑같은 401·같은 문장이다 (가입 여부가 새지 않게)")
  void unknownEmailLooksTheSameAsWrongPassword() {
    assertThatThrownBy(
            () -> authService.login(new LoginRequest("nobody@starflow.com", "study2026")))
        .isInstanceOf(ApiException.class)
        .hasMessage("이메일 또는 비밀번호가 맞지 않아요")
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.UNAUTHORIZED);
  }

  @Test
  @DisplayName("다른 사람의 비밀번호로는 로그인할 수 없다")
  void cannotLoginWithSomeoneElsesPassword() {
    authService.signup(signupRequest("one@starflow.com"));
    authService.signup(
        new SignupRequest(
            "two@starflow.com",
            "other2026",
            "이별빛",
            "별빛",
            null,
            null,
            new SignupRequest.Consents(true, true, false)));

    assertThatThrownBy(() -> authService.login(new LoginRequest("one@starflow.com", "other2026")))
        .isInstanceOf(ApiException.class);
    assertThat(authService.login(new LoginRequest("two@starflow.com", "other2026")).user().name())
        .isEqualTo("별빛");
  }

  // ---- 내 정보 ----

  @Test
  @DisplayName("로그인해서 받은 토큰으로 부르면 내 번호와 별명을 돌려준다")
  void meReturnsTheLoggedInUser() {
    UserResponse signedUp = authService.signup(signupRequest("tmax@starflow.com"));
    LoginResponse loggedIn = authService.login(new LoginRequest("tmax@starflow.com", "study2026"));
    request.addHeader("Authorization", "Bearer " + loggedIn.token());

    assertThat(authService.me()).isEqualTo(signedUp);
  }

  @Test
  @DisplayName("토큰 없이 부르면 401")
  void meWithoutTokenIsRejected() {
    authService.signup(signupRequest("tmax@starflow.com"));

    assertThatThrownBy(() -> authService.me())
        .isInstanceOf(ApiException.class)
        .hasMessage("로그인이 필요해요")
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.UNAUTHORIZED);
  }

  @Test
  @DisplayName("토큰은 맞는데 사용자가 지워졌으면 401 (다시 로그인하게 한다)")
  void meWhenUserIsGone() {
    authService.signup(signupRequest("tmax@starflow.com"));
    LoginResponse loggedIn = authService.login(new LoginRequest("tmax@starflow.com", "study2026"));
    request.addHeader("Authorization", "Bearer " + loggedIn.token());
    userRepository.deleteAll();

    assertThatThrownBy(() -> authService.me())
        .isInstanceOf(ApiException.class)
        .hasMessage("다시 로그인해 주세요");
  }
}
