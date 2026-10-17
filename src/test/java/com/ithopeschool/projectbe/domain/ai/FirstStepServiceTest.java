package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.ai.dto.FirstStepRequest;
import com.ithopeschool.projectbe.domain.ai.dto.FirstStepResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// Service 단위 테스트: AI(AiClient)를 가짜로 바꿔서 순서와 AI 에게 주는 글을 확인합니다.
class FirstStepServiceTest {

  private static final Long USER_ID = 7L;

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final FirstStepService service = new FirstStepService(currentUserProvider, aiClient);

  private void aiAnswers(String step) {
    given(aiClient.ask(eq(USER_ID), eq(GptUsageKind.CHECKLIST), any(), eq(FirstStep.Answer.class)))
        .willReturn(Optional.of(new FirstStep.Answer(step)));
  }

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
  }

  @Test
  @DisplayName("[더 작게]: 단계와 앞의 행동을 AI 에게 주고, 한도는 체크리스트(CHECKLIST)로 센다")
  void smaller() {
    aiAnswers("수학 문제집 꺼내기");

    FirstStepResponse response =
        service.suggest(new FirstStepRequest("42~45쪽 문제 풀기", "수학 문제집", 1, "수학 문제집 42쪽 펴기"));

    assertThat(response).isEqualTo(FirstStepResponse.of("수학 문제집 꺼내기"));
    ArgumentCaptor<AiQuestion> question = ArgumentCaptor.forClass(AiQuestion.class);
    verify(aiClient)
        .ask(
            eq(USER_ID),
            eq(GptUsageKind.CHECKLIST),
            question.capture(),
            eq(FirstStep.Answer.class));
    // 이름·학교 같은 개인정보가 들어가지 않는지 보려고, 입력 글 전체가 정확히 이것인지 봅니다.
    assertThat(question.getValue().input())
        .isEqualTo(FirstStep.input("42~45쪽 문제 풀기", "수학 문제집", 1, "수학 문제집 42쪽 펴기"));
  }

  @Test
  @DisplayName("AI 가 못 만들었거나 앞의 행동과 같은 답이면 에러가 아니라 step: null 이다")
  void unusableIsNull() {
    given(aiClient.ask(any(), any(), any(), eq(FirstStep.Answer.class)))
        .willReturn(Optional.empty());
    assertThat(service.suggest(new FirstStepRequest("단어 외우기", null, null, null)))
        .isEqualTo(FirstStepResponse.of(null));

    aiAnswers("단어장 펴기");
    assertThat(service.suggest(new FirstStepRequest("단어 외우기", null, 1, "단어장 펴기")).step()).isNull();
  }

  @Test
  @DisplayName("첫 항목 · 카드 제목 · 앞의 행동 중 하나라도 위기 표현이 있으면 AI 를 부르지 않고 showHelp 다")
  void crisisShowsHelp() {
    assertThat(service.suggest(new FirstStepRequest("죽고 싶다", null, 0, null)))
        .isEqualTo(FirstStepResponse.help());
    assertThat(service.suggest(new FirstStepRequest("단어 외우기", "그냥 사라지고 싶어", 0, null)))
        .isEqualTo(FirstStepResponse.help());
    assertThat(service.suggest(new FirstStepRequest("단어 외우기", null, 1, "자해")))
        .isEqualTo(FirstStepResponse.help());
    verifyNoInteractions(aiClient);
  }
}
