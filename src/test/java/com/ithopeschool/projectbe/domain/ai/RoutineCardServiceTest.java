package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.ai.RoutineCardPrompt.RawCard;
import com.ithopeschool.projectbe.domain.ai.dto.RoutineCardRequest;
import com.ithopeschool.projectbe.domain.ai.dto.RoutineCardResponse;
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
import org.mockito.ArgumentCaptor;

// Service 단위 테스트: AI(AiClient)를 가짜로 바꿔서 순서와 AI 에게 주는 글을 확인합니다.
class RoutineCardServiceTest {

  private static final Long USER_ID = 7L;
  // 2026-10-17 12:00 (한국 시간) → 하루의 기준(새벽 4시)으로도 10월 17일
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-17T03:00:00Z"), ZoneOffset.UTC);

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final RoutineCardService service =
      new RoutineCardService(currentUserProvider, aiClient, CLOCK);

  private static RoutineCardRequest completion() {
    return new RoutineCardRequest(
        "영단어장 1회독", "폰을 자꾸 보게 돼요", "폰을 다른 방에 두기", LocalDate.of(2026, 10, 26), 300, "쪽");
  }

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
  }

  @Test
  @DisplayName("완주형: 하루 분량을 코드가 계산해 AI 에게 주고, 한도는 작은 AI 기능 묶음(ASSIST)으로 센다")
  void suggests() {
    given(
            aiClient.ask(
                eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(RoutineCardPrompt.Answer.class)))
        .willReturn(
            Optional.of(
                new RoutineCardPrompt.Answer(
                    List.of(
                        new RawCard(
                            "영단어 30쪽", "WEEKDAY_EVENING", List.of("폰을 다른 방에 두고 오기", "30쪽 외우기")),
                        new RawCard("주말 점검", "SATURDAY_MORNING", List.of("밀린 쪽 채우기"))))));

    RoutineCardResponse response = service.suggest(completion());

    assertThat(response.showHelp()).isFalse();
    assertThat(response.cards())
        .containsExactly(
            new RoutineCardResponse.Suggestion(
                "영단어 30쪽",
                List.of(1, 2, 3, 4, 5),
                "19:00",
                50,
                List.of("폰을 다른 방에 두고 오기", "30쪽 외우기")),
            new RoutineCardResponse.Suggestion(
                "주말 점검", List.of(6), "10:00", 60, List.of("밀린 쪽 채우기")));
    ArgumentCaptor<AiQuestion> question = ArgumentCaptor.forClass(AiQuestion.class);
    verify(aiClient)
        .ask(
            eq(USER_ID),
            eq(GptUsageKind.ASSIST),
            question.capture(),
            eq(RoutineCardPrompt.Answer.class));
    // 개인정보가 들어가지 않는지 보려고, 입력 글 전체가 정확히 이것인지 봅니다. (17 ~ 26일 = 10일 → 하루 30쪽)
    assertThat(question.getValue().input())
        .isEqualTo(RoutineCardPrompt.input("영단어장 1회독", "폰을 자꾸 보게 돼요", "폰을 다른 방에 두기", "30쪽"));
  }

  @Test
  @DisplayName("점수형(분량 없음)이면 하루 분량 없이 묻는다")
  void scoreGoalHasNoDailyAmount() {
    given(aiClient.ask(any(), any(), any(), eq(RoutineCardPrompt.Answer.class)))
        .willReturn(Optional.empty());

    service.suggest(
        new RoutineCardRequest("수학 3등급", null, "문제집부터 펴기", LocalDate.of(2026, 11, 19), null, null));

    ArgumentCaptor<AiQuestion> question = ArgumentCaptor.forClass(AiQuestion.class);
    verify(aiClient).ask(any(), any(), question.capture(), eq(RoutineCardPrompt.Answer.class));
    assertThat(question.getValue().input()).doesNotContain("하루 분량");
  }

  @Test
  @DisplayName("AI 가 못 만들었거나 쓸 만한 카드가 2장보다 적으면 에러가 아니라 cards: null 이다")
  void unusableIsNull() {
    given(aiClient.ask(any(), any(), any(), eq(RoutineCardPrompt.Answer.class)))
        .willReturn(Optional.empty());
    assertThat(service.suggest(completion())).isEqualTo(new RoutineCardResponse(false, null));

    given(aiClient.ask(any(), any(), any(), eq(RoutineCardPrompt.Answer.class)))
        .willReturn(
            Optional.of(
                new RoutineCardPrompt.Answer(
                    List.of(new RawCard("영단어 30쪽", "WEEKDAY_EVENING", List.of("30쪽"))))));
    assertThat(service.suggest(completion()).cards()).isNull();
  }

  @Test
  @DisplayName("목표 · 방해물 · 대처 중 하나라도 위기 표현이 있으면 AI 를 부르지 않고 showHelp 다")
  void crisisShowsHelp() {
    assertThat(service.suggest(new RoutineCardRequest("죽고 싶다", null, "대처", null, null, null)))
        .isEqualTo(RoutineCardResponse.help());
    assertThat(service.suggest(new RoutineCardRequest("수학", "그냥 사라지고 싶어", "대처", null, null, null)))
        .isEqualTo(RoutineCardResponse.help());
    assertThat(service.suggest(new RoutineCardRequest("수학", null, "자해", null, null, null)))
        .isEqualTo(RoutineCardResponse.help());
    verifyNoInteractions(aiClient);
  }
}
