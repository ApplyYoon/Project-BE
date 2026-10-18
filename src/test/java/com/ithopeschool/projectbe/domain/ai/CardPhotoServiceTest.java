package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.ai.dto.CardParseResponse;
import com.ithopeschool.projectbe.domain.ai.dto.CardPhotoRequest;
import com.ithopeschool.projectbe.domain.ai.dto.CardPhotoResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Service 단위 테스트: AI(AiClient)와 맥락(CardParseService)을 가짜로 두고, 검사 · 순서 · 결과만 확인합니다.
class CardPhotoServiceTest {

  private static final Long USER_ID = 7L;
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 18);
  // 작은 jpeg 처럼 보이는 사진 (내용은 상관없습니다. 모양만 봅니다)
  private static final String IMAGE =
      "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(new byte[] {1, 2, 3, 4});

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final CardParseService cardParseService = mock(CardParseService.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final CardPhotoService service =
      new CardPhotoService(currentUserProvider, cardParseService, aiClient);

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(cardParseService.contextOf(USER_ID))
        .willReturn(
            new CardParseService.Context(
                List.of(new CardParse.GoalHint("4", "11월 학평 수학 3등급", LocalDate.of(2026, 11, 19))),
                Set.of("4"),
                List.of("수학 문제집"),
                new BlockLength.Choice(25, "기록 이유")));
  }

  private void givenAnswer(CardPhoto.Answer answer) {
    given(aiClient.askWithImage(any(), any(), any(), any(), eq(CardPhoto.Answer.class)))
        .willReturn(Optional.ofNullable(answer));
  }

  private static CardParse.Answer.Item card(int dayOffset, String title, String goalId) {
    return new CardParse.Answer.Item(dayOffset, null, null, title, List.of(title + " 하기"), goalId);
  }

  private static CardPhotoRequest request(LocalDate examDate, String note) {
    return new CardPhotoRequest(IMAGE, TODAY, examDate, note);
  }

  @Test
  @DisplayName("사진과 질문을 AI 에 보내고(ASSIST 한도), 시험 전날까지의 카드만 남기고, 읽은 범위 한 줄을 돌려준다")
  void makesDraftsUntilTheDayBeforeTheExam() {
    givenAnswer(
        new CardPhoto.Answer(
            false,
            true,
            " 수학 3~4단원 ",
            List.of(
                card(0, "수학 3단원 개념", "4"), card(2, "수학 4단원 개념", "99"), card(3, "수학 전체 복습", null))));

    // 시험이 3일 뒤(10-21) → 카드는 2일 뒤(10-20)까지
    CardPhotoResponse response = service.make(request(TODAY.plusDays(3), "수학만"));

    assertThat(response.showHelp()).isFalse();
    assertThat(response.scopeText()).isEqualTo("수학 3~4단원");
    assertThat(response.durationEvidence()).isEqualTo("기록 이유");
    assertThat(response.drafts())
        .extracting(
            CardParseResponse.Draft::date,
            CardParseResponse.Draft::title,
            CardParseResponse.Draft::goalId)
        .containsExactly(
            tuple(TODAY, "수학 3단원 개념", "4"), tuple(TODAY.plusDays(2), "수학 4단원 개념", null));
    assertThat(response.drafts())
        .allMatch(draft -> draft.startTime() == null && draft.durationMinutes() == 25);
    verify(aiClient)
        .askWithImage(
            eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(IMAGE), eq(CardPhoto.Answer.class));
  }

  @Test
  @DisplayName("AI 가 못 답하면(꺼짐 · 한도 · 실패) drafts 가 null 이다")
  void notMadeWhenNoAnswer() {
    givenAnswer(null);

    assertThat(service.make(request(null, null))).isEqualTo(CardPhotoResponse.notMade());
  }

  @Test
  @DisplayName("시험 범위 사진이 아니라고 하면 drafts 가 null 이다")
  void notMadeWhenNotExamScope() {
    givenAnswer(new CardPhoto.Answer(false, false, null, List.of(card(0, "수학", null))));

    assertThat(service.make(request(null, null))).isEqualTo(CardPhotoResponse.notMade());
  }

  @Test
  @DisplayName("메모에 위기 표현이 있으면 AI 를 부르지 않고 showHelp, AI 가 위기라고 해도 showHelp")
  void crisisMeansHelp() {
    assertThat(service.make(request(null, "죽고 싶다"))).isEqualTo(CardPhotoResponse.help());
    verifyNoInteractions(aiClient);

    givenAnswer(new CardPhoto.Answer(true, true, "수학", List.of(card(0, "수학", null))));
    assertThat(service.make(request(null, null))).isEqualTo(CardPhotoResponse.help());
  }

  @Test
  @DisplayName("data URL 이 아니거나 종류가 다르거나 base64 가 깨졌거나 4MB 를 넘으면 400 이고 AI 를 부르지 않는다")
  void rejectsBadImages() {
    assertRejected("aGVsbG8=", "사진은 jpeg · png · webp 의 data URL 로 보내 주세요");
    assertRejected("data:image/gif;base64,R0lG", "사진은 jpeg · png · webp 의 data URL 로 보내 주세요");
    assertRejected("data:image/jpeg;base64,@@@", "사진을 읽을 수 없어요. 다시 찍어 주세요");
    assertRejected("data:image/jpeg;base64,", "사진을 읽을 수 없어요. 다시 찍어 주세요");
    String tooBig =
        "data:image/png;base64,"
            + Base64.getEncoder().encodeToString(new byte[CardPhotoService.MAX_IMAGE_BYTES + 1]);
    assertRejected(tooBig, "사진이 너무 커요. 4MB 이하로 보내 주세요");
    verifyNoInteractions(aiClient);
  }

  @Test
  @DisplayName("시험 날짜가 기준 날짜보다 빠르면 400 이다")
  void rejectsExamBeforeBase() {
    assertThatThrownBy(() -> service.make(request(TODAY.minusDays(1), null)))
        .isInstanceOf(ApiException.class)
        .hasMessage("시험 날짜는 기준 날짜보다 빠를 수 없어요");
    verifyNoInteractions(aiClient);
  }

  private void assertRejected(String image, String message) {
    assertThatThrownBy(() -> service.make(new CardPhotoRequest(image, TODAY, null, null)))
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }
}
