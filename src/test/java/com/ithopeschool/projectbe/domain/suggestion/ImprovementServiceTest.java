package com.ithopeschool.projectbe.domain.suggestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.ai.AiClient;
import com.ithopeschool.projectbe.domain.ai.AiQuestion;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingService;
import com.ithopeschool.projectbe.domain.onboarding.dto.OnboardingResponse;
import com.ithopeschool.projectbe.domain.stats.MissReasonService;
import com.ithopeschool.projectbe.domain.stats.dto.MissReasonStatsResponse;
import com.ithopeschool.projectbe.domain.suggestion.dto.ImprovementSuggestionsResponse;
import com.ithopeschool.projectbe.domain.suggestion.dto.ImprovementSuggestionsResponse.Original;
import com.ithopeschool.projectbe.domain.suggestion.dto.ImprovementSuggestionsResponse.Suggestion;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// Service 단위 테스트: 실패 원인 분석 · 온보딩 · AI 를 가짜로 바꿔서 "무엇으로 고르고 AI 에게 무엇을 주는지" 를 확인합니다.
class ImprovementServiceTest {

  private static final Long USER_ID = 7L;
  // 2026-01-07 낮 12시(한국 시간) → 그해의 첫째 주라 강도마다 첫째 전략
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-01-07T03:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate FROM = LocalDate.parse("2025-12-07");
  private static final LocalDate TO = LocalDate.parse("2026-01-06");

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final MissReasonService missReasonService = mock(MissReasonService.class);
  private final OnboardingService onboardingService = mock(OnboardingService.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final ImprovementService service =
      new ImprovementService(
          currentUserProvider, missReasonService, onboardingService, aiClient, CLOCK);

  // 최근 30일 원인. pairs: "phone", 5, … (날 수가 많은 것부터) · 요일은 화요일에 몰아 둡니다.
  private void last30Days(Object... pairs) {
    List<MissReasonStatsResponse.Reason> reasons = new ArrayList<>();
    for (int i = 0; i < pairs.length; i += 2) {
      int days = (Integer) pairs[i + 1];
      reasons.add(
          new MissReasonStatsResponse.Reason(
              (String) pairs[i], days, List.of(0, days, 0, 0, 0, 0, 0), null, 0, List.of()));
    }
    MissReasonStatsResponse.Period empty =
        new MissReasonStatsResponse.Period(FROM, TO, 0, List.of(), List.of());
    given(missReasonService.getMissReasons())
        .willReturn(
            new MissReasonStatsResponse(
                empty, new MissReasonStatsResponse.Period(FROM, TO, 9, reasons, List.of())));
  }

  private void onboardingCauses(List<String> causes) {
    OnboardingResponse response = mock(OnboardingResponse.class);
    given(response.pastFailureCauses()).willReturn(causes);
    given(onboardingService.get()).willReturn(response);
  }

  private AiQuestion askedQuestion() {
    ArgumentCaptor<AiQuestion> question = ArgumentCaptor.forClass(AiQuestion.class);
    verify(aiClient)
        .ask(
            eq(USER_ID),
            eq(GptUsageKind.ASSIST),
            question.capture(),
            eq(StrategyPolish.Answer.class));
    return question.getValue();
  }

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(aiClient.ask(any(), any(), any(), eq(StrategyPolish.Answer.class)))
        .willReturn(Optional.empty());
  }

  @Test
  @DisplayName("최근 기록으로 원인을 고르고(온보딩은 안 봄), AI 가 다듬은 문장과 원래 문장을 함께 돌려준다")
  void recentWithPolish() {
    last30Days("phone", 5, "tired", 1);
    given(
            aiClient.ask(
                eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(StrategyPolish.Answer.class)))
        .willReturn(
            Optional.of(
                new StrategyPolish.Answer(
                    List.of(
                        new StrategyPolish.Answer.Item("화요일에 공부를 시작하면", "알림을 모두 끈다"),
                        new StrategyPolish.Answer.Item("학원에서 돌아오면", "폰을 가방에 넣고 공부를 시작한다"),
                        new StrategyPolish.Answer.Item("공부를 시작하면", "폰을 30분 동안 다른 방에 둔다")))));

    ImprovementSuggestionsResponse response = service.suggest();

    assertThat(response.cause()).isEqualTo("phone");
    assertThat(response.basis()).isEqualTo("recent");
    assertThat(response.suggestions())
        .containsExactly(
            new Suggestion(
                "phone-easy-1",
                "easy",
                "화요일에 공부를 시작하면",
                "알림을 모두 끈다",
                true,
                new Original("공부를 시작하면", "알림을 모두 끈다")),
            new Suggestion(
                "phone-normal-1",
                "normal",
                "학원에서 돌아오면",
                "폰을 가방에 넣고 공부를 시작한다",
                true,
                new Original("학원에서 돌아오면", "폰을 가방에 넣고 공부를 시작한다")),
            // 숫자(30분)가 새로 생겨서 원래 문장
            new Suggestion(
                "phone-hard-1",
                "hard",
                "공부를 시작하면",
                "폰을 다른 방에 두고 온다",
                false,
                new Original("공부를 시작하면", "폰을 다른 방에 두고 온다")));
    assertThat(askedQuestion().input())
        .isEqualTo(
            """
            학생의 상황: 최근 30일 중 '폰·SNS' 때문에 못 채운 날이 5일, 주로 화요일
            전략 (이 순서대로 하나씩):
            1. 쉬움: 공부를 시작하면 → 알림을 모두 끈다
            2. 보통: 학원에서 돌아오면 → 폰을 가방에 넣고 공부를 시작한다
            3. 강함: 공부를 시작하면 → 폰을 다른 방에 두고 온다""");
    verifyNoInteractions(onboardingService);
  }

  @Test
  @DisplayName("기록이 모자라면 온보딩 답으로 원인을 고르고, AI 가 꺼져 있으면 원래 문장 3개를 그대로 돌려준다")
  void onboardingFallbackWithoutAi() {
    last30Days("phone", 2);
    onboardingCauses(List.of("weekend", "tooMuchPlan"));

    ImprovementSuggestionsResponse response = service.suggest();

    assertThat(response.cause()).isEqualTo("tooMuch");
    assertThat(response.basis()).isEqualTo("onboarding");
    assertThat(response.suggestions())
        .extracting(Suggestion::key, Suggestion::polished)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("tooMuch-easy-1", false),
            org.assertj.core.groups.Tuple.tuple("tooMuch-normal-1", false),
            org.assertj.core.groups.Tuple.tuple("tooMuch-hard-1", false));
    assertThat(askedQuestion().input())
        .startsWith("학생의 상황: 처음 설문에서 '계획을 너무 많이 세워서' 계획이 무너진다고 했음\n");
  }

  @Test
  @DisplayName("기록도 없고 온보딩을 안 했으면(404) 원인 없이 빈 목록이고 AI 를 부르지 않는다")
  void nothingToSuggest() {
    last30Days();
    given(onboardingService.get())
        .willThrow(new ApiException(ErrorCode.NOT_FOUND, "온보딩 답이 아직 없어요"));

    assertThat(service.suggest()).isEqualTo(ImprovementSuggestionsResponse.none());
    verifyNoInteractions(aiClient);
  }
}
