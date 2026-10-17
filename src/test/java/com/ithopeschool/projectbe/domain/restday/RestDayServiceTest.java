package com.ithopeschool.projectbe.domain.restday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.restday.dto.RestDayResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 휴식 처리의 기한 · 한도 · 중복 검사를 확인합니다. DB 는 가짜로 바꿉니다. (이슈 #225)
class RestDayServiceTest {

  private static final Long ME = 1L;
  private static final LocalDate OCT_12 = LocalDate.of(2026, 10, 12);
  // 지금 = 2026-10-12 12:00 (한국 시간) → 오늘은 10-12
  private static final Instant NOW = Instant.parse("2026-10-12T03:00:00Z");

  private RestDayRepository restDayRepository;
  private RestDayService service;

  @BeforeEach
  void setUp() {
    CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    restDayRepository = mock(RestDayRepository.class);
    service =
        new RestDayService(
            currentUserProvider, restDayRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    given(restDayRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
  }

  private static void assertError(Runnable action, ErrorCode code, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(code);
  }

  @Test
  @DisplayName("오늘이나 지난 날은 휴식으로 처리된다 (그달 3번 썼어도 4번째는 된다)")
  void rest() {
    given(
            restDayRepository.countByUserIdAndRestDateBetween(
                ME, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
        .willReturn(3L);

    RestDayResponse response = service.rest(OCT_12);

    assertThat(response.date()).isEqualTo(OCT_12);
    assertThat(response.createdAt()).isEqualTo(NOW.toEpochMilli());
  }

  @Test
  @DisplayName("내일은 400, 이미 처리한 날은 409, 그달 4번을 다 썼으면 409")
  void checks() {
    assertError(
        () -> service.rest(OCT_12.plusDays(1)),
        ErrorCode.VALIDATION_ERROR,
        "휴식 처리는 지난 날이나 오늘만 할 수 있어요");

    given(restDayRepository.existsByUserIdAndRestDate(ME, OCT_12)).willReturn(true);
    assertError(() -> service.rest(OCT_12), ErrorCode.CONFLICT, "이미 휴식으로 처리한 날이에요");

    given(
            restDayRepository.countByUserIdAndRestDateBetween(
                ME, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
        .willReturn(4L);
    assertError(
        () -> service.rest(LocalDate.of(2026, 9, 20)), ErrorCode.CONFLICT, "휴식 처리는 한 달에 4번까지예요");
    verify(restDayRepository, never()).save(any());
  }

  @Test
  @DisplayName("목록은 시작이 끝보다 늦거나 31일을 넘으면 400")
  void listRange() {
    assertError(
        () -> service.getRestDays(OCT_12, OCT_12.minusDays(1)),
        ErrorCode.VALIDATION_ERROR,
        "시작 날짜는 끝 날짜보다 늦을 수 없어요");
    assertError(
        () -> service.getRestDays(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)),
        ErrorCode.VALIDATION_ERROR,
        "한 번에 31일까지만 조회할 수 있어요");
  }
}
