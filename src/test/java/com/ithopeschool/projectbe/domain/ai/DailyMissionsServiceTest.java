package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.ai.dto.DailyMissionsRequest;
import com.ithopeschool.projectbe.domain.ai.dto.DailyMissionsResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Service 단위 테스트: AI 를 가짜로 바꿔서 위기 표현 · 못 만듦 · 검사한 답을 봅니다.
class DailyMissionsServiceTest {

  private static final Long USER_ID = 7L;

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final DailyMissionsService service =
      new DailyMissionsService(currentUserProvider, aiClient);

  private static DailyMissionsRequest request(String focusName) {
    return new DailyMissionsRequest(
        LocalDate.of(2026, 10, 19),
        new DailyMissionsRequest.Focus("OTHER", focusName),
        List.of("미적분Ⅰ"),
        null,
        3);
  }

  @BeforeEach
  void loggedIn() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
  }

  @Test
  @DisplayName("준비하는 것의 이름에 위기 표현이 있으면 AI 를 부르지 않고 showHelp")
  void crisisSkipsAi() {
    assertThat(service.suggest(request("다 그만두고 죽고 싶어"))).isEqualTo(DailyMissionsResponse.help());
    verifyNoInteractions(aiClient);
  }

  @Test
  @DisplayName("AI 가 못 만들면 missions: null, 만들면 검사한 미션 (한도는 ASSIST)")
  void asksAiAndChecks() {
    given(aiClient.ask(eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(DailyMissions.Answer.class)))
        .willReturn(Optional.empty())
        .willReturn(
            Optional.of(
                new DailyMissions.Answer(
                    List.of(new DailyMissions.Mission("코딩 테스트 1문제", 30, "미적분Ⅰ")))));

    assertThat(service.suggest(request("코딩 대회"))).isEqualTo(DailyMissionsResponse.of(null));
    assertThat(service.suggest(request("코딩 대회")).missions())
        .containsExactly(new DailyMissions.Mission("코딩 테스트 1문제", 30, "미적분Ⅰ"));
  }
}
