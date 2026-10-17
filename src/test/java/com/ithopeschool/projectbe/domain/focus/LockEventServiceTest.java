package com.ithopeschool.projectbe.domain.focus;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ithopeschool.projectbe.domain.focus.dto.*;
import com.ithopeschool.projectbe.domain.settings.*;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class LockEventServiceTest {
  private final CurrentUserProvider user = mock(CurrentUserProvider.class);
  private final CareSettingRepository settings = mock(CareSettingRepository.class);
  private final LockEventRepository events = mock(LockEventRepository.class);
  private final Instant now = Instant.parse("2026-10-17T10:00:00Z");
  private final LockEventService service =
      new LockEventService(user, settings, events, Clock.fixed(now, ZoneOffset.UTC));

  private CareSetting allow() {
    when(user.getCurrentUserId()).thenReturn(7L);
    CareSetting setting = CareSetting.withDefaults(7L);
    setting.changeAllowAppLock(true);
    setting.changeMaxCareLevel(4, LocalDate.of(2026, 10, 16));
    when(settings.findByUserId(7L)).thenReturn(Optional.of(setting));
    return setting;
  }

  @Test
  void rejectsMissingConsentAndDelayedLimit() {
    when(user.getCurrentUserId()).thenReturn(7L);
    when(settings.findByUserId(7L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.create(new LockEventRequest(now, "app", "reason")))
        .isInstanceOf(ApiException.class);
    CareSetting setting = allow();
    setting.changeMaxCareLevel(2, LocalDate.of(2026, 10, 17));
    setting.changeMaxCareLevel(4, LocalDate.of(2026, 10, 17));
    assertThatThrownBy(() -> service.create(new LockEventRequest(now, "app", "reason")))
        .isInstanceOf(ApiException.class);
    verifyNoInteractions(events);
  }

  @Test
  void savesOwnerAndRejectsFutureAndMultipleLines() {
    allow();
    when(events.save(any())).thenAnswer(i -> i.getArgument(0));
    assertThat(service.create(new LockEventRequest(now, " app ", "reason")).appName())
        .isEqualTo("app");
    verify(events).save(argThat(e -> e.getUserId() == 7L && e.getOccurredAt().equals(now)));
    assertThatThrownBy(
            () -> service.create(new LockEventRequest(now.plusSeconds(1), "app", "reason")))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.create(new LockEventRequest(now, "app", "a\nb")))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void aggregatesOnlyOwnedRangeWithFourAmBoundary() {
    when(user.getCurrentUserId()).thenReturn(7L);
    LocalDate date = LocalDate.of(2026, 10, 17);
    when(events.findByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
            7L, DayRule.startOf(date), DayRule.startOf(date.plusDays(1))))
        .thenReturn(
            List.of(
                new LockEvent(7L, now, "app", "reason"), new LockEvent(7L, now, "app", "other")));
    LockEventStats stats = service.stats(date, date);
    assertThat(stats.total()).isEqualTo(2);
    assertThat(stats.byHour()).containsEntry(19, 2L);
    assertThat(stats.byApp()).containsEntry("app", 2L);
    assertThatThrownBy(() -> service.stats(date, date.minusDays(1)))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.stats(date, date.plusDays(366)))
        .isInstanceOf(ApiException.class);
  }
}
