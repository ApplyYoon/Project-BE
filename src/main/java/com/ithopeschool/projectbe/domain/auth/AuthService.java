package com.ithopeschool.projectbe.domain.auth;

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

  private final UserRepository userRepository;
  private final AccountRepository accountRepository;
  private final LoginTokenRepository loginTokenRepository;
  private final PasswordHasher passwordHasher;
  private final TokenHasher tokenHasher;
  private final CurrentUserProvider currentUserProvider;
  // "지금 몇 시인지" 는 시계에게 묻습니다. 테스트에서 시각을 고정할 수 있습니다. (global/config/ClockConfig)
  private final Clock clock;

  // 가입: 계정만 만들고 로그인은 하지 않습니다. (가입한 뒤 로그인 화면에서 로그인합니다)
  @Transactional
  public UserResponse signup(SignupRequest request) {
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
    if (!passwordHasher.matches(request.password(), account.getPasswordHash())) {
      throw new ApiException(ErrorCode.UNAUTHORIZED, LOGIN_FAIL_MESSAGE);
    }

    User user =
        userRepository
            .findById(account.getUserId())
            // 계정은 있는데 사용자가 없는 것은 데이터가 깨진 경우입니다. 로그인 실패와 같은 답을 줍니다.
            .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, LOGIN_FAIL_MESSAGE));

    // 토큰 원문은 FE 에게만 주고, DB 에는 해시만 저장합니다.
    String token = tokenHasher.newToken();
    Instant now = Instant.now(clock);
    Instant expiresAt = now.plus(Duration.ofDays(TOKEN_VALID_DAYS));
    loginTokenRepository.save(
        new LoginToken(user.getId(), tokenHasher.hash(token), expiresAt, now));

    return new LoginResponse(token, UserResponse.from(user));
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

  // 이메일을 비교하기 좋은 모양으로 맞춥니다. 앞뒤 공백을 지우고 소문자로 바꿉니다.
  //   " Tmax@Starflow.com " -> "tmax@starflow.com"
  // (대문자·소문자만 다른 같은 주소로 두 번 가입하지 못하게 하려는 것입니다)
  private static String normalizeEmail(String email) {
    // Locale.ROOT: 컴퓨터의 언어 설정과 상관없이 같은 결과가 나오게 합니다.
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
