package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.ai.dto.DayPlanRequest;
import com.ithopeschool.projectbe.domain.ai.dto.DayPlanResponse;
import com.ithopeschool.projectbe.domain.exam.Exam;
import com.ithopeschool.projectbe.domain.exam.ExamRepository;
import com.ithopeschool.projectbe.domain.exam.ExamScore;
import com.ithopeschool.projectbe.domain.exam.ExamScoreRepository;
import com.ithopeschool.projectbe.domain.exam.ExamType;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswerRepository;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.psychtest.PsychTestRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectColor;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

// Service 단위 테스트: DB 와 AI(AiClient)를 가짜로 바꿔서 "내 기록으로 숫자를 정하고 AI 에게 무엇을 주는지" 를 확인합니다.
class DayPlanServiceTest {

  private static final Long USER_ID = 7L;
  // 2026-10-17 낮 12시(한국 시간) → 오늘은 10/17, 기록은 10/3 ~ 10/16
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-17T03:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate TODAY = LocalDate.parse("2026-10-17");
  private static final LocalDate TOMORROW = TODAY.plusDays(1);

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final StudySessionRepository sessions = mock(StudySessionRepository.class);
  private final StudyPlanRepository plans = mock(StudyPlanRepository.class);
  private final OnboardingAnswerRepository onboarding = mock(OnboardingAnswerRepository.class);
  private final PsychTestRepository psychTests = mock(PsychTestRepository.class);
  private final SubjectRepository subjects = mock(SubjectRepository.class);
  private final ExamRepository exams = mock(ExamRepository.class);
  private final ExamScoreRepository examScores = mock(ExamScoreRepository.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final DayPlanService service =
      new DayPlanService(
          currentUserProvider,
          sessions,
          plans,
          onboarding,
          psychTests,
          subjects,
          exams,
          examScores,
          aiClient,
          CLOCK);

  private static Subject subject(long id, String name) {
    Subject subject = new Subject(USER_ID, name, SubjectColor.RED);
    ReflectionTestUtils.setField(subject, "id", id);
    return subject;
  }

  // 그날 한낮(한국 시간)에 시작한 공부 기록 하나 (minutes 분)
  private static StudySession session(String date, long subjectId, int minutes) {
    Instant start =
        LocalDate.parse(date).atTime(12, 0).atZone(java.time.ZoneId.of("Asia/Seoul")).toInstant();
    return new StudySession(USER_ID, subjectId, start, start.plusSeconds(minutes * 60L), 0);
  }

  private static StudyPlan plan(String date, long subjectId, String content, boolean done) {
    StudyPlan plan = new StudyPlan(USER_ID, subjectId, LocalDate.parse(date), content, 30);
    plan.changeDone(done);
    return plan;
  }

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(sessions.findStartedBetween(eq(USER_ID), any(), any())).willReturn(List.of());
    given(plans.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(eq(USER_ID), any(), any()))
        .willReturn(List.of());
    given(plans.findByUserIdAndPlanDateOrderById(eq(USER_ID), any())).willReturn(List.of());
    given(onboarding.findFirstByUserIdOrderByAnsweredAtDescIdDesc(USER_ID))
        .willReturn(Optional.empty());
    given(psychTests.findFirstByUserIdOrderByIdDesc(USER_ID)).willReturn(Optional.empty());
    given(subjects.findByUserIdOrderById(USER_ID))
        .willReturn(List.of(subject(1, "수학"), subject(2, "영어")));
    given(exams.findByUserIdOrderByExamDateDescIdDesc(USER_ID)).willReturn(List.of());
    given(aiClient.ask(any(), any(), any(), eq(DayPlanPrompt.Answer.class)))
        .willReturn(Optional.empty());
  }

  @Test
  @DisplayName("짧게 집중하는 기록이면 짧은 칸으로 나누고, 시험 과목을 먼저 · 더 많이 넣고, 근거 문장을 붙인다")
  void dataBasedPlan() {
    // 최근 공부 기록 6번, 한 번에 20 ~ 28분 (가운데값 (22 + 25) / 2 = 24 → 25분 칸) · 공부한 날 3일 (하루 평균 47분)
    List<StudySession> recent = new ArrayList<>();
    recent.add(session("2026-10-14", 1, 20));
    recent.add(session("2026-10-14", 1, 25));
    recent.add(session("2026-10-15", 1, 22));
    recent.add(session("2026-10-15", 2, 21));
    recent.add(session("2026-10-16", 1, 25));
    recent.add(session("2026-10-16", 1, 28));
    given(sessions.findStartedBetween(eq(USER_ID), any(), any())).willReturn(recent);
    given(plans.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(eq(USER_ID), any(), any()))
        .willReturn(
            List.of(plan("2026-10-15", 1, "문제집 3단원", true), plan("2026-10-16", 1, "오답 정리", true)));
    // 10/20 시험에 수학이 있음 (내일부터 2일 뒤)
    Exam exam = new Exam(USER_ID, "중간고사", ExamType.SCHOOL, LocalDate.parse("2026-10-20"));
    ReflectionTestUtils.setField(exam, "id", 9L);
    given(exams.findByUserIdOrderByExamDateDescIdDesc(USER_ID)).willReturn(List.of(exam));
    given(examScores.findByUserIdAndExamIdInOrderById(eq(USER_ID), anyCollection()))
        .willReturn(List.of(new ExamScore(USER_ID, 9L, 1L, 80)));

    DayPlanResponse response = service.suggest(new DayPlanRequest(TOMORROW));

    // 하루 평균 (45 + 43 + 53) / 3 = 47 → × 1.1 = 51.7 → 50분 → 25분 칸 2개
    assertThat(response.blockMinutes()).isEqualTo(25);
    assertThat(response.totalMinutes()).isEqualTo(50);
    assertThat(response.plans()).hasSize(2);
    assertThat(response.plans())
        .extracting(DayPlanResponse.Plan::subjectId)
        .containsExactly("1", "1");
    // AI 가 꺼졌으면 그 과목의 가장 최근 계획 제목
    assertThat(response.plans())
        .extracting(DayPlanResponse.Plan::content)
        .containsExactly("오답 정리", "오답 정리");
    assertThat(response.plans().get(0))
        .isEqualTo(new DayPlanResponse.Plan(TOMORROW, "1", "오답 정리", 25, null, "study"));
    assertThat(response.evidence())
        .containsExactly(
            "최근 14일 공부 기록 6번의 한 번 길이 가운데값이 24분이라 25분씩 나눴어요",
            "최근 14일 공부한 3일의 평균이 47분이라 조금 더 잡았어요",
            "2일 뒤 시험이 있는 수학을(를) 먼저 · 더 많이 넣었어요",
            "최근 14일 가장 덜 한 영어(21분)을(를) 넣었어요");
  }

  @Test
  @DisplayName("AI 가 쓴 할 일을 칸마다 쓰고, AI 에게는 과목 · 시간 · 시험 · 최근 제목만 준다")
  void aiContents() {
    given(aiClient.ask(eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(DayPlanPrompt.Answer.class)))
        .willReturn(Optional.of(new DayPlanPrompt.Answer(List.of("기출 1회분", "단어 30개"))));
    // 설문: 한 번에 50분 · 하루 100분 → 50분 칸 2개 (수학 · 영어)
    com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswer answer =
        mock(com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswer.class);
    given(answer.getMaxFocusMinutes()).willReturn(50);
    given(answer.getDailyAvailableMinutes()).willReturn(100);
    given(onboarding.findFirstByUserIdOrderByAnsweredAtDescIdDesc(USER_ID))
        .willReturn(Optional.of(answer));

    DayPlanResponse response = service.suggest(new DayPlanRequest(TOMORROW));

    assertThat(response.plans())
        .extracting(DayPlanResponse.Plan::content)
        .containsExactly("기출 1회분", "단어 30개");
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
            날짜: 10/18
            칸 (이 순서대로 하나씩):
            1. 수학 · 50분
            2. 영어 · 50분
            최근 계획:
            - 수학: (없음)
            - 영어: (없음)""");
  }

  @Test
  @DisplayName("과목이 없거나 그날 이미 충분히 계획했으면 빈 계획과 이유를 돌려주고 AI 를 부르지 않는다")
  void nothingToPlan() {
    given(subjects.findByUserIdOrderById(USER_ID)).willReturn(List.of());
    DayPlanResponse noSubjects = service.suggest(new DayPlanRequest(TOMORROW));
    assertThat(noSubjects.plans()).isEmpty();
    assertThat(noSubjects.evidence()).last().isEqualTo("과목이 아직 없어서 계획을 짤 수 없어요. 과목을 먼저 만들어 주세요");

    given(subjects.findByUserIdOrderById(USER_ID)).willReturn(List.of(subject(1, "수학")));
    given(plans.findByUserIdAndPlanDateOrderById(USER_ID, TOMORROW))
        .willReturn(
            List.of(new StudyPlan(USER_ID, 1L, TOMORROW, "이미 세운 계획", 200, null, PlanKind.STUDY)));
    DayPlanResponse full = service.suggest(new DayPlanRequest(TOMORROW));
    assertThat(full.plans()).isEmpty();
    assertThat(full.evidence()).last().isEqualTo("그날은 이미 충분히 계획했어요");

    verifyNoInteractions(aiClient);
  }

  @Test
  @DisplayName("하루 총량이 한 칸보다 짧으면 '이미 충분히' 가 아니라 짧은 칸 하나를 넣는다")
  void shortDay() {
    // 공부한 3일의 평균 22분 → × 1.1 → 20분 → 최소 30분 · 기록이 적어서 한 칸 50분
    given(sessions.findStartedBetween(eq(USER_ID), any(), any()))
        .willReturn(
            List.of(
                session("2026-10-14", 1, 20),
                session("2026-10-15", 1, 25),
                session("2026-10-16", 1, 22)));

    DayPlanResponse response = service.suggest(new DayPlanRequest(TOMORROW));

    assertThat(response.blockMinutes()).isEqualTo(50);
    assertThat(response.totalMinutes()).isEqualTo(30);
    assertThat(response.plans())
        .extracting(DayPlanResponse.Plan::targetMinutes)
        .containsExactly(30);
    assertThat(response.evidence()).doesNotContain("그날은 이미 충분히 계획했어요");
  }

  @Test
  @DisplayName("날짜가 오늘보다 앞이거나 14일보다 뒤면 400 이다")
  void dateRange() {
    assertThatThrownBy(() -> service.suggest(new DayPlanRequest(TODAY.minusDays(1))))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR))
        .hasMessage("오늘부터 14일 안의 날짜를 보내 주세요");
    assertThatThrownBy(() -> service.suggest(new DayPlanRequest(TODAY.plusDays(15))))
        .isInstanceOf(ApiException.class);
    assertThat(service.suggest(new DayPlanRequest(TODAY.plusDays(14))).date())
        .isEqualTo(TODAY.plusDays(14));
  }
}
