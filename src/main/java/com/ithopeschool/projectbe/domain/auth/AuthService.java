package com.ithopeschool.projectbe.domain.auth;

import com.ithopeschool.projectbe.domain.auth.GoogleIdTokenVerifier.GoogleProfile;
import com.ithopeschool.projectbe.domain.auth.dto.GoogleLoginRequest;
import com.ithopeschool.projectbe.domain.auth.dto.GoogleLoginResponse;
import com.ithopeschool.projectbe.domain.auth.dto.GoogleSignupRequest;
import com.ithopeschool.projectbe.domain.auth.dto.LoginRequest;
import com.ithopeschool.projectbe.domain.auth.dto.LoginResponse;
import com.ithopeschool.projectbe.domain.auth.dto.SignupRequest;
import com.ithopeschool.projectbe.domain.auth.dto.UserResponse;
import com.ithopeschool.projectbe.domain.consent.ConsentService;
import com.ithopeschool.projectbe.domain.user.User;
import com.ithopeschool.projectbe.domain.user.UserRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 이메일 가입과 로그인의 실제 로직입니다. (이슈 #97)
//
//   가입   : 이메일이 처음인지 확인 → 비밀번호를 해시로 바꿈 → users 와 accounts 에 한 줄씩 저장
//   로그인 : 이메일로 계정을 찾음 → 비밀번호가 맞는지 확인 → 새 토큰을 만들어 해시만 저장하고, 토큰은 FE 에 돌려줌
//   내 정보 : 요청에 담긴 토큰으로 누구인지 알아내서(CurrentUserProvider) 그 사람의 번호와 별명을 돌려줌
//   구글   : 구글 신분증(ID 토큰)을 구글에 확인 → 가입한 계정이면 로그인, 처음이면 "가입 필요" (이슈 #19)
//
// ⚠️ 이메일·비밀번호·토큰은 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class AuthService {

  // 로그인 토큰을 쓸 수 있는 기간(일). 이 기간이 지나면 다시 로그인해야 합니다.
  public static final int TOKEN_VALID_DAYS = 30;

  // 로그인 실패 문장. 이메일이 없을 때와 비밀번호가 틀렸을 때 똑같이 씁니다.
  // 문장이 다르면 "이 이메일은 가입돼 있다" 는 사실을 남이 알아낼 수 있기 때문입니다.
  private static final String LOGIN_FAIL_MESSAGE = "이메일 또는 비밀번호가 맞지 않아요";

  // 구글 확인에 실패했을 때의 문장. 가짜이거나 한 시간이 지나 끝난 구글 확인 정보일 때입니다.
  private static final String GOOGLE_FAIL_MESSAGE = "구글 확인에 실패했어요. 다시 Google로 시작해 주세요";
  // 구글 이메일로 이미 이메일 가입을 한 사람일 때의 문장입니다.
  // 자동으로 합치지 않습니다. 이메일 가입은 메일 인증이 없어서, 합치면 남이 만든 계정에 들어갈 수 있기 때문입니다.
  private static final String EMAIL_ACCOUNT_EXISTS_MESSAGE = "이미 이메일로 가입한 주소예요. 이메일로 로그인해 주세요";
  private static final String GOOGLE_ACCOUNT_EXISTS_MESSAGE =
      "이미 가입한 구글 계정이에요. 로그인 화면에서 Google로 시작해 주세요";

  private final UserRepository userRepository;
  private final AccountRepository accountRepository;
  private final LoginTokenRepository loginTokenRepository;
  private final PasswordHasher passwordHasher;
  private final TokenHasher tokenHasher;
  private final CurrentUserProvider currentUserProvider;
  private final GoogleIdTokenVerifier googleIdTokenVerifier;
  // 가입할 때 동의 문구 버전을 확인하고 동의 기록을 남깁니다. (이슈 #71)
  private final ConsentService consentService;
  // "지금 몇 시인지" 는 시계에게 묻습니다. 테스트에서 시각을 고정할 수 있습니다. (global/config/ClockConfig)
  private final Clock clock;

  // 가입: 계정만 만들고 로그인은 하지 않습니다. (가입한 뒤 로그인 화면에서 로그인합니다)
  @Transactional
  public UserResponse signup(SignupRequest request) {
    // 동의 문구 버전부터 확인합니다. 낡은 문구를 보여 주고 받은 동의는 저장하지 않습니다. (이슈 #71)
    consentService.checkVersion(request.consents().version());

    String email = normalizeEmail(request.email());
    if (accountRepository.existsByEmail(email)) {
      throw new ApiException(ErrorCode.CONFLICT, "이미 가입한 이메일이에요");
    }

    User user = userRepository.save(new User(request.nickname()));
    Account account =
        new Account(
            user.getId(),
            email,
            passwordHasher.hash(request.password()),
            request.name(),
            request.schoolCode(),
            request.schoolName(),
            request.consents().diaryAnalysis(),
            Instant.now(clock));

    try {
      // saveAndFlush: 지금 바로 DB 에 넣습니다. 그래야 아래 catch 에서 "이메일이 겹쳤다" 는 것을 잡을 수 있습니다.
      accountRepository.saveAndFlush(account);
    } catch (DataIntegrityViolationException e) {
      // 위에서 확인한 직후에 같은 이메일로 다른 가입이 먼저 들어온 경우입니다. (두 번 눌렀을 때 등)
      // DB 의 "이메일은 겹칠 수 없다" 규칙이 막아 준 것이고, 예외가 나가면서 위에서 만든 users 한 줄도 같이 취소됩니다.
      throw new ApiException(ErrorCode.CONFLICT, "이미 가입한 이메일이에요");
    }

    // 계정을 만든 같은 트랜잭션 안에서 동의 기록 한 줄을 남깁니다. 어느 한쪽이 실패하면 둘 다 취소됩니다.
    consentService.recordAtSignup(user.getId(), request.consents().version());
    return UserResponse.from(user);
  }

  // 로그인: 맞으면 새 토큰을 돌려주고, 틀리면 401 입니다.
  @Transactional
  public LoginResponse login(LoginRequest request) {
    Optional<Account> found = accountRepository.findByEmail(normalizeEmail(request.email()));
    if (found.isEmpty()) {
      // 계정이 없어도 비밀번호 계산을 한 번 합니다. 바로 답하면 응답이 눈에 띄게 빨라서,
      // 응답 시간만 보고도 "가입 안 된 이메일" 임을 알아낼 수 있기 때문입니다.
      passwordHasher.hash(request.password());
      throw new ApiException(ErrorCode.UNAUTHORIZED, LOGIN_FAIL_MESSAGE);
    }

    Account account = found.get();
    // 구글로 가입한 계정은 비밀번호가 없어서 이메일·비밀번호로는 로그인할 수 없습니다. (같은 실패 문장)
    if (!account.hasPassword()
        || !passwordHasher.matches(request.password(), account.getPasswordHash())) {
      throw new ApiException(ErrorCode.UNAUTHORIZED, LOGIN_FAIL_MESSAGE);
    }

    User user =
        userRepository
            .findById(account.getUserId())
            // 계정은 있는데 사용자가 없는 것은 데이터가 깨진 경우입니다. 로그인 실패와 같은 답을 줍니다.
            .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, LOGIN_FAIL_MESSAGE));

    return issueToken(user);
  }

  // 구글로 시작하기: 가입한 구글 계정이면 로그인 토큰을, 처음 온 구글 계정이면 "가입 필요" 를 돌려줍니다.
  // 처음 온 사람은 아직 약관에 동의하지 않아서 여기서 계정을 만들지 않습니다. (동의 전에는 개인정보를 저장하지 않기)
  @Transactional
  public GoogleLoginResponse googleLogin(GoogleLoginRequest request) {
    GoogleProfile profile = verifyGoogle(request.credential());

    Optional<Account> found = accountRepository.findByGoogleSubject(profile.subject());
    if (found.isPresent()) {
      User user =
          userRepository
              .findById(found.get().getUserId())
              // 계정은 있는데 사용자가 없는 것은 데이터가 깨진 경우입니다. 구글 확인 실패와 같은 답을 줍니다.
              .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR, GOOGLE_FAIL_MESSAGE));
      return GoogleLoginResponse.loggedIn(issueToken(user));
    }

    if (accountRepository.existsByEmail(normalizeEmail(profile.email()))) {
      throw new ApiException(ErrorCode.CONFLICT, EMAIL_ACCOUNT_EXISTS_MESSAGE);
    }
    return GoogleLoginResponse.signupRequired("", profile.email());
  }

  // 구글로 가입하기: 가입 화면에서 받은 정보와 동의로 계정을 만들고, 바로 로그인 토큰을 돌려줍니다.
  // 이메일은 구글이 확인한 값을 씁니다. (요청에 이메일 칸이 없습니다)
  @Transactional
  public LoginResponse googleSignup(GoogleSignupRequest request) {
    // 동의 문구 버전부터 확인합니다. 낡은 문구를 보여 주고 받은 동의는 저장하지 않습니다. (이슈 #71)
    consentService.checkVersion(request.consents().version());

    GoogleProfile profile = verifyGoogle(request.credential());

    if (accountRepository.existsByGoogleSubject(profile.subject())) {
      throw new ApiException(ErrorCode.CONFLICT, GOOGLE_ACCOUNT_EXISTS_MESSAGE);
    }
    String email = normalizeEmail(profile.email());
    if (accountRepository.existsByEmail(email)) {
      throw new ApiException(ErrorCode.CONFLICT, EMAIL_ACCOUNT_EXISTS_MESSAGE);
    }

    User user = userRepository.save(new User(request.nickname()));
    Account account =
        Account.google(
            user.getId(),
            profile.subject(),
            email,
            request.name(),
            request.schoolCode(),
            request.schoolName(),
            request.consents().diaryAnalysis(),
            Instant.now(clock));
    try {
      accountRepository.saveAndFlush(account);
    } catch (DataIntegrityViolationException e) {
      // 위에서 확인한 직후에 같은 구글 계정으로 다른 가입이 먼저 들어온 경우입니다. (두 번 눌렀을 때 등)
      // 예외가 나가면서 위에서 만든 users 한 줄도 같이 취소됩니다.
      throw new ApiException(ErrorCode.CONFLICT, GOOGLE_ACCOUNT_EXISTS_MESSAGE);
    }

    // 계정을 만든 같은 트랜잭션 안에서 동의 기록 한 줄을 남깁니다. 어느 한쪽이 실패하면 둘 다 취소됩니다.
    consentService.recordAtSignup(user.getId(), request.consents().version());
    return issueToken(user);
  }

  // 내 정보: 지금 로그인한 사람의 번호와 별명을 돌려줍니다.
  // 토큰이 없거나 틀리면 getCurrentUserId() 가 401 을 던지기 때문에, 여기서는 따로 검사하지 않습니다.
  @Transactional(readOnly = true)
  public UserResponse me() {
    Long userId = currentUserProvider.getCurrentUserId();
    User user =
        userRepository
            .findById(userId)
            // 토큰은 맞는데 사용자가 없는 경우(지워진 사용자)입니다. 다시 로그인하게 합니다.
            .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "다시 로그인해 주세요"));
    return UserResponse.from(user);
  }

  // 새 로그인 토큰을 만들어 해시만 저장하고, 토큰 원문은 FE 에게 돌려줍니다. (이메일 로그인 · 구글 로그인 · 구글 가입)
  // 시연 코드 로그인(DemoLoginService)도 같은 토큰을 쓰려고, private 이 아니라 같은 폴더에서만 부를 수 있게 열어 두었습니다.
  LoginResponse issueToken(User user) {
    String token = tokenHasher.newToken();
    Instant now = Instant.now(clock);
    Instant expiresAt = now.plus(Duration.ofDays(TOKEN_VALID_DAYS));
    loginTokenRepository.save(
        new LoginToken(user.getId(), tokenHasher.hash(token), expiresAt, now));

    return new LoginResponse(token, UserResponse.from(user));
  }

  // 구글 신분증을 확인합니다. 서버에 구글 로그인이 꺼져 있으면 503, 확인에 실패하면 400 입니다.
  // 401 이 아니라 400 인 이유: FE 는 401 을 받으면 "로그인이 풀렸다" 고 보고 로그인 화면으로 보냅니다.
  // 구글 확인 실패는 로그인하기 전의 일이라 그렇게 처리하면 안 되기 때문입니다.
  private GoogleProfile verifyGoogle(String credential) {
    if (!googleIdTokenVerifier.isEnabled()) {
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "구글 로그인이 아직 켜져 있지 않아요");
    }
    return googleIdTokenVerifier
        .verify(credential)
        .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR, GOOGLE_FAIL_MESSAGE));
  }

  // 이메일을 비교하기 좋은 모양으로 맞춥니다. 앞뒤 공백을 지우고 소문자로 바꿉니다.
  //   " Tmax@Starflow.com " -> "tmax@starflow.com"
  // (대문자·소문자만 다른 같은 주소로 두 번 가입하지 못하게 하려는 것입니다)
  private static String normalizeEmail(String email) {
    // Locale.ROOT: 컴퓨터의 언어 설정과 상관없이 같은 결과가 나오게 합니다.
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
