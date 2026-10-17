package com.ithopeschool.projectbe.domain.care;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ithopeschool.projectbe.domain.card.CardPriority;
import com.ithopeschool.projectbe.domain.cardresult.CardResult;
import com.ithopeschool.projectbe.domain.cardresult.CardResultService;
import com.ithopeschool.projectbe.domain.cardresult.JudgedCardDay;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 재발 신호 Service 가 "오늘(새벽 4시 기준)까지 35일" 의 카드 하루치를 받아 판정하는지 확인합니다. (이슈 #230)
// 판정 규칙 자체는 RelapseSignalTest 가 확인합니다.
class RelapseSignalServiceTest {

  private static final Long ME = 1L;

  @Test
  @DisplayName("오늘(10-17 새벽 3시 = 아직 10-16)부터 35일 전까지를 읽어 판정한다")
  void judgesLast35Days() {
    CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    CardResultService cardResultService = mock(CardResultService.class);
    // 지금 = 2026-10-17 03:00 (한국 시간) → 오늘은 10-16
    Instant now = Instant.parse("2026-10-16T18:00:00Z");
    RelapseSignalService service =
        new RelapseSignalService(
            currentUserProvider, cardResultService, Clock.fixed(now, ZoneOffset.UTC));
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);

    LocalDate today = LocalDate.of(2026, 10, 16);
    given(cardResultService.getJudgedDays(ME, LocalDate.of(2026, 9, 12), today))
        .willReturn(
            List.of(
                new JudgedCardDay(
                    7L,
                    today,
                    false,
                    CardPriority.ABSOLUTE,
                    CardResult.FAILED,
                    0,
                    MissReason.TIRED,
                    null,
                    null)));

    RelapseSignal signal = service.getRelapseSignal(today);

    assertThat(signal.reasons()).containsExactly(RelapseReason.ABSOLUTE_MISS);
    assertThat(signal.mainCause()).isEqualTo(MissReason.TIRED);
    assertThat(RelapseSignalService.LOOKBACK_DAYS).isEqualTo(35);
  }
}
