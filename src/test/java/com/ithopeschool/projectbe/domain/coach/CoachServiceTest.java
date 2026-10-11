package com.ithopeschool.projectbe.domain.coach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.coach.dto.WeeklyCoachRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// Service 단위 테스트: GPT 를 부르는 곳(CoachAiClient)을 가짜로 바꿔서, GPT 없이 로직만 확인합니다. (이슈 #87 완료 조건)
//   - GPT 가 좋은 문단을 줬을 때 / 숫자를 지어냈을 때 / 깎아내리는 말을 썼을 때 / 아무것도 안 줬을 때
//   - 로그인하지 않았을 때, AI 가 꺼져 있을 때, 하루 한도를 넘었을 때는 GPT 를 아예 부르지 않는지
class CoachServiceTest {

  private static final Long USER_ID = 7L;
  private static final String GOOD_PARAGRAPH =
      "이번 주는 계획의 64% 를 했어. 완주율은 55% 야. 다음 주도 지금처럼 이어 가 보자.";

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final CoachAiClient coachAiClient = mock(CoachAiClient.class);
  // 하루 한도는 2번으로 두고 진짜 코드를 씁니다.
  private final CoachDailyLimit coachDailyLimit =
      new CoachDailyLimit(2, Clock.fixed(Instant.parse("2026-10-11T06:00:00Z"), ZoneOffset.UTC));

  private final CoachService coachService =
      new CoachService(currentUserProvider, coachAiClient, coachDailyLimit);

  private static final WeeklyCoachRequest REQUEST =
      new WeeklyCoachRequest(
          "2026-10-05",
          List.of(
              new WeeklyCoachRequest.Number("계획 대비 실제", "64%", "계획 12개"),
              new WeeklyCoachRequest.Number("완주율", "55%", "20번")),
          1,
          3);

  @BeforeEach
  void loggedInAndAiOn() {
    // 기본 상황: 로그인한 사람이고, AI 가 켜져 있습니다. 테스트마다 필요한 것만 바꿉니다.
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(coachAiClient.isEnabled()).willReturn(true);
  }

  @Test
  @DisplayName("GPT 가 준 숫자만 쓴 문단을 주면 그대로 돌려준다")
  void returnsTheParagraphFromAi() {
    given(coachAiClient.ask(any(), any())).willReturn(Optional.of(GOOD_PARAGRAPH));

    assertThat(coachService.weeklyParagraph(REQUEST).paragraph()).isEqualTo(GOOD_PARAGRAPH);
  }

  @Test
  @DisplayName("GPT 에게는 받은 숫자와, 사용자가 정한 한도에 맞는 말투 규칙을 건넨다")
  void passesNumbersAndToneToAi() {
    given(coachAiClient.ask(any(), any())).willReturn(Optional.of(GOOD_PARAGRAPH));

    coachService.weeklyParagraph(REQUEST);

    ArgumentCaptor<String> instructions = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
    verify(coachAiClient).ask(instructions.capture(), input.capture());
    assertThat(instructions.getValue()).isEqualTo(CoachPrompt.instructions(1, 3));
    assertThat(input.getValue()).contains("- 계획 대비 실제: 64% (계획 12개)", "- 완주율: 55% (20번)");
  }

  @Test
  @DisplayName("GPT 가 준 적 없는 숫자를 쓰면 문단을 버린다 (FE 가 기본 문장을 보여준다)")
  void dropsParagraphWithInventedNumber() {
    given(coachAiClient.ask(any(), any())).willReturn(Optional.of("이번 주는 계획의 70% 를 했어."));

    assertThat(coachService.weeklyParagraph(REQUEST).paragraph()).isNull();
  }

  @Test
  @DisplayName("GPT 가 깎아내리는 말을 쓰면 문단을 버린다")
  void dropsParagraphWithBannedWord() {
    given(coachAiClient.ask(any(), any())).willReturn(Optional.of("완주율이 55% 라니 한심해."));

    assertThat(coachService.weeklyParagraph(REQUEST).paragraph()).isNull();
  }

  @Test
  @DisplayName("GPT 가 답을 못 주면(실패·시간 초과) 에러 없이 문단 없음으로 답한다")
  void noParagraphWhenAiFails() {
    given(coachAiClient.ask(any(), any())).willReturn(Optional.empty());

    assertThat(coachService.weeklyParagraph(REQUEST).paragraph()).isNull();
  }

  @Test
  @DisplayName("로그인하지 않았으면 401 이고, GPT 를 부르지 않는다")
  void requiresLogin() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    assertThatThrownBy(() -> coachService.weeklyParagraph(REQUEST))
        .isInstanceOf(ApiException.class)
        .hasMessage("로그인이 필요해요");
    verify(coachAiClient, never()).ask(any(), any());
  }

  @Test
  @DisplayName("AI 가 꺼진 서버(내 컴퓨터)에서는 GPT 를 부르지 않고 문단 없음으로 답한다")
  void noParagraphWhenAiIsOff() {
    given(coachAiClient.isEnabled()).willReturn(false);

    assertThat(coachService.weeklyParagraph(REQUEST).paragraph()).isNull();
    verify(coachAiClient, never()).ask(any(), any());
  }

  @Test
  @DisplayName("하루 한도(2번)를 넘으면 GPT 를 더 부르지 않고 문단 없음으로 답한다")
  void stopsCallingAiAfterTheDailyLimit() {
    given(coachAiClient.ask(any(), any())).willReturn(Optional.of(GOOD_PARAGRAPH));

    assertThat(coachService.weeklyParagraph(REQUEST).paragraph()).isEqualTo(GOOD_PARAGRAPH);
    assertThat(coachService.weeklyParagraph(REQUEST).paragraph()).isEqualTo(GOOD_PARAGRAPH);
    assertThat(coachService.weeklyParagraph(REQUEST).paragraph()).isNull();

    verify(coachAiClient, times(2)).ask(any(), any());
  }

  @Test
  @DisplayName("한 사람이 한도를 다 써도 다른 사람은 쓸 수 있다")
  void limitIsPerUser() {
    given(coachAiClient.ask(any(), any())).willReturn(Optional.of(GOOD_PARAGRAPH));
    coachService.weeklyParagraph(REQUEST);
    coachService.weeklyParagraph(REQUEST);
    assertThat(coachService.weeklyParagraph(REQUEST).paragraph()).isNull();

    given(currentUserProvider.getCurrentUserId()).willReturn(8L);

    assertThat(coachService.weeklyParagraph(REQUEST).paragraph()).isEqualTo(GOOD_PARAGRAPH);
  }

  @Test
  @DisplayName("AI 가 꺼져 있을 때는 하루 한도를 쓰지 않는다")
  void aiOffDoesNotUseTheLimit() {
    given(coachAiClient.isEnabled()).willReturn(false);
    for (int i = 0; i < 5; i++) {
      coachService.weeklyParagraph(REQUEST);
    }

    // AI 가 켜진 뒤에도 한도 2번이 그대로 남아 있습니다.
    given(coachAiClient.isEnabled()).willReturn(true);
    given(coachAiClient.ask(any(), any())).willReturn(Optional.of(GOOD_PARAGRAPH));
    assertThat(coachService.weeklyParagraph(REQUEST).paragraph()).isEqualTo(GOOD_PARAGRAPH);
    assertThat(coachService.weeklyParagraph(REQUEST).paragraph()).isEqualTo(GOOD_PARAGRAPH);
  }
}
