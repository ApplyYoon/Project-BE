package com.ithopeschool.projectbe.domain.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.onboarding.dto.InitialPlanRequest;
import com.ithopeschool.projectbe.domain.onboarding.dto.InitialPlanResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

// Service 단위 테스트: DB(Repository)를 가짜로 바꿔서 처음 계획 세우기·다시 세우기 규칙만 확인합니다.
class InitialPlanServiceTest {

  private static final Long USER_ID = 7L;
  // 지금은 2026-10-18 새벽 1시 30분(한국 시간) → 하루의 기준(새벽 4시)으로는 아직 10월 17일
  private static final Instant NOW = Instant.parse("2026-10-17T16:30:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final UserInitialPlanRepository userInitialPlanRepository =
      mock(UserInitialPlanRepository.class);
  private final InitialPlanService service =
      new InitialPlanService(
          currentUserProvider, userInitialPlanRepository, Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(userInitialPlanRepository.saveAndFlush(any()))
        .willAnswer(invocation -> invocation.getArgument(0));
  }

  private static void assertError(Runnable action, ErrorCode code, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(code);
  }

  @Test
  @DisplayName("처음 계획을 세우면 세운 날짜는 오늘(새벽 4시 기준)이다")
  void createsWithToday() {
    InitialPlanResponse response = service.create(new InitialPlanRequest(180, 5));

    assertThat(response).isEqualTo(new InitialPlanResponse(TODAY, 180, 5));
  }

  @Test
  @DisplayName("이미 세웠으면 덮어쓰지 않고 409 다")
  void createTwiceIsConflict() {
    given(userInitialPlanRepository.existsByUserId(USER_ID)).willReturn(true);

    assertError(
        () -> service.create(new InitialPlanRequest(60, 3)),
        ErrorCode.CONFLICT,
        "처음 계획은 이미 세웠어요. 바꾸려면 다시 세우기를 해 주세요");
    verify(userInitialPlanRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("완료 버튼을 빠르게 두 번 눌러서 DB 가 막아도(한 사람에 한 줄) 500 이 아니라 409 다")
  void createRaceIsConflict() {
    given(userInitialPlanRepository.saveAndFlush(any()))
        .willThrow(new DataIntegrityViolationException("uk_initial_plans_user_id"));

    assertError(
        () -> service.create(new InitialPlanRequest(180, 5)),
        ErrorCode.CONFLICT,
        "처음 계획은 이미 세웠어요. 바꾸려면 다시 세우기를 해 주세요");
  }

  @Test
  @DisplayName("다시 세우기는 원래 줄을 고치고, 세운 날짜도 오늘로 바뀐다")
  void remakeChangesTheRow() {
    UserInitialPlan plan = new UserInitialPlan(USER_ID, LocalDate.of(2026, 10, 1), 180, 5);
    given(userInitialPlanRepository.findByUserId(USER_ID)).willReturn(Optional.of(plan));

    InitialPlanResponse response = service.remake(new InitialPlanRequest(60, 3));

    assertThat(response).isEqualTo(new InitialPlanResponse(TODAY, 60, 3));
    assertThat(plan.getDailyMinutes()).isEqualTo(60);
    assertThat(plan.getCreatedDate()).isEqualTo(TODAY);
  }

  @Test
  @DisplayName("아직 안 세웠으면 다시 세우기와 조회는 404")
  void notMadeYet() {
    given(userInitialPlanRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

    assertError(
        () -> service.remake(new InitialPlanRequest(60, 3)),
        ErrorCode.NOT_FOUND,
        "아직 처음 계획을 세우지 않았어요");
    assertError(service::get, ErrorCode.NOT_FOUND, "아직 처음 계획을 세우지 않았어요");
  }

  @Test
  @DisplayName("고를 수 없는 값(하루 90분, 주 4일, 0분, 음수)은 400 이고 저장하지 않는다")
  void rejectsUnknownOptions() {
    String minutesMessage = "하루 목표 시간은 60, 120, 180, 240, 300분 중에서 골라 주세요";
    String daysMessage = "일주일에 며칠은 3, 5, 6, 7 중에서 골라 주세요";

    assertError(
        () -> service.create(new InitialPlanRequest(90, 5)),
        ErrorCode.VALIDATION_ERROR,
        minutesMessage);
    assertError(
        () -> service.create(new InitialPlanRequest(0, 5)),
        ErrorCode.VALIDATION_ERROR,
        minutesMessage);
    assertError(
        () -> service.create(new InitialPlanRequest(-60, 5)),
        ErrorCode.VALIDATION_ERROR,
        minutesMessage);
    assertError(
        () -> service.create(new InitialPlanRequest(180, 4)),
        ErrorCode.VALIDATION_ERROR,
        daysMessage);
    assertError(
        () -> service.remake(new InitialPlanRequest(180, 8)),
        ErrorCode.VALIDATION_ERROR,
        daysMessage);
    verify(userInitialPlanRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("로그인하지 않았으면 셋 다 401")
  void requiresLogin() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    assertError(service::get, ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
    assertError(
        () -> service.create(new InitialPlanRequest(180, 5)), ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
    assertError(
        () -> service.remake(new InitialPlanRequest(180, 5)), ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
  }
}
