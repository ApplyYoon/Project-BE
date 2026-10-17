package com.ithopeschool.projectbe.domain.dailysignal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.dailysignal.dto.DailySignalResponse;
import com.ithopeschool.projectbe.domain.dailysignal.dto.DailySignalSaveRequest;
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
import org.springframework.test.util.ReflectionTestUtils;

// Service 단위 테스트: DB(Repository)를 가짜로 바꿔서 저장·덮어쓰기·검사 로직만 확인합니다.
class DailySignalServiceTest {

  private static final Long USER_ID = 7L;
  // 지금은 2026-10-17 오후 6시(한국 시간)
  private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");
  private static final LocalDate TODAY = LocalDate.parse("2026-10-17");
  // 10/16 밤 11시 30분에 자고 10/17 아침 7시에 일어남 (한국 시간)
  private static final long SLEPT_AT = Instant.parse("2026-10-16T14:30:00Z").toEpochMilli();
  private static final long WOKE_AT = Instant.parse("2026-10-16T22:00:00Z").toEpochMilli();

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final DailySignalRepository dailySignalRepository = mock(DailySignalRepository.class);
  private final DailySignalService service =
      new DailySignalService(
          currentUserProvider, dailySignalRepository, Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(dailySignalRepository.save(any()))
        .willAnswer(
            invocation -> {
              DailySignal signal = invocation.getArgument(0);
              ReflectionTestUtils.setField(signal, "id", 5L);
              return signal;
            });
  }

  @Test
  @DisplayName("그날 처음 보내면 새 줄을 만들고, 시각은 밀리초 숫자로 돌려준다")
  void createsNewDay() {
    given(dailySignalRepository.findByUserIdAndSignalDate(USER_ID, TODAY))
        .willReturn(Optional.empty());

    DailySignalResponse response =
        service.save(new DailySignalSaveRequest(TODAY, 420, SLEPT_AT, WOKE_AT, 8500, true));

    assertThat(response.id()).isEqualTo("5");
    assertThat(response.date()).isEqualTo(TODAY);
    assertThat(response.sleepMinutes()).isEqualTo(420);
    assertThat(response.sleptAt()).isEqualTo(SLEPT_AT);
    assertThat(response.wokeAt()).isEqualTo(WOKE_AT);
    assertThat(response.steps()).isEqualTo(8500);
    assertThat(response.snsOverThirtyMinutes()).isTrue();
  }

  @Test
  @DisplayName("같은 날을 다시 보내면 새 줄을 만들지 않고 덮어쓴다. 이번에 안 보낸 칸은 비워진다")
  void overwritesSameDay() {
    DailySignal existing =
        new DailySignal(
            USER_ID,
            TODAY,
            420,
            Instant.ofEpochMilli(SLEPT_AT),
            Instant.ofEpochMilli(WOKE_AT),
            8500,
            true);
    ReflectionTestUtils.setField(existing, "id", 5L);
    given(dailySignalRepository.findByUserIdAndSignalDate(USER_ID, TODAY))
        .willReturn(Optional.of(existing));

    DailySignalResponse response =
        service.save(new DailySignalSaveRequest(TODAY, null, null, null, 12000, false));

    verify(dailySignalRepository, never()).save(any());
    assertThat(response.id()).isEqualTo("5");
    assertThat(response.steps()).isEqualTo(12000);
    assertThat(response.snsOverThirtyMinutes()).isFalse();
    assertThat(response.sleepMinutes()).isNull();
    assertThat(response.sleptAt()).isNull();
    assertThat(existing.getSteps()).isEqualTo(12000);
  }

  @Test
  @DisplayName("날짜만 보내도 저장된다 (모든 칸이 비어도 된다)")
  void savesEmptyDay() {
    given(dailySignalRepository.findByUserIdAndSignalDate(USER_ID, TODAY))
        .willReturn(Optional.empty());

    DailySignalResponse response =
        service.save(new DailySignalSaveRequest(TODAY, null, null, null, null, null));

    assertThat(response.sleepMinutes()).isNull();
    assertThat(response.sleptAt()).isNull();
    assertThat(response.wokeAt()).isNull();
    assertThat(response.steps()).isNull();
    assertThat(response.snsOverThirtyMinutes()).isNull();
  }

  @Test
  @DisplayName("새벽 4시 전에는 아직 전날이라, 달력상 오늘 날짜도 '오지 않은 날' 로 400 이다")
  void rejectsFutureDate() {
    // 10/18 새벽 2시(한국 시간)는 하루의 기준으로 아직 10/17 입니다.
    DailySignalService beforeFour =
        new DailySignalService(
            currentUserProvider,
            dailySignalRepository,
            Clock.fixed(Instant.parse("2026-10-17T17:00:00Z"), ZoneOffset.UTC));

    assertThatThrownBy(
            () ->
                beforeFour.save(
                    new DailySignalSaveRequest(
                        LocalDate.parse("2026-10-18"), null, null, null, null, null)))
        .isInstanceOf(ApiException.class)
        .hasMessage("아직 오지 않은 날짜예요")
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
    verify(dailySignalRepository, never()).save(any());
  }

  @Test
  @DisplayName("일어난 시각이 잠든 시각보다 앞이거나 같으면 400 이다")
  void rejectsWokeBeforeSlept() {
    assertThatThrownBy(
            () ->
                service.save(
                    new DailySignalSaveRequest(TODAY, null, WOKE_AT, SLEPT_AT, null, null)))
        .isInstanceOf(ApiException.class)
        .hasMessage("일어난 시각은 잠든 시각보다 뒤여야 해요");
    assertThatThrownBy(
            () ->
                service.save(
                    new DailySignalSaveRequest(TODAY, null, SLEPT_AT, SLEPT_AT, null, null)))
        .isInstanceOf(ApiException.class);
    verify(dailySignalRepository, never()).save(any());
  }

  @Test
  @DisplayName("잠든 시각이나 일어난 시각 하나만 보내도 된다")
  void allowsOnlyOneOfSleepTimes() {
    given(dailySignalRepository.findByUserIdAndSignalDate(USER_ID, TODAY))
        .willReturn(Optional.empty());

    DailySignalResponse response =
        service.save(new DailySignalSaveRequest(TODAY, null, null, WOKE_AT, null, null));

    assertThat(response.wokeAt()).isEqualTo(WOKE_AT);
    assertThat(response.sleptAt()).isNull();
  }
}
