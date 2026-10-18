package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.ai.dto.ChecklistRequest;
import com.ithopeschool.projectbe.domain.ai.dto.ChecklistResponse;
import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.psychtest.PsychItem;
import com.ithopeschool.projectbe.domain.psychtest.PsychScale;
import com.ithopeschool.projectbe.domain.psychtest.PsychTest;
import com.ithopeschool.projectbe.domain.psychtest.PsychTestRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.thinkingtest.ThinkingTest;
import com.ithopeschool.projectbe.domain.thinkingtest.ThinkingTestRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

// Service 단위 테스트: AI(AiClient)와 DB 를 가짜로 바꿔서 순서와 "DB 에서 무엇을 읽어 AI 에게 주는지" 를 확인합니다.
class ChecklistServiceTest {

  private static final Long USER_ID = 7L;
  // 2026-10-17 낮 12시(한국 시간) → 오늘은 10/17, 지난 4주는 9/19 ~ 10/16
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-17T03:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate FROM = LocalDate.parse("2026-09-19");
  private static final LocalDate TO = LocalDate.parse("2026-10-16");

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final StudyPlanRepository studyPlanRepository = mock(StudyPlanRepository.class);
  private final StudySessionRepository studySessionRepository = mock(StudySessionRepository.class);
  private final PsychTestRepository psychTestRepository = mock(PsychTestRepository.class);
  private final ThinkingTestRepository thinkingTestRepository = mock(ThinkingTestRepository.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final ChecklistService service =
      new ChecklistService(
          currentUserProvider,
          studyPlanRepository,
          studySessionRepository,
          psychTestRepository,
          thinkingTestRepository,
          aiClient,
          CLOCK);

  private static StudyPlan plan(long id, String date, String content, int minutes, boolean done) {
    return plan(id, date, content, minutes, done, PlanKind.STUDY);
  }

  private static StudyPlan plan(
      long id, String date, String content, int minutes, boolean done, PlanKind kind) {
    StudyPlan plan =
        new StudyPlan(
            USER_ID,
            kind == PlanKind.STUDY ? 3L : null,
            LocalDate.parse(date),
            content,
            minutes,
            null,
            kind);
    plan.changeDone(done);
    ReflectionTestUtils.setField(plan, "id", id);
    return plan;
  }

  // 그 계획으로 켠 타이머 기록 하나 (minutes 분 동안, 일시정지 pausedMinutes 분)
  private static StudySession session(long planId, int minutes, int pausedMinutes) {
    Instant start = Instant.parse("2026-10-14T10:00:00Z");
    return new StudySession(
        USER_ID,
        3L,
        start,
        start.plusSeconds(minutes * 60L),
        pausedMinutes > 0 ? 1 : 0,
        pausedMinutes * 60_000L,
        planId,
        null,
        null,
        PlanKind.STUDY);
  }

  // AI 가 받은 질문을 꺼냅니다.
  private AiQuestion askedQuestion() {
    ArgumentCaptor<AiQuestion> question = ArgumentCaptor.forClass(AiQuestion.class);
    verify(aiClient)
        .ask(
            eq(USER_ID),
            eq(GptUsageKind.CHECKLIST),
            question.capture(),
            eq(ChecklistPrompt.Answer.class));
    return question.getValue();
  }

  private void aiAnswers(String... items) {
    given(
            aiClient.ask(
                eq(USER_ID), eq(GptUsageKind.CHECKLIST), any(), eq(ChecklistPrompt.Answer.class)))
        .willReturn(Optional.of(new ChecklistPrompt.Answer(List.of(items))));
  }

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(
            studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
                USER_ID, FROM, TO))
        .willReturn(List.of());
    given(thinkingTestRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
        .willReturn(Optional.empty());
    given(psychTestRepository.findFirstByUserIdOrderByIdDesc(USER_ID)).willReturn(Optional.empty());
  }

  @Test
  @DisplayName("AI 가 준 항목을 검사해서 돌려준다")
  void suggests() {
    aiAnswers("42~45쪽 풀기", " 오답 표시 ", "채점하기");

    ChecklistResponse response = service.suggest(new ChecklistRequest("수학 문제집", null));

    assertThat(response.showHelp()).isFalse();
    assertThat(response.items()).containsExactly("42~45쪽 풀기", "오답 표시", "채점하기");
  }

  @Test
  @DisplayName("내 DB 의 최근 4주 같은 제목 공부 계획(최근 것부터 5개)과 그 순공을 AI 에게 주고, 그 밖의 것은 주지 않는다")
  void givesPastSameCardsFromDb() {
    List<StudyPlan> plans = new ArrayList<>();
    plans.add(plan(1, "2026-09-20", "수학 문제집", 60, true));
    plans.add(plan(2, "2026-10-08", "수학 문제집", 60, true));
    plans.add(plan(3, "2026-10-09", "수학 문제집", 60, false));
    plans.add(plan(4, "2026-10-10", "영어 단어", 30, true)); // 제목이 다름
    plans.add(plan(5, "2026-10-11", "수학 문제집", 60, true, PlanKind.PLAY)); // 노는 계획
    plans.add(plan(6, "2026-10-12", " 수학 문제집 ", 60, true)); // 앞뒤 공백은 같은 제목
    plans.add(plan(7, "2026-10-13", "수학 문제집", 90, false));
    plans.add(plan(8, "2026-10-14", "수학 문제집", 60, false));
    given(
            studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
                USER_ID, FROM, TO))
        .willReturn(plans);
    // 8번: 30분 + (50분 − 일시정지 5분) = 75분 / 6번: 62분 / 나머지는 타이머를 안 켬
    given(studySessionRepository.findByUserIdAndPlanIdIn(eq(USER_ID), anyCollection()))
        .willReturn(List.of(session(8, 30, 0), session(8, 50, 5), session(6, 62, 0)));
    aiAnswers("하나", "둘", "셋");

    service.suggest(new ChecklistRequest("수학 문제집", "6월 모의고사 수학 2등급"));

    // 이름·학교 같은 개인정보가 들어가지 않는지 보려고, 입력 글 전체가 정확히 이것인지 봅니다.
    assertThat(askedQuestion().input())
        .isEqualTo(
            """
            카드 제목: 수학 문제집
            목표: 6월 모의고사 수학 2등급
            개수: 3~6개
            지난 같은 카드 (최근 것부터):
            - 10/14 · 목표 60분 · 공부 75분 · 못 끝냄
            - 10/13 · 목표 90분 · 공부 0분 · 못 끝냄
            - 10/12 · 목표 60분 · 공부 62분 · 끝냄
            - 10/9 · 목표 60분 · 공부 0분 · 못 끝냄
            - 10/8 · 목표 60분 · 공부 0분 · 끝냄""");
  }

  @Test
  @DisplayName("심리검사 답이 모두 같아서 믿기 어려우면 계획 낙관이 7 이어도 계획 성향 줄을 주지 않는다")
  void unsurePsychTestIgnored() {
    Map<PsychItem, Integer> allSeven = new java.util.EnumMap<>(PsychItem.class);
    for (PsychItem item : PsychItem.values()) {
      allSeven.put(item, 7);
    }
    given(psychTestRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
        .willReturn(
            Optional.of(new PsychTest(USER_ID, Instant.parse("2026-10-16T00:00:00Z"), allSeven)));
    aiAnswers("하나", "둘", "셋");

    service.suggest(new ChecklistRequest("수학 문제집", null));

    assertThat(askedQuestion().input()).doesNotContain("계획 성향");
  }

  @Test
  @DisplayName("심리검사가 없으면 예전 생각 습관 테스트의 '계획할 때' 가 낙관일 때 계획 성향 줄을 준다")
  void planOptimismFromThinkingTest() {
    ThinkingTest test =
        new ThinkingTest(
            USER_ID,
            Instant.parse("2026-10-10T00:00:00Z"),
            2,
            Map.of(CognitiveBias.PLANNING_FALLACY, 6.0, CognitiveBias.FUTURE_SELF, 5.0),
            Set.of());
    given(thinkingTestRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
        .willReturn(Optional.of(test));
    aiAnswers("하나", "둘", "셋");

    service.suggest(new ChecklistRequest("수학 문제집", null));

    assertThat(askedQuestion().input()).contains("계획 성향: 계획을 크게 잡는 편");
  }

  // 계획 낙관(P1 ~ P5)만 answer, 나머지는 척도마다 다른 답(1 ~ 7)인 심리검사
  // (나머지를 모두 4 로 하면 35개가 다 같아져서 믿기 어려운 검사가 됩니다. PsychTest.isReliable)
  private void psychTestWithPlanOptimism(int answer) {
    Map<PsychItem, Integer> answers = new java.util.EnumMap<>(PsychItem.class);
    for (PsychItem item : PsychItem.values()) {
      answers.put(
          item, item.scale() == PsychScale.PLAN_OPTIMISM ? answer : item.scale().ordinal() + 1);
    }
    given(psychTestRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
        .willReturn(
            Optional.of(new PsychTest(USER_ID, Instant.parse("2026-10-16T00:00:00Z"), answers)));
  }

  @Test
  @DisplayName("심리검사의 계획 낙관이 높음(5 이상)이면 계획 성향 줄을 준다")
  void planOptimismFromPsychTest() {
    psychTestWithPlanOptimism(5);
    aiAnswers("하나", "둘", "셋");

    service.suggest(new ChecklistRequest("수학 문제집", null));

    assertThat(askedQuestion().input()).contains("계획 성향: 계획을 크게 잡는 편");
  }

  @Test
  @DisplayName("심리검사가 있으면 그것만 본다: 계획 낙관이 보통이면 생각 습관 테스트가 낙관이어도 줄이 없다")
  void psychTestWinsOverThinkingTest() {
    psychTestWithPlanOptimism(4);
    given(thinkingTestRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
        .willReturn(
            Optional.of(
                new ThinkingTest(
                    USER_ID,
                    Instant.parse("2026-10-10T00:00:00Z"),
                    2,
                    Map.of(CognitiveBias.PLANNING_FALLACY, 6.0, CognitiveBias.FUTURE_SELF, 5.0),
                    Set.of())));
    aiAnswers("하나", "둘", "셋");

    service.suggest(new ChecklistRequest("수학 문제집", null));

    assertThat(askedQuestion().input()).doesNotContain("계획 성향");
  }

  @Test
  @DisplayName("테스트를 안 했거나 '계획할 때' 가 낙관이 아니면 계획 성향 줄이 없다")
  void noPlanOptimism() {
    aiAnswers("하나", "둘", "셋");
    service.suggest(new ChecklistRequest("수학 문제집", null));
    assertThat(askedQuestion().input()).doesNotContain("계획 성향");
  }

  @Test
  @DisplayName("AI 가 못 만들면(꺼짐·한도·실패) 에러가 아니라 items: null 이다")
  void aiUnavailableIsNull() {
    given(aiClient.ask(any(), any(), any(), eq(ChecklistPrompt.Answer.class)))
        .willReturn(Optional.empty());

    ChecklistResponse response = service.suggest(new ChecklistRequest("수학 문제집", null));

    assertThat(response.showHelp()).isFalse();
    assertThat(response.items()).isNull();
  }

  @Test
  @DisplayName("쓸 만한 항목이 3개보다 적으면 items: null 이다")
  void tooFewItemsIsNull() {
    aiAnswers("42~45쪽 풀기", "");

    assertThat(service.suggest(new ChecklistRequest("수학 문제집", null)).items()).isNull();
  }

  @Test
  @DisplayName("제목이나 목표에 위기 표현이 있으면 AI 와 DB 를 부르지 않고 showHelp 를 돌려준다")
  void crisisShowsHelp() {
    ChecklistResponse byTitle = service.suggest(new ChecklistRequest("그냥 죽고 싶다", null));
    ChecklistResponse byGoal = service.suggest(new ChecklistRequest("수학 문제집", "사라지고 싶어"));

    assertThat(byTitle).isEqualTo(ChecklistResponse.help());
    assertThat(byGoal).isEqualTo(ChecklistResponse.help());
    verifyNoInteractions(aiClient, studySessionRepository, thinkingTestRepository);
    verify(studyPlanRepository, never())
        .findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(any(), any(), any());
  }
}
