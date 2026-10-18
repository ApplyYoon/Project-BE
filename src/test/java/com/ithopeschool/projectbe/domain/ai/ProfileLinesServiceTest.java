package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.ai.dto.ProfileLinesResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.psychtest.PsychItem;
import com.ithopeschool.projectbe.domain.psychtest.PsychTest;
import com.ithopeschool.projectbe.domain.psychtest.PsychTestRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// Service 단위 테스트: DB 와 AI(AiClient)를 가짜로 바꿔서 "DB 의 심리검사를 읽어 AI 에게 무엇을 주는지" 를 확인합니다.
class ProfileLinesServiceTest {

  private static final Long USER_ID = 7L;

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final PsychTestRepository psychTestRepository = mock(PsychTestRepository.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final ProfileLinesService service =
      new ProfileLinesService(currentUserProvider, psychTestRepository, aiClient);

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
  }

  // 시작 저항(S)은 7, 관리 선호(C)는 1, 나머지 4 인 35문항 검사
  private void latestTest() {
    Map<PsychItem, Integer> answers = new EnumMap<>(PsychItem.class);
    for (PsychItem item : PsychItem.values()) {
      char scale = item.name().charAt(0);
      answers.put(item, scale == 'S' ? 7 : scale == 'C' ? 1 : 4);
    }
    given(psychTestRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
        .willReturn(
            Optional.of(new PsychTest(USER_ID, Instant.parse("2026-10-17T09:00:00Z"), answers)));
  }

  @Test
  @DisplayName("DB 의 가장 최근 심리검사로 두드러진 척도 3개를 AI 에게 주고, 받은 3줄을 돌려준다")
  void suggests() {
    latestTest();
    given(aiClient.ask(eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(ProfileLines.Answer.class)))
        .willReturn(Optional.of(new ProfileLines.Answer(List.of("하나", "둘", "셋"))));

    assertThat(service.suggest()).isEqualTo(new ProfileLinesResponse(List.of("하나", "둘", "셋")));

    ArgumentCaptor<AiQuestion> question = ArgumentCaptor.forClass(AiQuestion.class);
    verify(aiClient)
        .ask(
            eq(USER_ID),
            eq(GptUsageKind.ASSIST),
            question.capture(),
            eq(ProfileLines.Answer.class));
    assertThat(question.getValue().input())
        .isEqualTo(
            """
            척도 (이 순서대로 한 줄씩):
            1. 시작 저항 — 높음 (마음먹고 시작하기까지 꾸물거림)
            2. 관리 선호 — 낮음 (따끔한 관리를 원함)
            3. 주의 분산 — 보통 (공부 중 폰·딴생각에 흔들림)""");
  }

  @Test
  @DisplayName("서버에 심리검사가 없으면 AI 를 부르지 않고 lines: null 이다")
  void noTest() {
    given(psychTestRepository.findFirstByUserIdOrderByIdDesc(USER_ID)).willReturn(Optional.empty());

    assertThat(service.suggest()).isEqualTo(new ProfileLinesResponse(null));
    verifyNoInteractions(aiClient);
  }

  @Test
  @DisplayName("심리검사 답이 거의 다 같아서 믿기 어려우면 AI 를 부르지 않고 정해 둔 3줄이다")
  void unsureTest() {
    Map<PsychItem, Integer> answers = new EnumMap<>(PsychItem.class);
    for (PsychItem item : PsychItem.values()) {
      answers.put(item, 7);
    }
    given(psychTestRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
        .willReturn(
            Optional.of(new PsychTest(USER_ID, Instant.parse("2026-10-17T09:00:00Z"), answers)));

    assertThat(service.suggest()).isEqualTo(new ProfileLinesResponse(ProfileLines.UNSURE_LINES));
    verifyNoInteractions(aiClient);
    // 정해 둔 줄도 AI 의 줄과 같은 규칙(60자 · 숫자 없음)을 지킵니다.
    assertThat(ProfileLines.UNSURE_LINES)
        .hasSize(3)
        .allSatisfy(
            line ->
                assertThat(line)
                    .hasSizeLessThanOrEqualTo(ProfileLines.MAX_LINE_LENGTH)
                    .doesNotContainPattern("\\d"));
  }

  @Test
  @DisplayName("AI 가 못 만들었거나 쓸 수 없는 답이면 lines: null 이다")
  void aiUnusable() {
    latestTest();
    given(aiClient.ask(any(), any(), any(), eq(ProfileLines.Answer.class)))
        .willReturn(Optional.empty());
    assertThat(service.suggest().lines()).isNull();

    given(aiClient.ask(any(), any(), any(), eq(ProfileLines.Answer.class)))
        .willReturn(Optional.of(new ProfileLines.Answer(List.of("하나", "둘"))));
    assertThat(service.suggest().lines()).isNull();
  }
}
