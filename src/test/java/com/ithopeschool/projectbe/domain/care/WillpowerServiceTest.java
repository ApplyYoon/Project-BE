package com.ithopeschool.projectbe.domain.care;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 의지력 Service 가 "오늘(새벽 4시 기준) 포함 최근 14일" 의 카드 하루치로 계산하는지 확인합니다. (이슈 #231)
// 계산 규칙 자체는 WillpowerTest 가 확인합니다.
class WillpowerServiceTest {

  private static final Long ME = 1L;
  private static final LocalDate OCT_17 = LocalDate.of(2026, 10, 17);

  private CardResultService cardResultService;
  private WillpowerService service;

  @BeforeEach
  void setUp() {
    CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    cardResultService = mock(CardResultService.class);
    // 지금 = 2026-10-17 12:00 (한국 시간)
    Instant now = Instant.parse("2026-10-17T03:00:00Z");
    service =
        new WillpowerService(
            currentUserProvider, cardResultService, Clock.fixed(now, ZoneOffset.UTC));
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
  }

  @Test
  @DisplayName("10-04 ~ 10-17 (14일) 을 읽어 검사와 섞는다: 데이터 80 · 유효 1일 · 검사 50 → 52.1…")
  void mixesLast14Days() {
    given(cardResultService.getJudgedDays(ME, LocalDate.of(2026, 10, 4), OCT_17))
        .willReturn(
            List.of(
                new JudgedCardDay(
                    7L,
                    OCT_17,
                    true,
                    CardPriority.FLEXIBLE,
                    CardResult.DONE,
                    0.6,
                    null,
                    true,
                    1.0)));

    // 데이터 = 50 × 0.6 + 30 × 1 + 20 × 1 = 80, w = 1/14 → 80/14 + 50 × 13/14
    double expected = 80.0 / 14 + 50.0 * 13 / 14;
    assertThat(service.getWillpower(new Willpower.TestScores(4, 4, 4)))
        .isCloseTo(expected, within(1e-9));
  }

  @Test
  @DisplayName("카드도 검사도 없으면 50")
  void nothing() {
    given(cardResultService.getJudgedDays(ME, LocalDate.of(2026, 10, 4), OCT_17))
        .willReturn(List.of());

    assertThat(service.getWillpower(null)).isEqualTo(50);
  }
}
