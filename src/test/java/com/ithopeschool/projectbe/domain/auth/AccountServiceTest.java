package com.ithopeschool.projectbe.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ithopeschool.projectbe.domain.auth.dto.LoginRequest;
import com.ithopeschool.projectbe.domain.auth.dto.PasswordChangeRequest;
import com.ithopeschool.projectbe.domain.auth.dto.ProfileResponse;
import com.ithopeschool.projectbe.domain.auth.dto.ProfileUpdateRequest;
import com.ithopeschool.projectbe.domain.auth.dto.SignupRequest;
import com.ithopeschool.projectbe.domain.auth.dto.WithdrawRequest;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectColor;
import com.ithopeschool.projectbe.domain.user.UserRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.mock.web.MockHttpServletRequest;

// "내 계정" 로직을 테스트용 DB(H2)에 실제로 넣고 꺼내 보며 확인합니다.
// 두 사람(에이·비이)을 가입시켜 두고, 한 사람이 한 일이 다른 사람에게 번지지 않는지도 같이 봅니다.
@DataJpaTest
class AccountServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-11T06:00:00Z");
  private static final String PASSWORD = "study2026";

  @Autowired private UserRepository userRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private LoginTokenRepository loginTokenRepository;
  @Autowired private EntityManager entityManager;

  // 테스트가 빨리 끝나게 되풀이 횟수는 가장 작은 값으로 둡니다.
  private final PasswordHasher passwordHasher = new PasswordHasher(PasswordHasher.MIN_ITERATIONS);
  private final TokenHasher tokenHasher = new TokenHasher();
  // 가짜 요청입니다. Authorization 헤더를 바꿔 끼워서 "누가 부른 요청인지" 를 흉내 냅니다.
  private MockHttpServletRequest request;
  private AuthService authService;
  private AccountService accountService;

  // 에이가 로그인해서 받은 토큰 (기기 두 대에서 로그인한 것처럼 두 개), 비이의 토큰
  private String tokenOfA;
  private String secondTokenOfA;
  private String tokenOfB;
  private Long idOfA;
  private Long idOfB;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest();
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
    accountService =
        new AccountService(
            currentUserProvider,
            userRepository,
            accountRepository,
            loginTokenRepository,
            passwordHasher,
            entityManager);

    idOfA = Long.valueOf(authService.signup(signupRequest("a@starflow.com", "에이")).id());
    idOfB = Long.valueOf(authService.signup(signupRequest("b@starflow.com", "비이")).id());
    tokenOfA = authService.login(new LoginRequest("a@starflow.com", PASSWORD)).token();
    secondTokenOfA = authService.login(new LoginRequest("a@starflow.com", PASSWORD)).token();
    tokenOfB = authService.login(new LoginRequest("b@starflow.com", PASSWORD)).token();
  }

  private static SignupRequest signupRequest(String email, String nickname) {
    return new SignupRequest(
        email,
        PASSWORD,
        "김티맥",
        nickname,
        "7530560",
        "한국디지털미디어고등학교",
        new SignupRequest.Consents(true, true, false));
  }

  // 이 토큰으로 부른 요청인 것처럼 만듭니다. null 이면 로그인하지 않은 요청입니다.
  private void callWith(String token) {
    request.removeHeader("Authorization");
    if (token != null) {
      request.addHeader("Authorization", "Bearer " + token);
    }
  }

  private static void assertError(Runnable action, ErrorCode code, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(code);
  }

  // ---- 내 정보 보기 ----

  @Test
  @DisplayName("내 정보에는 내 이메일·이름·닉네임·학교·동의가 나오고, 남의 것은 나오지 않는다")
  void profileShowsMine() {
    callWith(tokenOfA);
    ProfileResponse mine = accountService.getProfile();

    assertThat(mine.email()).isEqualTo("a@starflow.com");
    assertThat(mine.name()).isEqualTo("김티맥");
    assertThat(mine.nickname()).isEqualTo("에이");
    assertThat(mine.schoolCode()).isEqualTo("7530560");
    assertThat(mine.schoolName()).isEqualTo("한국디지털미디어고등학교");
    assertThat(mine.diaryAnalysis()).isFalse();

    callWith(tokenOfB);
    assertThat(accountService.getProfile().nickname()).isEqualTo("비이");
  }

  @Test
  @DisplayName("로그인하지 않으면 다섯 가지 모두 401")
  void everythingRequiresLogin() {
    callWith(null);
    String message = "로그인이 필요해요";

    assertError(() -> accountService.getProfile(), ErrorCode.UNAUTHORIZED, message);
    assertError(
        () ->
            accountService.updateProfile(new ProfileUpdateRequest("김티맥", "새별명", null, null, true)),
        ErrorCode.UNAUTHORIZED,
        message);
    assertError(
        () -> accountService.changePassword(new PasswordChangeRequest(PASSWORD, "newpass2026")),
        ErrorCode.UNAUTHORIZED,
        message);
    assertError(() -> accountService.logout(), ErrorCode.UNAUTHORIZED, message);
    assertError(
        () -> accountService.withdraw(new WithdrawRequest(PASSWORD)),
        ErrorCode.UNAUTHORIZED,
        message);
    // 아무것도 바뀌지 않았습니다.
    assertThat(userRepository.count()).isEqualTo(2);
  }

  // ---- 내 정보 고치기 ----

  @Test
  @DisplayName("이름·닉네임·학교·일기 분석 동의를 고치면 DB 에 반영되고, 남의 정보는 그대로다")
  void updateProfileChangesOnlyMine() {
    callWith(tokenOfA);
    ProfileResponse updated =
        accountService.updateProfile(
            new ProfileUpdateRequest("김하늘", "하늘이", "1234567", "별빛고등학교", true));

    assertThat(updated.name()).isEqualTo("김하늘");
    assertThat(updated.nickname()).isEqualTo("하늘이");
    assertThat(updated.schoolName()).isEqualTo("별빛고등학교");
    assertThat(updated.diaryAnalysis()).isTrue();
    // 이메일은 바뀌지 않습니다.
    assertThat(updated.email()).isEqualTo("a@starflow.com");

    entityManager.flush();
    entityManager.clear();
    assertThat(userRepository.findById(idOfA).orElseThrow().getNickname()).isEqualTo("하늘이");
    assertThat(accountRepository.findByUserId(idOfA).orElseThrow().isDiaryAnalysisAgreed())
        .isTrue();
    assertThat(userRepository.findById(idOfB).orElseThrow().getNickname()).isEqualTo("비이");
    assertThat(accountRepository.findByUserId(idOfB).orElseThrow().getName()).isEqualTo("김티맥");
  }

  @Test
  @DisplayName("학교를 null 로 보내면 학교가 지워지고, 선택 동의를 다시 끌 수 있다")
  void schoolCanBeClearedAndConsentWithdrawn() {
    callWith(tokenOfA);
    accountService.updateProfile(
        new ProfileUpdateRequest("김티맥", "에이", "7530560", "한국디지털미디어고등학교", true));

    ProfileResponse updated =
        accountService.updateProfile(new ProfileUpdateRequest("김티맥", "에이", null, null, false));

    assertThat(updated.schoolCode()).isNull();
    assertThat(updated.schoolName()).isNull();
    assertThat(updated.diaryAnalysis()).isFalse();
  }

  // ---- 비밀번호 바꾸기 ----

  @Test
  @DisplayName("지금 비밀번호가 틀리면 400 이고 비밀번호가 바뀌지 않는다 (401 이 아니라서 로그인이 풀리지 않는다)")
  void changePasswordNeedsCurrentPassword() {
    callWith(tokenOfA);

    assertError(
        () -> accountService.changePassword(new PasswordChangeRequest("wrong2026", "newpass2026")),
        ErrorCode.VALIDATION_ERROR,
        "비밀번호가 맞지 않아요");

    assertThat(authService.login(new LoginRequest("a@starflow.com", PASSWORD)).token())
        .isNotBlank();
  }

  @Test
  @DisplayName("비밀번호를 바꾸면 옛 비밀번호로는 로그인되지 않고 새 비밀번호로 로그인된다")
  void changePasswordWorks() {
    callWith(tokenOfA);
    accountService.changePassword(new PasswordChangeRequest(PASSWORD, "newpass2026"));

    assertError(
        () -> authService.login(new LoginRequest("a@starflow.com", PASSWORD)),
        ErrorCode.UNAUTHORIZED,
        "이메일 또는 비밀번호가 맞지 않아요");
    assertThat(authService.login(new LoginRequest("a@starflow.com", "newpass2026")).token())
        .isNotBlank();
    // 저장된 값에 새 비밀번호가 그대로 들어 있지 않습니다.
    assertThat(accountRepository.findByUserId(idOfA).orElseThrow().getPasswordHash())
        .doesNotContain("newpass2026");
  }

  @Test
  @DisplayName("비밀번호를 바꾸면 지금 기기는 그대로이고 다른 기기의 로그인만 풀린다. 남의 로그인은 그대로다")
  void changePasswordLogsOutOtherDevices() {
    callWith(tokenOfA);
    accountService.changePassword(new PasswordChangeRequest(PASSWORD, "newpass2026"));

    // 지금 기기(tokenOfA)는 계속 쓸 수 있습니다.
    assertThat(accountService.getProfile().nickname()).isEqualTo("에이");
    // 다른 기기(secondTokenOfA)는 풀렸습니다.
    callWith(secondTokenOfA);
    assertError(() -> accountService.getProfile(), ErrorCode.UNAUTHORIZED, "다시 로그인해 주세요");
    // 비이의 로그인은 그대로입니다.
    callWith(tokenOfB);
    assertThat(accountService.getProfile().nickname()).isEqualTo("비이");
  }

  // ---- 로그아웃 ----

  @Test
  @DisplayName("로그아웃하면 그 토큰으로는 더 부를 수 없고, 다른 기기와 남의 로그인은 그대로다")
  void logoutDeletesOnlyThisToken() {
    callWith(tokenOfA);
    accountService.logout();

    assertError(() -> accountService.getProfile(), ErrorCode.UNAUTHORIZED, "다시 로그인해 주세요");
    callWith(secondTokenOfA);
    assertThat(accountService.getProfile().nickname()).isEqualTo("에이");
    callWith(tokenOfB);
    assertThat(accountService.getProfile().nickname()).isEqualTo("비이");
  }

  // ---- 탈퇴 ----

  // 에이와 비이에게 과목·계획·공부 기록을 하나씩 만들어 둡니다.
  private void giveBothSomeRecords() {
    for (Long userId : new Long[] {idOfA, idOfB}) {
      Subject subject = new Subject(userId, "수학", SubjectColor.RED);
      entityManager.persist(subject);
      entityManager.persist(
          new StudyPlan(userId, subject.getId(), LocalDate.of(2026, 10, 11), "3단원", 60));
      entityManager.persist(
          new StudySession(userId, subject.getId(), NOW.minusSeconds(3600), NOW, 0));
    }
    entityManager.flush();
  }

  private long countRows(String entityName, Long userId) {
    return entityManager
        .createQuery(
            "select count(e) from " + entityName + " e where e.userId = :userId", Long.class)
        .setParameter("userId", userId)
        .getSingleResult();
  }

  @Test
  @DisplayName("비밀번호가 틀리면 탈퇴되지 않는다 (400, 아무것도 지워지지 않음)")
  void withdrawNeedsPassword() {
    giveBothSomeRecords();
    callWith(tokenOfA);

    assertError(
        () -> accountService.withdraw(new WithdrawRequest("wrong2026")),
        ErrorCode.VALIDATION_ERROR,
        "비밀번호가 맞지 않아요");

    assertThat(userRepository.existsById(idOfA)).isTrue();
    assertThat(countRows("Subject", idOfA)).isEqualTo(1);
    assertThat(accountService.getProfile().nickname()).isEqualTo("에이");
  }

  @Test
  @DisplayName("탈퇴하면 사용자·계정·토큰·과목·계획·공부 기록이 모두 지워지고, 남의 것은 그대로다")
  void withdrawDeletesEverythingOfMine() {
    giveBothSomeRecords();
    callWith(tokenOfA);

    accountService.withdraw(new WithdrawRequest(PASSWORD));
    entityManager.flush();
    entityManager.clear();

    assertThat(userRepository.existsById(idOfA)).isFalse();
    for (String table :
        new String[] {"Account", "LoginToken", "Subject", "StudyPlan", "StudySession"}) {
      assertThat(countRows(table, idOfA)).as("에이의 %s", table).isZero();
      assertThat(countRows(table, idOfB)).as("비이의 %s", table).isEqualTo(1);
    }
    assertThat(userRepository.existsById(idOfB)).isTrue();
  }

  @Test
  @DisplayName("탈퇴한 뒤에는 그 토큰과 이메일·비밀번호가 통하지 않고, 같은 이메일로 다시 가입할 수 있다")
  void afterWithdraw() {
    callWith(tokenOfA);
    accountService.withdraw(new WithdrawRequest(PASSWORD));
    entityManager.flush();
    entityManager.clear();

    assertError(() -> accountService.getProfile(), ErrorCode.UNAUTHORIZED, "다시 로그인해 주세요");
    callWith(secondTokenOfA);
    assertError(() -> accountService.getProfile(), ErrorCode.UNAUTHORIZED, "다시 로그인해 주세요");
    assertError(
        () -> authService.login(new LoginRequest("a@starflow.com", PASSWORD)),
        ErrorCode.UNAUTHORIZED,
        "이메일 또는 비밀번호가 맞지 않아요");

    assertThat(authService.signup(signupRequest("a@starflow.com", "다시 온 에이")).name())
        .isEqualTo("다시 온 에이");
  }

  @Test
  @DisplayName("탈퇴할 때 지우는 대상은 'userId 가 있는 모든 테이블' 이다 (지금 5개)")
  void everyTableWithUserIdIsCovered() {
    // 새 테이블을 만들면 이 숫자가 늘어납니다. 그 테이블에 userId 가 있으면 탈퇴 때 자동으로 지워집니다.
    long tablesWithUserId =
        entityManager.getMetamodel().getEntities().stream()
            .filter(e -> e.getAttributes().stream().anyMatch(a -> a.getName().equals("userId")))
            .count();
    long allTables = entityManager.getMetamodel().getEntities().size();

    // users 하나만 userId 가 없습니다.
    assertThat(tablesWithUserId).isEqualTo(allTables - 1);
  }
}
