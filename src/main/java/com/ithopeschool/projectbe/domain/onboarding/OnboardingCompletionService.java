package com.ithopeschool.projectbe.domain.onboarding;

import com.ithopeschool.projectbe.domain.auth.dto.UserResponse;
import com.ithopeschool.projectbe.domain.user.User;
import com.ithopeschool.projectbe.domain.user.UserRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// "온보딩을 마쳤다" 를 서버에 적는 로직입니다. (users.onboarded_at, 2026-10-17 멘토 결정)
//
// 왜 필요한가요?
//   FE 는 온보딩을 어디까지 했는지를 브라우저 저장소에서만 봤습니다. 그래서 이미 가입한 사람도 다른 기기 · 브라우저에서
//   로그인하면 앱 소개부터 다시 해야 했습니다. 서버가 "마쳤다" 를 기억해 두고 로그인 응답(user.onboarded)으로 알려 줍니다.
//
// 여러 번 불러도 처음 마친 시각이 그대로라 같은 결과입니다. 로그인한 사람의 번호로만 적습니다.
@Service
@RequiredArgsConstructor
public class OnboardingCompletionService {

  private final CurrentUserProvider currentUserProvider;
  private final UserRepository userRepository;
  private final Clock clock;

  // 내 온보딩을 마쳤다고 적고, 바뀐 내 정보를 돌려줍니다. (onboarded 가 true)
  @Transactional
  public UserResponse complete() {
    Long userId = currentUserProvider.getCurrentUserId();
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없어요"));
    user.markOnboarded(clock.instant());
    return UserResponse.from(user);
  }
}
