package com.ithopeschool.projectbe.domain.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ithopeschool.projectbe.domain.auth.dto.UserResponse;
import com.ithopeschool.projectbe.domain.user.User;
import com.ithopeschool.projectbe.domain.user.UserRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// Service 단위 테스트: "온보딩을 마쳤다" 를 서버에 적는 흐름을 확인합니다. DB 는 가짜입니다. (2026-10-17)
class OnboardingCompletionServiceTest {

  private static final Long USER_ID = 7L;
  private static final Instant NOW = Instant.parse("2026-10-17T15:00:00Z");

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final UserRepository userRepository = mock(UserRepository.class);
  private final OnboardingCompletionService service =
      new OnboardingCompletionService(
          currentUserProvider, userRepository, Clock.fixed(NOW, ZoneOffset.UTC));

  private User user;

  @BeforeEach
  void loggedIn() {
    user = new User("티맥이");
    ReflectionTestUtils.setField(user, "id", USER_ID);
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
  }

  @Test
  @DisplayName("가입한 사람은 처음에 onboarded 가 false 이고, 마침 표시를 하면 true 가 된다")
  void marksOnboarded() {
    assertThat(UserResponse.from(user).onboarded()).isFalse();

    UserResponse response = service.complete();

    assertThat(response).isEqualTo(new UserResponse("7", "티맥이", true));
    assertThat(user.getOnboardedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("여러 번 불러도 처음 마친 시각이 그대로다")
  void keepsTheFirstTime() {
    Instant first = Instant.parse("2026-10-10T00:00:00Z");
    user.markOnboarded(first);

    service.complete();

    assertThat(user.getOnboardedAt()).isEqualTo(first);
  }

  @Test
  @DisplayName("토큰은 있는데 사용자가 없으면(탈퇴 직후 등) 404")
  void missingUserIs404() {
    given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

    assertThatThrownBy(service::complete).extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
  }
}
