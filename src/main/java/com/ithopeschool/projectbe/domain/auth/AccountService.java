package com.ithopeschool.projectbe.domain.auth;

import com.ithopeschool.projectbe.domain.auth.dto.PasswordChangeRequest;
import com.ithopeschool.projectbe.domain.auth.dto.ProfileResponse;
import com.ithopeschool.projectbe.domain.auth.dto.ProfileUpdateRequest;
import com.ithopeschool.projectbe.domain.auth.dto.WithdrawRequest;
import com.ithopeschool.projectbe.domain.user.User;
import com.ithopeschool.projectbe.domain.user.UserRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 로그인한 사람이 자기 계정을 관리하는 로직입니다. (이슈 #101)
//
//   내 정보 보기·고치기 / 비밀번호 바꾸기 / 로그아웃 / 탈퇴
//
// 모든 메서드는 currentUserProvider.getCurrentUserId() 로 시작합니다. 그래서 로그인하지 않은 요청은 401 이 나가고,
// 남의 번호를 보내서 남의 계정을 고치는 일이 생기지 않습니다. (누구의 계정인지를 요청에서 받지 않습니다)
//
// ⚠️ 이메일·실명·비밀번호는 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class AccountService {

  // 비밀번호가 틀렸을 때의 문장입니다. 비밀번호 바꾸기와 탈퇴가 같이 씁니다.
  private static final String WRONG_PASSWORD_MESSAGE = "비밀번호가 맞지 않아요";

  private final CurrentUserProvider currentUserProvider;
  private final UserRepository userRepository;
  private final AccountRepository accountRepository;
  private final LoginTokenRepository loginTokenRepository;
  private final PasswordHasher passwordHasher;
  // 탈퇴할 때 "userId 가 있는 모든 테이블" 을 찾아 지우는 데 씁니다.
  private final EntityManager entityManager;

  // 내 정보 보기
  @Transactional(readOnly = true)
  public ProfileResponse getProfile() {
    Long userId = currentUserProvider.getCurrentUserId();
    return ProfileResponse.of(findUser(userId), findAccount(userId));
  }

  // 내 정보 고치기: 이름·닉네임·학교·일기 분석 동의를 받은 값으로 바꿉니다.
  @Transactional
  public ProfileResponse updateProfile(ProfileUpdateRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    User user = findUser(userId);
    Account account = findAccount(userId);

    // 엔티티의 값을 바꾸기만 하면, 이 메서드가 끝날 때 JPA 가 바뀐 것을 알아채고 DB 에 반영합니다. (save 를 부르지 않아도 됩니다)
    user.changeNickname(request.nickname());
    account.updateProfile(
        request.name(), request.schoolCode(), request.schoolName(), request.diaryAnalysis());
    return ProfileResponse.of(user, account);
  }

  // 비밀번호 바꾸기: 지금 비밀번호가 맞아야 합니다. 바꾸면 다른 기기의 로그인은 풀립니다.
  @Transactional
  public void changePassword(PasswordChangeRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    Account account = findAccount(userId);
    checkPassword(request.currentPassword(), account);

    account.changePasswordHash(passwordHasher.hash(request.newPassword()));

    // 비밀번호를 바꾸는 이유가 "누가 내 계정에 들어온 것 같아서" 일 수 있습니다.
    // 그래서 지금 이 요청의 토큰만 남기고, 이 사람의 다른 토큰(다른 기기의 로그인)은 모두 지웁니다.
    String currentTokenHash = currentUserProvider.getCurrentTokenHash();
    if (currentTokenHash == null) {
      loginTokenRepository.deleteByUserId(userId);
    } else {
      loginTokenRepository.deleteByUserIdAndTokenHashNot(userId, currentTokenHash);
    }
  }

  // 로그아웃: 지금 요청에 담긴 토큰을 지웁니다. 지운 뒤에는 그 토큰으로 더 부를 수 없습니다. (401)
  @Transactional
  public void logout() {
    // 로그인한 요청인지 먼저 확인합니다. (토큰이 없거나 틀리면 여기서 401)
    currentUserProvider.getCurrentUserId();

    String currentTokenHash = currentUserProvider.getCurrentTokenHash();
    if (currentTokenHash != null) {
      loginTokenRepository.deleteByTokenHash(currentTokenHash);
    }
  }

  // 탈퇴: 비밀번호를 확인한 뒤, 이 사람의 데이터를 모두 지웁니다. 되돌릴 수 없습니다.
  @Transactional
  public void withdraw(WithdrawRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    checkPassword(request.password(), findAccount(userId));

    // 이 프로젝트의 규칙: users 를 뺀 모든 테이블에는 "누구의 것인지" 를 적는 userId 가 있습니다.
    // 그 규칙 덕분에, userId 가 있는 테이블을 전부 돌면서 이 사람의 줄을 지우면 빠뜨리는 것이 없습니다.
    // (계정·로그인 토큰·과목·계획·공부 기록이 모두 여기서 지워지고, 앞으로 테이블이 늘어도 이 코드는 그대로입니다)
    for (EntityType<?> entity : entityManager.getMetamodel().getEntities()) {
      boolean hasUserId =
          entity.getAttributes().stream().anyMatch(a -> a.getName().equals("userId"));
      if (hasUserId) {
        // entity.getName() 은 우리가 만든 클래스 이름(예: StudyPlan)이라, 바깥에서 들어온 글자가 아닙니다.
        // 사용자 번호는 SQL 문자열에 붙이지 않고 파라미터(:userId)로 넘깁니다.
        entityManager
            .createQuery("delete from " + entity.getName() + " e where e.userId = :userId")
            .setParameter("userId", userId)
            .executeUpdate();
      }
    }

    // 마지막으로 users 의 그 사람 한 줄을 지웁니다.
    userRepository.deleteById(userId);
  }

  // 입력한 비밀번호가 이 계정의 비밀번호가 맞는지 확인합니다. 틀리면 400 입니다.
  // 401 이 아니라 400 인 이유: FE 는 401 을 받으면 "로그인이 풀렸다" 고 보고 로그인 화면으로 보냅니다.
  // 비밀번호 한 번 틀렸다고 로그인까지 풀리면 안 되기 때문입니다.
  private void checkPassword(String rawPassword, Account account) {
    // 구글로 가입한 계정은 비밀번호가 없습니다. (비밀번호 바꾸기 · 탈퇴는 아직 이메일 계정만 됩니다, 이슈 #19)
    if (!account.hasPassword()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "구글로 가입한 계정은 비밀번호가 없어요");
    }
    if (!passwordHasher.matches(rawPassword, account.getPasswordHash())) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, WRONG_PASSWORD_MESSAGE);
    }
  }

  private User findUser(Long userId) {
    return userRepository
        .findById(userId)
        // 토큰은 맞는데 사용자가 없는 경우(지워진 사용자)입니다. 다시 로그인하게 합니다.
        .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "다시 로그인해 주세요"));
  }

  private Account findAccount(Long userId) {
    return accountRepository
        .findByUserId(userId)
        // 사용자는 있는데 계정이 없는 경우입니다. 내 컴퓨터의 테스트 사용자(테스트 토큰)가 여기에 해당합니다.
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "이메일로 가입한 계정이 없어요"));
  }
}
