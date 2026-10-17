package com.ithopeschool.projectbe.domain.coach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.coach.dto.CoachCommentRequest;
import com.ithopeschool.projectbe.domain.coach.dto.WeeklyCoachRequest;
import com.ithopeschool.projectbe.domain.gpt.GptUsageGuard;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// Service 단위 테스트: GPT 를 부르는 곳(CoachAiClient)을 가짜로 바꿔서, GPT 없이 로직만 확인합니다. (이슈 #87 완료 조건)
//   - GPT 가 좋은 문단을 줬을 때 / 숫자를 지어냈을 때 / 깎아내리는 말을 썼을 때 / 아무것도 안 줬을 때
//   - 로그인하지 않았을 때, AI 가 꺼져 있을 때, 하루 한도를 넘었을 때는 GPT 를 아예 부르지 않는지
//   - 같은 숫자로 다시 물으면 받아 둔 문단을 돌려주는지, 안전 브레이크가 켜지면 위로하는 말투로 묻는지
class CoachServiceTest {

  private static final Long USER_ID = 7L;
  private static final String GOOD_PARAGRAPH =
      "이번 주는 계획의 64% 를 했어. 완주율은 55% 야. 다음 주도 지금처럼 이어 가 보자.";

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final CoachAiClient coachAiClient = mock(CoachAiClient.class);
  // 하루 한도는 가짜로 "한 사람당 2번" 을 흉내 냅니다. (진짜로 DB 에 기록하며 세는 것은 GptUsageGuardTest 가 확인합니다)
  private final GptUsageGuard gptUsageGuard = mock(GptUsageGuard.class);
  // 가짜 한도가 세는 횟수 (사용자 번호 → 오늘 쓴 횟수)
  private final Map<Long, Integer> usedCounts = new HashMap<>();

  // 받아 둔 문단을 기억하는 곳도 진짜 코드를 씁니다.
  private final CoachParagraphStore coachParagraphStore = new CoachParagraphStore();

  private final CoachService coachService =
      new CoachService(currentUserProvider, coachAiClient, gptUsageGuard, coachParagraphStore);

  private static final List<WeeklyCoachRequest.Number> NUMBERS =
      List.of(
          new WeeklyCoachRequest.Number("계획 대비 실제", "64%", "계획 12개"),
          new WeeklyCoachRequest.Number("완주율", "55%", "20번"));

  // 10월 5일 주의 요청입니다. 안전 브레이크는 보내지 않았습니다. (꺼진 것으로 봅니다)
  private static final WeeklyCoachRequest REQUEST = weekOf("2026-10-05");

  // 숫자는 같고 주만 다른 요청을 만듭니다. 주가 다르면 받아 둔 문단을 쓰지 않고 GPT 에게 새로 묻습니다.
  private static WeeklyCoachRequest weekOf(String mondayKey) {
    return new WeeklyCoachRequest(mondayKey, NUMBERS, 1, 3, null);
  }

  @BeforeEach
  void loggedInAndAiOn() {
    // 기본 상황: 로그인한 사람이고, AI 가 켜져 있습니다. 테스트마다 필요한 것만 바꿉니다.
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(coachAiClient.isEnabled()).willReturn(true);
    // 코치로 부르면 한 사람당 2번까지 true, 그 뒤로는 false 입니다.
    given(gptUsageGuard.tryUseText(any(), eq(GptUsageKind.COACH)))
        .willAnswer(
            invocation -> {
              Long userId = invocation.getArgument(0);
              int used = usedCounts.getOrDefault(userId, 0);
              if (used >= 2) {
                return false;
              }
              usedCounts.put(userId, used + 1);
              return true;
            });
  }

  @Test
  @DisplayName("GPT 를 부르기 전에 '코치' 종류로 하루 한도를 보고 기록한다")
  void usesTheCoachLimit() {
    given(coachAiClient.ask(any(), any())).willReturn(Optional.of(GOOD_PARAGRAPH));

    coachService.weeklyParagraph(REQUEST);

    verify(gptUsageGuard).tryUseText(USER_ID, GptUsageKind.COACH);
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

    // 주를 바꿔 가며 묻습니다. (같은 주를 같은 숫자로 다시 물으면 GPT 를 부르지 않아서 한도를 쓰지 않습니다)
    assertThat(coachService.weeklyParagraph(weekOf("2026-09-21")).paragraph())
        .isEqualTo(GOOD_PARAGRAPH);
    assertThat(coachService.weeklyParagraph(weekOf("2026-09-28")).paragraph())
        .isEqualTo(GOOD_PARAGRAPH);
    assertThat(coachService.weeklyParagraph(weekOf("2026-10-05")).paragraph()).isNull();

    verify(coachAiClient, times(2)).ask(any(), any());
  }

  @Test
  @DisplayName("한 사람이 한도를 다 써도 다른 사람은 쓸 수 있다")
  void limitIsPerUser() {
    given(coachAiClient.ask(any(), any())).willReturn(Optional.of(GOOD_PARAGRAPH));
    coachService.weeklyParagraph(weekOf("2026-09-21"));
    coachService.weeklyParagraph(weekOf("2026-09-28"));
    assertThat(coachService.weeklyParagraph(weekOf("2026-10-05")).paragraph()).isNull();

    given(currentUserProvider.getCurrentUserId()).willReturn(8L);

    assertThat(coachService.weeklyParagraph(weekOf("2026-10-05")).paragraph())
        .isEqualTo(GOOD_PARAGRAPH);
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
    assertThat(coachService.weeklyParagraph(weekOf("2026-09-28")).paragraph())
        .isEqualTo(GOOD_PARAGRAPH);
    assertThat(coachService.weeklyParagraph(weekOf("2026-10-05")).paragraph())
        .isEqualTo(GOOD_PARAGRAPH);
    verify(coachAiClient, times(2)).ask(any(), any());
  }

  // ---- 받아 둔 문단 다시 쓰기 ----

  @Test
  @DisplayName("같은 주를 같은 숫자로 다시 물으면 GPT 를 부르지 않고 받아 둔 문단을 돌려준다 (하루 한도도 쓰지 않는다)")
  void sameQuestionReusesTheSavedParagraph() {
    given(coachAiClient.ask(any(), any())).willReturn(Optional.of(GOOD_PARAGRAPH));

    // 한도는 2번인데 5번을 물어도 전부 문단이 나옵니다. GPT 는 처음 한 번만 부릅니다.
    for (int i = 0; i < 5; i++) {
      assertThat(coachService.weeklyParagraph(REQUEST).paragraph()).isEqualTo(GOOD_PARAGRAPH);
    }
    verify(coachAiClient, times(1)).ask(any(), any());

    // 한도가 그대로 남아 있어서 다른 주도 물어볼 수 있습니다.
    assertThat(coachService.weeklyParagraph(weekOf("2026-09-28")).paragraph())
        .isEqualTo(GOOD_PARAGRAPH);
    verify(coachAiClient, times(2)).ask(any(), any());
  }

  @Test
  @DisplayName("같은 주라도 숫자가 달라지면 GPT 에게 새로 묻는다 (옛 문단의 숫자가 화면과 달라지면 안 된다)")
  void changedNumbersAskAgain() {
    given(coachAiClient.ask(any(), any())).willReturn(Optional.of(GOOD_PARAGRAPH));
    coachService.weeklyParagraph(REQUEST);

    // 그 주에 기록이 늘어서 완주율이 55% 에서 60% 가 됐습니다.
    WeeklyCoachRequest changed =
        new WeeklyCoachRequest(
            "2026-10-05",
            List.of(
                new WeeklyCoachRequest.Number("계획 대비 실제", "64%", "계획 12개"),
                new WeeklyCoachRequest.Number("완주율", "60%", "20번")),
            1,
            3,
            null);
    String newParagraph = "이번 주는 계획의 64% 를 했어. 완주율은 60% 야.";
    given(coachAiClient.ask(any(), any())).willReturn(Optional.of(newParagraph));

    assertThat(coachService.weeklyParagraph(changed).paragraph()).isEqualTo(newParagraph);
    verify(coachAiClient, times(2)).ask(any(), any());
  }

  @Test
  @DisplayName("한도(말투)를 바꾸면 같은 숫자여도 GPT 에게 새로 묻는다")
  void changedLimitsAskAgain() {
    given(coachAiClient.ask(any(), any())).willReturn(Optional.of(GOOD_PARAGRAPH));
    coachService.weeklyParagraph(REQUEST);

    // 독설 허용 수위를 3 에서 1 로 낮췄습니다. 옛 문단은 더 센 말투로 쓴 것이라 다시 쓰면 안 됩니다.
    coachService.weeklyParagraph(new WeeklyCoachRequest("2026-10-05", NUMBERS, 1, 1, null));

    verify(coachAiClient, times(2)).ask(any(), any());
  }

  @Test
  @DisplayName("받아 둔 문단은 그 사람 것만 돌려준다 (다른 사람이 같은 숫자로 물어도 GPT 에게 새로 묻는다)")
  void savedParagraphIsPerUser() {
    given(coachAiClient.ask(any(), any())).willReturn(Optional.of(GOOD_PARAGRAPH));
    coachService.weeklyParagraph(REQUEST);

    given(currentUserProvider.getCurrentUserId()).willReturn(8L);
    coachService.weeklyParagraph(REQUEST);

    verify(coachAiClient, times(2)).ask(any(), any());
  }

  @Test
  @DisplayName("버린 문단은 기억하지 않아서, 다시 물으면 GPT 에게 다시 묻는다")
  void droppedParagraphIsNotSaved() {
    given(coachAiClient.ask(any(), any())).willReturn(Optional.of("이번 주는 계획의 70% 를 했어."));
    assertThat(coachService.weeklyParagraph(REQUEST).paragraph()).isNull();

    given(coachAiClient.ask(any(), any())).willReturn(Optional.of(GOOD_PARAGRAPH));
    assertThat(coachService.weeklyParagraph(REQUEST).paragraph()).isEqualTo(GOOD_PARAGRAPH);
    verify(coachAiClient, times(2)).ask(any(), any());
  }

  // ---- 안전 브레이크 ----

  @Test
  @DisplayName("안전 브레이크가 켜져 있으면 한도가 몇이든 위로하는 말투 규칙으로 묻는다")
  void safetyBrakeMakesTheToneSupportive() {
    given(coachAiClient.ask(any(), any())).willReturn(Optional.of(GOOD_PARAGRAPH));

    // 관리 강도 한도 4, 독설 허용 수위 5 (가장 센 설정) 이지만 안전 브레이크가 켜져 있습니다.
    coachService.weeklyParagraph(new WeeklyCoachRequest("2026-10-05", NUMBERS, 4, 5, true));

    ArgumentCaptor<String> instructions = ArgumentCaptor.forClass(String.class);
    verify(coachAiClient).ask(instructions.capture(), any());
    assertThat(instructions.getValue()).isEqualTo(CoachPrompt.instructions(0, 5));
    assertThat(instructions.getValue()).contains("위로하고 응원하는 말만", "지적하지 마");
  }

  @Test
  @DisplayName("안전 브레이크를 false 로 보내거나 안 보내면 사용자가 정한 한도의 말투 그대로다")
  void safetyBrakeOffKeepsTheUsersTone() {
    given(coachAiClient.ask(any(), any())).willReturn(Optional.of(GOOD_PARAGRAPH));

    coachService.weeklyParagraph(new WeeklyCoachRequest("2026-09-28", NUMBERS, 4, 5, false));
    coachService.weeklyParagraph(new WeeklyCoachRequest("2026-10-05", NUMBERS, 4, 5, null));

    ArgumentCaptor<String> instructions = ArgumentCaptor.forClass(String.class);
    verify(coachAiClient, times(2)).ask(instructions.capture(), any());
    assertThat(instructions.getAllValues()).containsOnly(CoachPrompt.instructions(4, 5));
  }

  @Test
  @DisplayName("안전 브레이크가 켜지면, 꺼져 있을 때 받아 둔 문단을 다시 쓰지 않는다")
  void safetyBrakeDoesNotReuseTheHarsherParagraph() {
    given(coachAiClient.ask(any(), any())).willReturn(Optional.of(GOOD_PARAGRAPH));
    coachService.weeklyParagraph(new WeeklyCoachRequest("2026-10-05", NUMBERS, 4, 5, false));

    coachService.weeklyParagraph(new WeeklyCoachRequest("2026-10-05", NUMBERS, 4, 5, true));

    // 두 번째는 위로하는 말투로 새로 물어야 합니다.
    ArgumentCaptor<String> instructions = ArgumentCaptor.forClass(String.class);
    verify(coachAiClient, times(2)).ask(instructions.capture(), any());
    assertThat(instructions.getAllValues().get(1)).isEqualTo(CoachPrompt.instructions(0, 5));
  }

  @Test
  @DisplayName("어제 한 줄·월간도 안전 브레이크가 켜지면 위로하는 말투로 묻는다")
  void safetyBrakeWorksForOtherKinds() {
    given(coachAiClient.ask(any(), any())).willReturn(Optional.of(GOOD_PARAGRAPH));
    CoachCommentRequest braked = new CoachCommentRequest(NUMBERS, 3, 4, true);

    coachService.dailyParagraph(braked);
    coachService.monthlyParagraph(braked);

    ArgumentCaptor<String> instructions = ArgumentCaptor.forClass(String.class);
    verify(coachAiClient, times(2)).ask(instructions.capture(), any());
    assertThat(instructions.getAllValues())
        .containsExactly(
            CoachPrompt.instructions(CoachKind.DAILY, 0, 4),
            CoachPrompt.instructions(CoachKind.MONTHLY, 0, 4));
  }

  // ---- 어제 한 줄 · 월간 ----

  private static final CoachCommentRequest COMMENT_REQUEST =
      new CoachCommentRequest(NUMBERS, 1, 3, null);

  @Test
  @DisplayName("어제 한 줄은 '어제 하루' 지침으로, 월간은 '최근 4주' 지침으로 GPT 에게 묻는다")
  void eachKindUsesItsOwnInstructions() {
    given(coachAiClient.ask(any(), any())).willReturn(Optional.of(GOOD_PARAGRAPH));

    coachService.dailyParagraph(COMMENT_REQUEST);
    coachService.monthlyParagraph(COMMENT_REQUEST);

    ArgumentCaptor<String> instructions = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
    verify(coachAiClient, times(2)).ask(instructions.capture(), input.capture());
    assertThat(instructions.getAllValues().get(0))
        .isEqualTo(CoachPrompt.instructions(CoachKind.DAILY, 1, 3));
    assertThat(input.getAllValues().get(0)).startsWith("어제 숫자야.");
    assertThat(instructions.getAllValues().get(1))
        .isEqualTo(CoachPrompt.instructions(CoachKind.MONTHLY, 1, 3));
    assertThat(input.getAllValues().get(1)).startsWith("최근 4주 숫자야.");
  }

  @Test
  @DisplayName("어제 한 줄·월간도 지어낸 숫자가 있으면 버리고, 로그인이 필요하고, 하루 한도를 같이 쓴다")
  void otherKindsFollowTheSameRules() {
    given(coachAiClient.ask(any(), any())).willReturn(Optional.of("어제는 계획의 70% 를 했어."));
    assertThat(coachService.dailyParagraph(COMMENT_REQUEST).paragraph()).isNull();

    // 위에서 1번 썼고, 한도는 2번입니다. 월간으로 1번 더 쓰면 다음은 종류와 상관없이 막힙니다.
    given(coachAiClient.ask(any(), any())).willReturn(Optional.of(GOOD_PARAGRAPH));
    assertThat(coachService.monthlyParagraph(COMMENT_REQUEST).paragraph())
        .isEqualTo(GOOD_PARAGRAPH);
    assertThat(coachService.dailyParagraph(COMMENT_REQUEST).paragraph()).isNull();
    assertThat(coachService.weeklyParagraph(REQUEST).paragraph()).isNull();

    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));
    assertThatThrownBy(() -> coachService.dailyParagraph(COMMENT_REQUEST))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> coachService.monthlyParagraph(COMMENT_REQUEST))
        .isInstanceOf(ApiException.class);
  }
}
