package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.ai.dto.DayPlanReviseRequest;
import com.ithopeschool.projectbe.domain.ai.dto.DayPlanReviseResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Service 단위 테스트: AI 를 가짜로 바꿔서 위기 표현 · 못 고침 · 고친 계획을 봅니다.
class DayPlanReviseServiceTest {

  private static final Long USER_ID = 7L;
  // 2026-10-19 12:00 (한국 시간)
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-19T03:00:00Z"), ZoneOffset.UTC);

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final DayPlanReviseService service =
      new DayPlanReviseService(currentUserProvider, aiClient, CLOCK);

  private static DayPlanReviseRequest request(String text) {
    return new DayPlanReviseRequest(
        List.of(
            new DayPlanReviseRequest.Draft(
                LocalDate.of(2026, 10, 19), "19:00", 30, "미적분 문제집", List.of())),
        text,
        null,
        null);
  }

  @BeforeEach
  void loggedIn() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
  }

  @Test
  @DisplayName("요청에 위기 표현이 있으면 AI 를 부르지 않고 showHelp")
  void crisisSkipsAi() {
    assertThat(service.revise(request("다 그만두고 죽고 싶어"))).isEqualTo(DayPlanReviseResponse.help());
    verifyNoInteractions(aiClient);
  }

  @Test
  @DisplayName("AI 가 못 고치면 drafts: null, 고치면 검사한 계획과 한 줄 (한도는 ASSIST)")
  void revisesWithAi() {
    given(aiClient.ask(eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(DayPlanRevise.Answer.class)))
        .willReturn(Optional.empty())
        .willReturn(
            Optional.of(
                new DayPlanRevise.Answer(
                    "20분으로 줄였어.",
                    List.of(
                        new DayPlanRevise.Answer.Item(
                            "2026-10-19", "19:00", 20, "미적분 문제집", List.of("5문제"))))));

    assertThat(service.revise(request("줄여 줘"))).isEqualTo(DayPlanReviseResponse.notMade());
    DayPlanReviseResponse revised = service.revise(request("줄여 줘"));
    assertThat(revised.reply()).isEqualTo("20분으로 줄였어.");
    assertThat(revised.drafts())
        .containsExactly(
            new DayPlanRevise.Card(
                LocalDate.of(2026, 10, 19), "19:00", 20, "미적분 문제집", List.of("5문제")));
  }
}
