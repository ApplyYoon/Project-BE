package com.ithopeschool.projectbe.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ithopeschool.projectbe.domain.auth.GoogleIdTokenVerifier.GoogleProfile;
import com.ithopeschool.projectbe.domain.auth.dto.GoogleLoginRequest;
import com.ithopeschool.projectbe.domain.auth.dto.GoogleLoginResponse;
import com.ithopeschool.projectbe.domain.auth.dto.GoogleSignupRequest;
import com.ithopeschool.projectbe.domain.auth.dto.LoginRequest;
import com.ithopeschool.projectbe.domain.auth.dto.LoginResponse;
import com.ithopeschool.projectbe.domain.auth.dto.SignupRequest;
import com.ithopeschool.projectbe.domain.auth.dto.UserResponse;
import com.ithopeschool.projectbe.domain.consent.ConsentRecord;
import com.ithopeschool.projectbe.domain.consent.ConsentRecordRepository;
import com.ithopeschool.projectbe.domain.consent.ConsentService;
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
import java.util.Optional;
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
  @Autowired private ConsentRecordRepository consentRecordRepository;

  // 테스트가 빨리 끝나게 되풀이 횟수는 가장 작은 값으로 둡니다. (횟수와 상관없는 로직을 보는 테스트입니다)
  private final PasswordHasher passwordHasher = new PasswordHasher(PasswordHasher.MIN_ITERATIONS);
  private final TokenHasher tokenHasher = new TokenHasher();
  // 가짜 요청입니다. 테스트에서 Authorization 헤더를 넣어 "이 토큰으로 부른 요청" 을 흉내 냅니다.
  private final MockHttpServletRequest request = new MockHttpServletRequest();
  // 가짜 구글 확인기입니다. 테스트마다 "이 토큰이면 이 사람" 을 정해 둡니다. (진짜 구글은 GoogleIdTokenVerifierTest 에서)
  private final GoogleIdTokenVerifier googleIdTokenVerifier = mock(GoogleIdTokenVerifier.class);

  private AuthService authService;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    CurrentUserProvider currentUserProvider =
        new CurrentUserProvider(
            request, loginTokenRepository, consentRecordRepository, tokenHasher, clock, "");
    authService =
        new AuthService(
            userRepository,
            accountRepository,
            loginTokenRepository,
            passwordHasher,
            tokenHasher,
            currentUserProvider,
            googleIdTokenVerifier,
            new ConsentService(currentUserProvider, consentRecordRepository, clock),
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
        new SignupRequest.Consents(true, true, false, true, "v1"));
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
    assertThat(account.getName()).isNull();
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
            new SignupRequest.Consents(true, true, true, true, "v1"));

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
            new SignupRequest.Consents(true, true, false, true, "v1")));

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

  // ---- 구글로 시작하기 (이슈 #19) ----

  private static final String CREDENTIAL = "google-credential";

  // 가짜 구글 확인기가 CREDENTIAL 을 이 사람으로 확인해 주게 합니다.
  private void googleSays(String subject, String email, String name) {
    given(googleIdTokenVerifier.isEnabled()).willReturn(true);
    given(googleIdTokenVerifier.verify(CREDENTIAL))
        .willReturn(Optional.of(new GoogleProfile(subject, email, name)));
  }

  private static GoogleSignupRequest googleSignupRequest() {
    return new GoogleSignupRequest(
        CREDENTIAL,
        "김티맥",
        "티맥이",
        null,
        null,
        new SignupRequest.Consents(true, true, true, true, "v1"));
  }

  @Test
  @DisplayName("처음 온 구글 계정이면 '가입 필요' 와 구글 이름·이메일을 돌려주고, 계정은 만들지 않는다")
  void newGoogleAccountNeedsSignup() {
    googleSays("google-1", "tmax@gmail.com", "김티맥");

    GoogleLoginResponse response = authService.googleLogin(new GoogleLoginRequest(CREDENTIAL));

    assertThat(response.status()).isEqualTo(GoogleLoginResponse.SIGNUP_REQUIRED);
    assertThat(response.name()).isEmpty();
    assertThat(response.email()).isEqualTo("tmax@gmail.com");
    assertThat(response.token()).isNull();
    assertThat(accountRepository.count()).isZero();
    assertThat(userRepository.count()).isZero();
  }

  @Test
  @DisplayName("구글로 가입하면 비밀번호 없는 계정이 생기고 바로 로그인 토큰을 받는다. 다음부터는 바로 로그인된다")
  void googleSignupThenLogin() {
    googleSays("google-1", "Tmax@Gmail.com", "김티맥");

    LoginResponse signedUp = authService.googleSignup(googleSignupRequest());

    Account account = accountRepository.findByGoogleSubject("google-1").orElseThrow();
    assertThat(account.getEmail()).isEqualTo("tmax@gmail.com");
    assertThat(account.hasPassword()).isFalse();
    assertThat(account.isDiaryAnalysisAgreed()).isTrue();
    assertThat(account.getAgreedAt()).isEqualTo(NOW);
    assertThat(signedUp.user().name()).isEqualTo("티맥이");
    assertThat(
            accountRepository
                .findByUserId(Long.valueOf(signedUp.user().id()))
                .orElseThrow()
                .getName())
        .isNull();
    assertThat(loginTokenRepository.findByTokenHash(tokenHasher.hash(signedUp.token())))
        .isPresent();

    GoogleLoginResponse again = authService.googleLogin(new GoogleLoginRequest(CREDENTIAL));
    assertThat(again.status()).isEqualTo(GoogleLoginResponse.LOGGED_IN);
    assertThat(again.user().id()).isEqualTo(signedUp.user().id());
    assertThat(again.token()).isNotEqualTo(signedUp.token());
  }

  @Test
  @DisplayName("같은 구글 계정으로 두 번 가입하면 409 이다")
  void sameGoogleAccountCannotSignUpTwice() {
    googleSays("google-1", "tmax@gmail.com", "김티맥");
    authService.googleSignup(googleSignupRequest());

    assertThatThrownBy(() -> authService.googleSignup(googleSignupRequest()))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.CONFLICT);
  }

  @Test
  @DisplayName("구글 이메일로 이미 이메일 가입을 했으면 합치지 않고 409 로 '이메일로 로그인' 을 안내한다")
  void emailAccountWithSameEmailIsNotMerged() {
    authService.signup(signupRequest("tmax@gmail.com"));
    googleSays("google-1", "TMAX@gmail.com", "김티맥");

    assertThatThrownBy(() -> authService.googleLogin(new GoogleLoginRequest(CREDENTIAL)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("이메일로 로그인")
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.CONFLICT);
    assertThatThrownBy(() -> authService.googleSignup(googleSignupRequest()))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.CONFLICT);
  }

  @Test
  @DisplayName("구글로 가입한 계정은 이메일·비밀번호로 로그인할 수 없다 (401)")
  void googleAccountCannotUsePasswordLogin() {
    googleSays("google-1", "tmax@gmail.com", "김티맥");
    authService.googleSignup(googleSignupRequest());

    assertThatThrownBy(() -> authService.login(new LoginRequest("tmax@gmail.com", "study2026")))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.UNAUTHORIZED);
  }

  @Test
  @DisplayName("구글 확인에 실패하면 400, 서버에 구글 로그인이 꺼져 있으면 503 이다")
  void googleFailures() {
    given(googleIdTokenVerifier.isEnabled()).willReturn(true);
    given(googleIdTokenVerifier.verify(CREDENTIAL)).willReturn(Optional.empty());
    assertThatThrownBy(() -> authService.googleLogin(new GoogleLoginRequest(CREDENTIAL)))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.VALIDATION_ERROR);

    given(googleIdTokenVerifier.isEnabled()).willReturn(false);
    assertThatThrownBy(() -> authService.googleLogin(new GoogleLoginRequest(CREDENTIAL)))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
  }

  // ---- 동의 기록 · 실명 (이슈 #71) ----

  @Test
  @DisplayName("가입하면 동의 기록이 한 줄 남는다: 동의 시각 · 문구 버전 · 만 14세 확인")
  void signupLeavesAConsentRecord() {
    UserResponse response = authService.signup(signupRequest("tmax@starflow.com"));

    ConsentRecord record =
        consentRecordRepository
            .findFirstByUserIdOrderByIdDesc(Long.valueOf(response.id()))
            .orElseThrow();
    assertThat(record.getAgreedAt()).isEqualTo(NOW);
    assertThat(record.getTextVersion()).isEqualTo("v1");
    assertThat(record.getAgeConfirmedOver14()).isTrue();
    assertThat(consentRecordRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("지금 받는 버전이 아닌 문구로 동의하면 409 이고, 사용자·계정·동의 기록이 하나도 생기지 않는다")
  void signupWithOldTextVersionIsRejected() {
    SignupRequest request =
        new SignupRequest(
            "tmax@starflow.com",
            "study2026",
            "김티맥",
            "티맥이",
            null,
            null,
            new SignupRequest.Consents(true, true, false, true, "v0"));

    assertThatThrownBy(() -> authService.signup(request))
        .isInstanceOf(ApiException.class)
        .hasMessage("동의 문구가 바뀌었어요. 화면을 새로 고쳐서 최신 문구에 동의해 주세요")
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.CONFLICT);
    assertThat(userRepository.count()).isZero();
    assertThat(accountRepository.count()).isZero();
    assertThat(consentRecordRepository.count()).isZero();
  }

  @Test
  @DisplayName("실명 없이(null) 가입해도 되고, 계정의 실명 칸은 비어 있다")
  void signupWithoutRealName() {
    SignupRequest request =
        new SignupRequest(
            "tmax@starflow.com",
            "study2026",
            null,
            "티맥이",
            null,
            null,
            new SignupRequest.Consents(true, true, false, true, "v1"));

    authService.signup(request);

    assertThat(accountRepository.findByEmail("tmax@starflow.com").orElseThrow().getName()).isNull();
  }

  @Test
  @DisplayName("구글로 가입해도 동의 기록이 남고, 낡은 문구 버전이면 409 이며 아무것도 생기지 않는다")
  void googleSignupLeavesAConsentRecordAndChecksVersion() {
    googleSays("google-1", "tmax@gmail.com", "김티맥");
    GoogleSignupRequest oldText =
        new GoogleSignupRequest(
            CREDENTIAL,
            "김티맥",
            "티맥이",
            null,
            null,
            new SignupRequest.Consents(true, true, true, true, "v0"));

    assertThatThrownBy(() -> authService.googleSignup(oldText))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.CONFLICT);
    assertThat(userRepository.count()).isZero();
    assertThat(consentRecordRepository.count()).isZero();

    LoginResponse signedUp = authService.googleSignup(googleSignupRequest());

    ConsentRecord record =
        consentRecordRepository
            .findFirstByUserIdOrderByIdDesc(Long.valueOf(signedUp.user().id()))
            .orElseThrow();
    assertThat(record.getTextVersion()).isEqualTo("v1");
    assertThat(record.getAgeConfirmedOver14()).isTrue();
  }
}
