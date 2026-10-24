package com.ithopeschool.projectbe.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ithopeschool.projectbe.domain.coach.CoachAiClient;
import com.ithopeschool.projectbe.domain.gpt.GptUsageGuard;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.message.Message;
import com.ithopeschool.projectbe.domain.message.MessageKind;
import com.ithopeschool.projectbe.domain.message.MessageRepository;
import com.ithopeschool.projectbe.domain.settings.CareSetting;
import com.ithopeschool.projectbe.domain.settings.CareSettingRepository;
import java.time.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SafetyServiceTest {
  final CoachAiClient ai = mock(CoachAiClient.class);
  final GptUsageGuard usage = mock(GptUsageGuard.class);
  final CareSettingRepository settings = mock(CareSettingRepository.class);
  final MessageRepository messages = mock(MessageRepository.class);
  final Instant now = Instant.parse("2026-10-17T10:00:00Z");
  final SafetyService service =
      new SafetyService(ai, usage, settings, messages, Clock.fixed(now, ZoneOffset.UTC));

  @Test
  void keywordsWorkWithoutAi() {
    assertThat(service.check(7L, List.of("사라지고 싶어"))).isTrue();
    verifyNoInteractions(ai, usage);
  }

  @Test
  void aiFindsIndirectExpressionAndFailsConservatively() {
    when(ai.isEnabled()).thenReturn(true);
    when(ai.ask(anyString(), anyString()))
        .thenReturn(Optional.of("CRISIS"), Optional.empty(), Optional.of("SAFE"));
    assertThat(service.check(7L, List.of("오늘이 마지막이면 좋겠어"))).isTrue();
    assertThat(service.check(7L, List.of("오늘은 힘들었어"))).isTrue();
    assertThat(service.check(7L, List.of("오늘은 힘들었어"))).isFalse();
    // 하루 한도 없이 AI 에게 물을 때마다 기록만 남깁니다.
    verify(usage, times(3)).recordText(7L, GptUsageKind.COACH);
  }

  @Test
  void activationClearsPendingIncreaseAndDoesNotStoreOriginalText() {
    CareSetting setting = CareSetting.withDefaults(7L);
    setting.changeMaxCareLevel(4, LocalDate.of(2026, 10, 17));
    setting.changeAllowAppLock(true);
    when(settings.findByUserId(7L)).thenReturn(Optional.of(setting));
    service.activate(7L);
    assertThat(setting.maxCareLevelOn(LocalDate.of(2026, 10, 18))).isZero();
    assertThat(setting.isAllowAppLock()).isFalse();
    ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
    verify(messages).save(saved.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(7L);
    assertThat(saved.getValue().getText()).isEqualTo(SafetyService.HELP_MESSAGE);
    assertThat(saved.getValue().getSentAt()).isEqualTo(now);
  }

  @Test
  void recentProtectionLookupIsScopedToUser() {
    service.isActive(7L);
    verify(messages)
        .existsByUserIdAndKindAndTextAndSentAtGreaterThanEqual(
            eq(7L), eq(MessageKind.RECOVERY), eq(SafetyService.HELP_MESSAGE), any());
  }
}
