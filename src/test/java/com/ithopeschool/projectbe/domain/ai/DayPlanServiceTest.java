package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.ai.dto.DayPlanRequest;
import com.ithopeschool.projectbe.domain.ai.dto.DayPlanResponse;
import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.card.ChecklistItem;
import com.ithopeschool.projectbe.domain.fixedblock.FixedBlock;
import com.ithopeschool.projectbe.domain.fixedblock.FixedBlockRepository;
import com.ithopeschool.projectbe.domain.goal.Goal;
import com.ithopeschool.projectbe.domain.goal.GoalKind;
import com.ithopeschool.projectbe.domain.goal.GoalRepository;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.onboarding.Chronotype;
import com.ithopeschool.projectbe.domain.onboarding.Grade;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswer;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswerRepository;
import com.ithopeschool.projectbe.domain.psychtest.PsychTestRepository;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

// Service 단위 테스트: DB 와 AI 를 가짜로 바꿔서 "어떤 재료로 어느 시각에 카드를 놓는지" 와 AI 에게 주는 글을 확인합니다.
class DayPlanServiceTest {

  private static final Long USER_ID = 7L;
  // 한국 시간 2026-10-17(토) 12:00 → 오늘은 10-17
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-17T03:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
  // 2026-10-19 는 월요일
  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final StudySessionRepository sessions = mock(StudySessionRepository.class);
  private final OnboardingAnswerRepository onboarding = mock(OnboardingAnswerRepository.class);
  private final PsychTestRepository psychTests = mock(PsychTestRepository.class);
  private final FixedBlockRepository fixedBlocks = mock(FixedBlockRepository.class);
  private final CardRepository cards = mock(CardRepository.class);
  private final GoalRepository goals = mock(GoalRepository.class);
  private final BlockLengthService blockLength = mock(BlockLengthService.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final DayPlanService service =
      new DayPlanService(
          currentUserProvider,
          sessions,
          onboarding,
          psychTests,
          fixedBlocks,
          cards,
          goals,
          blockLength,
          aiClient,
          CLOCK);

  private static OnboardingAnswer answer(LocalTime sleep, LocalTime wake, Chronotype chronotype) {
    OnboardingAnswer.Values values = new OnboardingAnswer.Values();
    values.grade = Grade.HIGH_2;
    values.sleepTime = sleep;
    values.wakeUpTime = wake;
    values.chronotype = chronotype;
    return new OnboardingAnswer(USER_ID, Instant.EPOCH, values);
  }

  private static FixedBlock school() {
    return new FixedBlock(
        USER_ID,
        "학교",
        List.of(1, 2, 3, 4, 5),
        LocalTime.of(8, 0),
        LocalTime.of(16, 30),
        Instant.EPOCH);
  }

  private static Card card(String title, LocalDate date, LocalTime start, int minutes) {
    Card card =
        new Card(
            USER_ID, title, date, minutes, List.of(new ChecklistItem("i1", "하기")), Instant.EPOCH);
    card.changeStartTime(start);
    return card;
  }

  // 완주형 목표 "영단어장 1회독" 300쪽, D-day 10-28 (월요일 기준 9일 남음 → 하루 30쪽), 번호 4
  private static Goal wordBook() {
    Goal goal =
        new Goal(
            USER_ID,
            GoalKind.COMPLETION,
            new Goal.Target("영단어장 1회독", LocalDate.of(2026, 10, 28), null, 300, "쪽"),
            new Goal.Woop("a", "b", "c", "d"),
            Instant.EPOCH);
    ReflectionTestUtils.setField(goal, "id", 4L);
    return goal;
  }

  private void aiAnswers(DayPlanPrompt.Answer answer) {
    given(aiClient.ask(eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(DayPlanPrompt.Answer.class)))
        .willReturn(Optional.ofNullable(answer));
  }

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(sessions.findStartedBetween(eq(USER_ID), any(), any())).willReturn(List.of());
    given(psychTests.findFirstByUserIdOrderByIdDesc(USER_ID)).willReturn(Optional.empty());
    given(blockLength.choose(USER_ID))
        .willReturn(new BlockLength.Choice(50, "아직 공부 기록이 적어서 50분으로 잡았어요"));
    given(onboarding.findFirstByUserIdOrderByAnsweredAtDescIdDesc(USER_ID))
        .willReturn(
            Optional.of(answer(LocalTime.of(23, 0), LocalTime.of(7, 0), Chronotype.MORNING)));
    given(fixedBlocks.findByUserIdOrderById(USER_ID)).willReturn(List.of(school()));
    given(cards.findCandidatesOn(eq(USER_ID), any())).willReturn(List.of());
    given(cards.findByUserIdOrderById(USER_ID)).willReturn(List.of());
    given(goals.findByUserIdOrderById(USER_ID)).willReturn(List.of(wordBook()));
  }

  @Test
  @DisplayName("학교 · 그날 카드 · 수면을 피해 빈 시간에 카드를 놓고, 목표의 하루 분량을 AI 에게 준다")
  void placesCardsAroundSchoolAndCards() {
    Card evening = card("수학 학원 숙제", MONDAY, LocalTime.of(19, 0), 60);
    given(cards.findCandidatesOn(USER_ID, MONDAY)).willReturn(List.of(evening));
    aiAnswers(
        new DayPlanPrompt.Answer(
            List.of(new DayPlanPrompt.Answer.Item("영단어 30쪽", List.of("1 ~ 30쪽 외우기")))));

    DayPlanResponse response = service.suggest(new DayPlanRequest(MONDAY));

    // 총량 120분(기본) − 그날 카드 60분 = 60분 → 50분 칸 하나 (남은 10분은 30분보다 짧아서 버림)
    assertThat(response.totalMinutes()).isEqualTo(60);
    assertThat(response.drafts())
        .containsExactly(
            new DayPlanResponse.Draft(MONDAY, "16:40", 50, "영단어 30쪽", List.of("1 ~ 30쪽 외우기"), "4"));
    assertThat(response.evidence())
        .contains(
            "고정 일정 「학교」 08:00 ~ 16:30 을 피했어요",
            "그날 이미 있는 카드 1장의 앞뒤 10분을 비웠어요",
            "그날 이미 있는 카드 60분을 뺐어요",
            "취침 23:00 · 기상 07:00 이라 수면 시간(최소 6시간)과 일어난 뒤 1시간을 빼고 08:00 ~ 23:00 사이에 짰어요",
            "남은 10분은 30분보다 짧아서 칸을 만들지 않았어요",
            "D-day 가 가까운 목표를 더 많이 넣었어요");

    ArgumentCaptor<AiQuestion> question = ArgumentCaptor.forClass(AiQuestion.class);
    verify(aiClient)
        .ask(
            eq(USER_ID),
            eq(GptUsageKind.ASSIST),
            question.capture(),
            eq(DayPlanPrompt.Answer.class));
    assertThat(question.getValue().input())
        .isEqualTo(
            """
            날짜: 10/19
            칸 (이 순서대로 하나씩):
            1. 16:40 · 50분 · 목표 「영단어장 1회독」 D-day 까지 9일 · 하루 분량 30쪽
            최근 카드 제목: (없음)""");
  }

  @Test
  @DisplayName("AI 가 못 쓰면 목표 이름과 하루 분량으로 채운다 (AI 가 없어도 계획은 나온다)")
  void fallbackWithoutAi() {
    aiAnswers(null);

    DayPlanResponse response = service.suggest(new DayPlanRequest(MONDAY));

    // 총량 120분 → 50 · 50분 (남은 20분은 30분보다 짧아서 칸을 만들지 않음 — 20분짜리 카드는 없음)
    assertThat(response.drafts())
        .extracting(DayPlanResponse.Draft::durationMinutes)
        .containsExactly(50, 50);
    assertThat(response.drafts().get(0))
        .isEqualTo(
            new DayPlanResponse.Draft(MONDAY, "16:40", 50, "영단어장 1회독", List.of("30쪽 하기"), "4"));
    assertThat(response.drafts().get(1).startTime()).isEqualTo("17:40");
  }

  @Test
  @DisplayName("저녁형이면 늦은 시간부터 채운다")
  void nightTypeFillsLate() {
    given(onboarding.findFirstByUserIdOrderByAnsweredAtDescIdDesc(USER_ID))
        .willReturn(Optional.of(answer(LocalTime.of(23, 0), LocalTime.of(7, 0), Chronotype.NIGHT)));
    aiAnswers(null);

    DayPlanResponse response = service.suggest(new DayPlanRequest(MONDAY));

    assertThat(response.drafts())
        .extracting(DayPlanResponse.Draft::startTime)
        .containsExactly("21:10", "22:10");
    assertThat(response.evidence()).contains("저녁형이라고 해서 늦은 시간부터 채웠어요");
  }

  @Test
  @DisplayName("설문 · 고정 일정 · 목표가 없으면 기본 시간에, 최근 카드 제목으로 채우고 그 사실을 근거에 적는다")
  void withoutMaterials() {
    given(onboarding.findFirstByUserIdOrderByAnsweredAtDescIdDesc(USER_ID))
        .willReturn(Optional.empty());
    given(fixedBlocks.findByUserIdOrderById(USER_ID)).willReturn(List.of());
    given(goals.findByUserIdOrderById(USER_ID)).willReturn(List.of());
    given(cards.findByUserIdOrderById(USER_ID))
        .willReturn(List.of(card("수학 문제집", TODAY, null, 50)));
    aiAnswers(null);

    DayPlanResponse response = service.suggest(new DayPlanRequest(MONDAY));

    assertThat(response.drafts().get(0))
        .isEqualTo(
            new DayPlanResponse.Draft(MONDAY, "07:00", 50, "수학 문제집", List.of("수학 문제집"), null));
    assertThat(response.evidence())
        .contains(
            "그날 고정 일정이 없어요. 학교 · 학원 시간을 고정 일정에 넣으면 피해서 짜요",
            "취침 · 기상 시각을 아직 몰라서 07:00 ~ 00:00 사이에 짰어요. 관리 설정에서 넣으면 맞춰 짜요",
            "진행 중인 목표가 없어서 최근 카드로 채웠어요");
  }

  @Test
  @DisplayName("오늘이면 지금 시각 뒤부터 놓는다 (토요일이라 학교 없음)")
  void todayStartsFromNow() {
    aiAnswers(null);

    DayPlanResponse response = service.suggest(new DayPlanRequest(TODAY));

    assertThat(response.drafts().get(0).startTime()).isEqualTo("12:00");
  }

  @Test
  @DisplayName("그날 카드가 이미 하루 총량만큼 있으면 빈 목록과 이유")
  void alreadyEnough() {
    given(cards.findCandidatesOn(USER_ID, MONDAY))
        .willReturn(List.of(card("수학", MONDAY, LocalTime.of(19, 0), 120)));

    DayPlanResponse response = service.suggest(new DayPlanRequest(MONDAY));

    assertThat(response.drafts()).isEmpty();
    assertThat(response.evidence()).contains("그날은 이미 충분히 계획했어요");
  }

  @Test
  @DisplayName("날짜가 오늘보다 앞이거나 14일보다 뒤면 400 이다")
  void dateRange() {
    assertThatThrownBy(() -> service.suggest(new DayPlanRequest(TODAY.minusDays(1))))
        .isInstanceOf(ApiException.class)
        .hasMessage("오늘부터 14일 안의 날짜를 보내 주세요");
    assertThatThrownBy(() -> service.suggest(new DayPlanRequest(TODAY.plusDays(15))))
        .isInstanceOf(ApiException.class);
  }

  @Test
  @DisplayName("컨디션이 2 이하면 총량을 60% 로 줄여 가볍게 짜고 근거에 적는다 · 준비하는 것과 과목을 AI 입력에 넣는다")
  void lowConditionMakesLighterPlan() {
    aiAnswers(null);

    DayPlanResponse usual = service.suggest(new DayPlanRequest(MONDAY));
    DayPlanResponse lighter =
        service.suggest(
            new DayPlanRequest(
                MONDAY, new DayPlanRequest.Focus("SUNEUNG", null), List.of("미적분Ⅰ"), 2));

    assertThat(lighter.totalMinutes()).isEqualTo(usual.totalMinutes() * 60 / 100);
    assertThat(lighter.evidence()).anyMatch(line -> line.contains("가볍게 짰어요"));
    assertThat(
            DayPlanService.extraLinesOf(
                new DayPlanRequest(
                    MONDAY, new DayPlanRequest.Focus("CONTEST", "정보올림피아드"), List.of("미적분Ⅰ"), 3)))
        .containsExactly("준비하는 것: 대회 · 자격증 — 정보올림피아드", "오늘 배운 과목: 미적분Ⅰ");
  }
}
