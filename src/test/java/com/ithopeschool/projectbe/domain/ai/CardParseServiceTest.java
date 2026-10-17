package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.ai.dto.CardParseRequest;
import com.ithopeschool.projectbe.domain.ai.dto.CardParseResponse;
import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.card.ChecklistItem;
import com.ithopeschool.projectbe.domain.goal.Goal;
import com.ithopeschool.projectbe.domain.goal.GoalKind;
import com.ithopeschool.projectbe.domain.goal.GoalRepository;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

// Service 단위 테스트: DB 와 AI(AiClient)를 가짜로 바꿔서 "DB 에서 무엇을 읽어 AI 에게 주는지" 와 결과 모양을 확인합니다.
class CardParseServiceTest {

  private static final Long USER_ID = 7L;
  private static final LocalDate BASE = LocalDate.of(2026, 10, 17);
  private static final LocalDate EXAM_DAY = LocalDate.of(2026, 11, 19);

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final GoalRepository goalRepository = mock(GoalRepository.class);
  private final CardRepository cardRepository = mock(CardRepository.class);
  private final BlockLengthService blockLengthService = mock(BlockLengthService.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final CardParseService service =
      new CardParseService(
          currentUserProvider, goalRepository, cardRepository, blockLengthService, aiClient);

  private static Goal goal(long id, String title, boolean active) {
    Goal goal =
        new Goal(
            USER_ID,
            GoalKind.SCORE,
            new Goal.Target(title, EXAM_DAY, "5등급", null, null),
            new Goal.Woop("a", "b", "c", "d"),
            Instant.EPOCH);
    ReflectionTestUtils.setField(goal, "id", id);
    if (!active) {
      goal.giveUp("쉼");
    }
    return goal;
  }

  private static Card card(String title) {
    return new Card(
        USER_ID, title, BASE, 30, List.of(new ChecklistItem("i1", "하기")), Instant.EPOCH);
  }

  private void aiAnswers(CardParse.Answer answer) {
    given(aiClient.ask(eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(CardParse.Answer.class)))
        .willReturn(Optional.ofNullable(answer));
  }

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(goalRepository.findByUserIdOrderById(USER_ID))
        .willReturn(List.of(goal(4, "수학 3등급", true), goal(5, "영단어", false)));
    given(cardRepository.findByUserIdOrderById(USER_ID))
        .willReturn(List.of(card("영어 단어"), card("수학 문제집"), card("영어 단어")));
    given(blockLengthService.choose(USER_ID))
        .willReturn(new BlockLength.Choice(25, "최근 14일 공부 기록 8번의 한 번 길이 가운데값이 24분이라 25분으로 잡았어요"));
  }

  @Test
  @DisplayName("AI 에게 진행 중 목표만 · 최근 카드 제목을 최근 것부터 겹치지 않게 주고, 작은 AI 기능 한도(ASSIST)를 쓴다")
  void givesContextFromDb() {
    aiAnswers(new CardParse.Answer(false, List.of()));

    service.parse(new CardParseRequest(BASE, "내일 7시에 수학 문제집 2장"));

    ArgumentCaptor<AiQuestion> question = ArgumentCaptor.forClass(AiQuestion.class);
    verify(aiClient)
        .ask(eq(USER_ID), eq(GptUsageKind.ASSIST), question.capture(), eq(CardParse.Answer.class));
    assertThat(question.getValue().input())
        .isEqualTo(
            """
            기준 날짜: 2026-10-17 (토)
            내 진행 중 목표:
            - 4: 수학 3등급 (D-day 2026-11-19)
            최근 카드 제목: 영어 단어 / 수학 문제집

            학생이 말한 문장:
            내일 7시에 수학 문제집 2장""");
  }

  @Test
  @DisplayName("길이를 말하지 않은 카드는 내 한 칸 길이로 채우고 그 이유를 같이 준다. 포기한 목표 번호는 비운다")
  void draftsWithRecordLength() {
    aiAnswers(
        new CardParse.Answer(
            false,
            List.of(
                new CardParse.Answer.Item(1, "19:00", null, "수학 문제집", List.of("문제집 2장 풀기"), "4"),
                new CardParse.Answer.Item(1, null, 30, "영어 단어", List.of("단어 30개"), "5"))));

    CardParseResponse response =
        service.parse(new CardParseRequest(BASE, "내일 7시에 수학 문제집 2장, 영어 단어 30분"));

    assertThat(response.showHelp()).isFalse();
    assertThat(response.drafts())
        .containsExactly(
            new CardParseResponse.Draft(
                LocalDate.of(2026, 10, 18), "19:00", 25, true, "수학 문제집", List.of("문제집 2장 풀기"), "4"),
            new CardParseResponse.Draft(
                LocalDate.of(2026, 10, 18), null, 30, false, "영어 단어", List.of("단어 30개"), null));
    assertThat(response.durationEvidence()).startsWith("최근 14일 공부 기록 8번");
  }

  @Test
  @DisplayName("모든 카드의 길이를 말했으면 durationEvidence 는 null")
  void noEvidenceWhenAllLengthsSaid() {
    aiAnswers(
        new CardParse.Answer(
            false, List.of(new CardParse.Answer.Item(0, null, 60, "수학", List.of("오답"), null))));

    assertThat(service.parse(new CardParseRequest(BASE, "수학 1시간")).durationEvidence()).isNull();
  }

  @Test
  @DisplayName("문장에 위기 표현이 있으면 AI 를 부르지 않고 showHelp")
  void crisisWordsSkipAi() {
    CardParseResponse response = service.parse(new CardParseRequest(BASE, "그냥 죽고 싶다"));

    assertThat(response.showHelp()).isTrue();
    assertThat(response.drafts()).isNull();
    verifyNoInteractions(aiClient);
  }

  @Test
  @DisplayName("AI 가 위기 표현이라고 하면 showHelp, AI 가 답하지 못하면 drafts 가 null (에러 아님)")
  void aiCrisisAndFailure() {
    aiAnswers(new CardParse.Answer(true, List.of()));
    assertThat(service.parse(new CardParseRequest(BASE, "수학")).showHelp()).isTrue();

    aiAnswers(null);
    CardParseResponse failed = service.parse(new CardParseRequest(BASE, "수학"));
    assertThat(failed.showHelp()).isFalse();
    assertThat(failed.drafts()).isNull();
  }
}
