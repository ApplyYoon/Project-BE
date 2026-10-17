package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.ai.dto.TextLabelRequest;
import com.ithopeschool.projectbe.domain.ai.dto.TextLabelResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// Service 단위 테스트: AI(AiClient)를 가짜로 바꿔서 순서와 AI 에게 주는 글을 확인합니다.
class TextLabelServiceTest {

  private static final Long USER_ID = 7L;

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final TextLabelService service = new TextLabelService(currentUserProvider, aiClient);

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
  }

  @Test
  @DisplayName("AI 가 고른 이름표를 돌려주고, AI 에게는 보낸 글 하나만 준다 (한도는 ASSIST)")
  void labels() {
    given(aiClient.ask(eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(TextLabel.Answer.class)))
        .willReturn(Optional.of(new TextLabel.Answer("contact")));

    TextLabelResponse response = service.label(new TextLabelRequest("memo", " 엄마한테 학원비 문자 보내기 "));

    assertThat(response).isEqualTo(TextLabelResponse.of("contact"));
    ArgumentCaptor<AiQuestion> question = ArgumentCaptor.forClass(AiQuestion.class);
    verify(aiClient)
        .ask(eq(USER_ID), eq(GptUsageKind.ASSIST), question.capture(), eq(TextLabel.Answer.class));
    assertThat(question.getValue().input()).isEqualTo("글: 엄마한테 학원비 문자 보내기");
  }

  @Test
  @DisplayName("AI 가 못 붙였거나 그 종류의 이름표가 아니면 label: null 이다")
  void unusableIsNull() {
    given(aiClient.ask(any(), any(), any(), eq(TextLabel.Answer.class)))
        .willReturn(Optional.empty());
    assertThat(service.label(new TextLabelRequest("reason", "그냥")))
        .isEqualTo(TextLabelResponse.of(null));

    given(aiClient.ask(any(), any(), any(), eq(TextLabel.Answer.class)))
        .willReturn(Optional.of(new TextLabel.Answer("worry")));
    assertThat(service.label(new TextLabelRequest("reason", "그냥")).label()).isNull();
  }

  @Test
  @DisplayName("글에 위기 표현이 있으면 AI 를 부르지 않고 showHelp 다 (딴생각 메모도)")
  void crisisShowsHelp() {
    assertThat(service.label(new TextLabelRequest("memo", "다 그만두고 사라지고 싶다")))
        .isEqualTo(TextLabelResponse.help());
    assertThat(service.label(new TextLabelRequest("reason", "자해 생각이 났다")))
        .isEqualTo(TextLabelResponse.help());
    verifyNoInteractions(aiClient);
  }
}
