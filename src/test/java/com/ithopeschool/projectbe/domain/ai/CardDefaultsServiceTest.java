package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.ai.dto.CardDefaultsRequest;
import com.ithopeschool.projectbe.domain.ai.dto.CardDefaultsResponse;
import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.card.CardType;
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

// Service 단위 테스트: 같은 제목 카드가 있을 때(코드) · 없을 때(AI) 를 나눠서, 무엇을 채우고 무엇을 AI 에게 주는지 확인합니다.
class CardDefaultsServiceTest {

  private static final Long USER_ID = 7L;
  private static final LocalDate EXAM_DAY = LocalDate.of(2026, 11, 19);

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final CardRepository cardRepository = mock(CardRepository.class);
  private final GoalRepository goalRepository = mock(GoalRepository.class);
  private final BlockLengthService blockLengthService = mock(BlockLengthService.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final CardDefaultsService service =
      new CardDefaultsService(
          currentUserProvider, cardRepository, goalRepository, blockLengthService, aiClient);

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

  private static Card card(String title, String date, int minutes, CardType type, Long goalId) {
    Card card =
        new Card(
            USER_ID,
            title,
            LocalDate.parse(date),
            minutes,
            List.of(new ChecklistItem("i1", "하기")),
            Instant.EPOCH);
    card.changeType(type);
    card.changeGoalId(goalId);
    return card;
  }

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(goalRepository.findByUserIdOrderById(USER_ID))
        .willReturn(List.of(goal(4, "수학 3등급", true), goal(5, "영단어", false)));
    given(blockLengthService.choose(USER_ID))
        .willReturn(new BlockLength.Choice(25, "아직 공부 기록이 적어서 25분으로 잡았어요"));
  }

  @Test
  @DisplayName("같은 제목의 카드가 있으면 가장 최근 것의 목표 · 유형 · 길이를 따르고 AI 를 부르지 않는다")
  void followsLastCardWithSameTitle() {
    given(cardRepository.findByUserIdOrderById(USER_ID))
        .willReturn(
            List.of(
                card("수학 인강", "2026-10-10", 40, CardType.SELF, null),
                card("수학 인강 ", "2026-10-15", 60, CardType.LECTURE, 4L),
                card("영어", "2026-10-16", 30, CardType.SELF, null)));

    CardDefaultsResponse response = service.suggest(new CardDefaultsRequest("수학 인강"));

    assertThat(response)
        .isEqualTo(
            new CardDefaultsResponse(
                false,
                "4",
                "lecture",
                60,
                List.of("지난번 같은 제목의 카드(2026-10-15)와 같은 목표 · 유형 · 길이로 채웠어요")));
    verifyNoInteractions(aiClient, blockLengthService);
  }

  @Test
  @DisplayName("지난 카드의 목표가 이제 진행 중이 아니면 목표는 비우고 그 이유를 더한다")
  void dropsFinishedGoal() {
    given(cardRepository.findByUserIdOrderById(USER_ID))
        .willReturn(List.of(card("영단어", "2026-10-15", 30, CardType.SELF, 5L)));

    CardDefaultsResponse response = service.suggest(new CardDefaultsRequest("영단어"));

    assertThat(response.goalId()).isNull();
    assertThat(response.evidence())
        .containsExactly(
            "지난번 같은 제목의 카드(2026-10-15)와 같은 목표 · 유형 · 길이로 채웠어요",
            "지난번에 이은 목표는 지금 진행 중이 아니라서 목표는 비워 두었어요");
  }

  @Test
  @DisplayName("처음 보는 제목이면 길이는 내 한 칸 길이, 목표 · 유형은 AI 가 진행 중 목표 중에서 고른다")
  void newTitleAsksAi() {
    given(cardRepository.findByUserIdOrderById(USER_ID)).willReturn(List.of());
    given(aiClient.ask(eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(CardDefaults.Answer.class)))
        .willReturn(Optional.of(new CardDefaults.Answer("4", "lecture")));

    CardDefaultsResponse response = service.suggest(new CardDefaultsRequest("수학 인강 3강"));

    assertThat(response)
        .isEqualTo(
            new CardDefaultsResponse(
                false,
                "4",
                "lecture",
                25,
                List.of("아직 공부 기록이 적어서 25분으로 잡았어요", "제목을 보고 「수학 3등급」 목표에 이었어요")));
    ArgumentCaptor<AiQuestion> question = ArgumentCaptor.forClass(AiQuestion.class);
    verify(aiClient)
        .ask(
            eq(USER_ID),
            eq(GptUsageKind.ASSIST),
            question.capture(),
            eq(CardDefaults.Answer.class));
    assertThat(question.getValue().input())
        .isEqualTo("카드 제목: 수학 인강 3강\n내 진행 중 목표:\n- 4: 수학 3등급 (D-day 2026-11-19)");
  }

  @Test
  @DisplayName("AI 가 포기한 목표 번호나 모르는 유형을 고르면 비우고, AI 가 실패해도 길이는 채운다")
  void badOrMissingAiAnswer() {
    given(cardRepository.findByUserIdOrderById(USER_ID)).willReturn(List.of());
    given(aiClient.ask(eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(CardDefaults.Answer.class)))
        .willReturn(Optional.of(new CardDefaults.Answer("5", "video")));

    CardDefaultsResponse picked = service.suggest(new CardDefaultsRequest("영단어 외우기"));
    assertThat(picked.goalId()).isNull();
    assertThat(picked.type()).isNull();

    given(aiClient.ask(eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(CardDefaults.Answer.class)))
        .willReturn(Optional.empty());
    CardDefaultsResponse failed = service.suggest(new CardDefaultsRequest("영단어 외우기"));
    assertThat(failed.durationMinutes()).isEqualTo(25);
    assertThat(failed.goalId()).isNull();
  }

  @Test
  @DisplayName("제목에 위기 표현이 있으면 아무것도 읽거나 부르지 않고 showHelp")
  void crisis() {
    CardDefaultsResponse response = service.suggest(new CardDefaultsRequest("자해"));

    assertThat(response.showHelp()).isTrue();
    verifyNoInteractions(aiClient, cardRepository, goalRepository);
  }
}
