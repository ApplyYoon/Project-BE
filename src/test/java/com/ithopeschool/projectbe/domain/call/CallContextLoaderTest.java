package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.CardPriority;
import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.cardresult.CardResult;
import com.ithopeschool.projectbe.domain.cardresult.CardResultService;
import com.ithopeschool.projectbe.domain.cardresult.JudgedCardDay;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.checkin.CheckIn;
import com.ithopeschool.projectbe.domain.checkin.CheckInRepository;
import com.ithopeschool.projectbe.domain.checkin.CheckInSource;
import com.ithopeschool.projectbe.domain.checkin.EmotionTag;
import com.ithopeschool.projectbe.domain.dailysignal.DailySignal;
import com.ithopeschool.projectbe.domain.dailysignal.DailySignalRepository;
import com.ithopeschool.projectbe.domain.exam.Exam;
import com.ithopeschool.projectbe.domain.exam.ExamRepository;
import com.ithopeschool.projectbe.domain.exam.ExamScore;
import com.ithopeschool.projectbe.domain.exam.ExamScoreRepository;
import com.ithopeschool.projectbe.domain.exam.ExamType;
import com.ithopeschool.projectbe.domain.experiment.Experiment;
import com.ithopeschool.projectbe.domain.experiment.ExperimentRepository;
import com.ithopeschool.projectbe.domain.experiment.ExperimentStatus;
import com.ithopeschool.projectbe.domain.experiment.ExperimentStrategy;
import com.ithopeschool.projectbe.domain.goal.Goal;
import com.ithopeschool.projectbe.domain.goal.GoalKind;
import com.ithopeschool.projectbe.domain.goal.GoalRepository;
import com.ithopeschool.projectbe.domain.goal.WeekGoalService;
import com.ithopeschool.projectbe.domain.goal.dto.WeekGoalDto;
import com.ithopeschool.projectbe.domain.onboarding.Chronotype;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswer;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswerRepository;
import com.ithopeschool.projectbe.domain.session.SessionTags.ShortfallReason;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectColor;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// DB 를 가짜(mock)로 두고, 어떤 기록을 골라 꺼내는지만 확인합니다. 문장 모양은 CallContextTest 가 확인합니다.
class CallContextLoaderTest {

  private static final Long USER_ID = 7L;
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 18);

  private final ExamRepository examRepository = mock(ExamRepository.class);
  private final ExamScoreRepository examScoreRepository = mock(ExamScoreRepository.class);
  private final SubjectRepository subjectRepository = mock(SubjectRepository.class);
  private final GoalRepository goalRepository = mock(GoalRepository.class);
  private final OnboardingAnswerRepository onboardingAnswerRepository =
      mock(OnboardingAnswerRepository.class);
  private final CardRepository cardRepository = mock(CardRepository.class);
  private final CardResultService cardResultService = mock(CardResultService.class);
  private final StudySessionRepository studySessionRepository = mock(StudySessionRepository.class);
  private final CheckInRepository checkInRepository = mock(CheckInRepository.class);
  private final DailySignalRepository dailySignalRepository = mock(DailySignalRepository.class);
  private final WeekGoalService weekGoalService = mock(WeekGoalService.class);
  private final ExperimentRepository experimentRepository = mock(ExperimentRepository.class);

  private final CallContextLoader loader =
      new CallContextLoader(
          examRepository,
          examScoreRepository,
          subjectRepository,
          goalRepository,
          onboardingAnswerRepository,
          cardRepository,
          cardResultService,
          studySessionRepository,
          checkInRepository,
          dailySignalRepository,
          weekGoalService,
          experimentRepository);

  private static Exam exam(long id, String name, LocalDate date) {
    Exam exam = new Exam(USER_ID, name, ExamType.MOCK, date);
    ReflectionTestUtils.setField(exam, "id", id);
    return exam;
  }

  private static ExamScore score(long examId, long subjectId, int expected, Integer actual) {
    ExamScore score = new ExamScore(USER_ID, examId, subjectId, expected);
    if (actual != null) {
      score.changeActualScore(actual);
    }
    return score;
  }

  private static Subject subject(long id, String name) {
    Subject subject = new Subject(USER_ID, name, SubjectColor.RED);
    ReflectionTestUtils.setField(subject, "id", id);
    return subject;
  }

  private static Goal goal(String title, LocalDate dDay, String obstacle) {
    return new Goal(
        USER_ID,
        GoalKind.SCORE,
        new Goal.Target(title, dDay, "5등급", null, null),
        new Goal.Woop("좋은 점", "치를 대가", obstacle, "씻기 전에 오답 1개"),
        Instant.parse("2026-09-21T00:00:00Z"));
  }

  @Test
  @DisplayName("기록이 하나도 없으면 비어 있는 값이라 대본에 칸이 생기지 않는다")
  void nothing() {
    CallContext.Facts facts = loader.load(USER_ID, TODAY);

    assertThat(CallContext.section(facts)).isEmpty();
  }

  @Test
  @DisplayName("다음 시험은 오늘 포함 가장 가까운 것, 지난 시험은 가장 최근 것에서 실제 점수를 적은 과목만 꺼낸다")
  void exams() {
    // 날짜가 늦은 것부터 (Repository 이름 그대로)
    given(examRepository.findByUserIdOrderByExamDateDescIdDesc(USER_ID))
        .willReturn(
            List.of(
                exam(4, "기말고사", LocalDate.of(2026, 12, 7)),
                exam(3, "11월 학평", LocalDate.of(2026, 11, 20)),
                exam(2, "9월 학평", LocalDate.of(2026, 9, 3)),
                exam(1, "6월 학평", LocalDate.of(2026, 6, 4))));
    given(subjectRepository.findByUserIdOrderById(USER_ID))
        .willReturn(List.of(subject(10, "국어"), subject(11, "수학")));
    given(examScoreRepository.findByUserIdAndExamIdOrderById(USER_ID, 2L))
        .willReturn(List.of(score(2, 10, 70, 66), score(2, 11, 55, null)));

    CallContext.Facts facts = loader.load(USER_ID, TODAY);

    assertThat(facts.nextExam()).isEqualTo(new CallContext.NextExam("11월 학평", 33));
    assertThat(facts.pastExam())
        .isEqualTo(
            new CallContext.PastExam("9월 학평", List.of(new CallContext.ScoreLine("국어", 70, 66))));
  }

  @Test
  @DisplayName("지난 시험에 실제 점수가 하나도 없으면 지난 시험을 넣지 않는다")
  void pastExamWithoutActualScores() {
    given(examRepository.findByUserIdOrderByExamDateDescIdDesc(USER_ID))
        .willReturn(List.of(exam(2, "9월 학평", LocalDate.of(2026, 9, 3))));
    given(examScoreRepository.findByUserIdAndExamIdOrderById(USER_ID, 2L))
        .willReturn(List.of(score(2, 10, 70, null)));

    assertThat(loader.load(USER_ID, TODAY).pastExam()).isNull();
  }

  @Test
  @DisplayName("목표는 진행 중이고 D-day 가 지나지 않은 것만, 가까운 순으로 2개까지 꺼낸다")
  void goals() {
    Goal done = goal("끝낸 목표", LocalDate.of(2026, 10, 30), null);
    done.markDone();
    given(goalRepository.findByUserIdOrderById(USER_ID))
        .willReturn(
            List.of(
                goal("기말 국어 2등급", LocalDate.of(2026, 12, 7), null),
                done,
                goal("지난 목표", LocalDate.of(2026, 10, 17), null),
                goal("11월 학평 수학 3등급", LocalDate.of(2026, 11, 20), "학원 다녀오면 피곤해요"),
                goal("영단어장 1회독", LocalDate.of(2026, 11, 7), "  ")));

    List<CallContext.GoalLine> goals = loader.load(USER_ID, TODAY).goals();

    assertThat(goals)
        .containsExactly(
            new CallContext.GoalLine("영단어장 1회독", 20, "지금 5등급", null, "씻기 전에 오답 1개"),
            new CallContext.GoalLine("11월 학평 수학 3등급", 33, "지금 5등급", "학원 다녀오면 피곤해요", "씻기 전에 오답 1개"));
  }

  @Test
  @DisplayName("완주형 목표의 지금 수준은 '전체 양' 으로 적는다")
  void completionGoal() {
    Goal words =
        new Goal(
            USER_ID,
            GoalKind.COMPLETION,
            new Goal.Target("기말 전 영단어장 1회독", LocalDate.of(2026, 11, 7), null, 300, "쪽"),
            new Goal.Woop("좋은 점", "치를 대가", "폰을 자꾸 봐요", "폰을 다른 방에"),
            Instant.parse("2026-09-21T00:00:00Z"));
    given(goalRepository.findByUserIdOrderById(USER_ID)).willReturn(List.of(words));

    assertThat(loader.load(USER_ID, TODAY).goals().get(0).level()).isEqualTo("전체 300쪽");
  }

  @Test
  @DisplayName("모든 조회는 넘겨받은 내 번호로만 한다")
  void onlyMine() {
    given(examRepository.findByUserIdOrderByExamDateDescIdDesc(anyLong())).willReturn(List.of());

    loader.load(USER_ID, TODAY);

    verify(examRepository).findByUserIdOrderByExamDateDescIdDesc(USER_ID);
    verify(goalRepository).findByUserIdOrderById(USER_ID);
    verify(checkInRepository).findWithEmotionsBetween(USER_ID, TODAY.minusDays(6), TODAY);
    verify(dailySignalRepository)
        .findByUserIdAndSignalDateBetweenOrderBySignalDateAsc(USER_ID, TODAY.minusDays(1), TODAY);
  }

  // ---- 아침형/저녁형 · 못 채운 이유 ----

  private static Card card(long id, LocalTime start) {
    Card card = mock(Card.class);
    given(card.getId()).willReturn(id);
    given(card.getStartTime()).willReturn(start);
    return card;
  }

  // checklistRate: 체크리스트 달성률. 미실행이면 null
  private static JudgedCardDay day(
      long cardId, CardResult result, MissReason reason, Double checklistRate) {
    return new JudgedCardDay(
        cardId,
        TODAY.minusDays(1),
        true,
        CardPriority.FLEXIBLE,
        result,
        0,
        reason,
        null,
        checklistRate);
  }

  // daysAgo 일 전 정오(UTC)에 시작한 10분짜리 공부 기록
  private static StudySession session(int daysAgo) {
    Instant start = TODAY.minusDays(daysAgo).atTime(12, 0).toInstant(ZoneOffset.UTC);
    return new StudySession(USER_ID, null, start, start.plusSeconds(600), 0);
  }

  private void givenSaid(Chronotype chronotype) {
    OnboardingAnswer answer = mock(OnboardingAnswer.class);
    given(answer.getChronotype()).willReturn(chronotype);
    given(onboardingAnswerRepository.findFirstByUserIdOrderByAnsweredAtDescIdDesc(USER_ID))
        .willReturn(Optional.of(answer));
  }

  @Test
  @DisplayName(
      "최근 2주(어제까지) 판정된 카드를 시작 시각으로 아침 · 저녁으로 나눠, 체크리스트까지 다 채운 날을 센다. 휴식한 날과 낮 카드는 빼고, 미실행 · 덜 채운 날은 '다 채운 날' 이 아니다")
  void rhythmGap() {
    givenSaid(Chronotype.NIGHT);
    given(cardResultService.getJudgedDays(USER_ID, TODAY.minusDays(21), TODAY.minusDays(1)))
        .willReturn(
            List.of(
                day(1, CardResult.DONE, null, 1.0),
                day(1, CardResult.LATE, null, 1.0),
                day(1, CardResult.ENDED_EARLY, null, 0.5),
                day(1, CardResult.REST, null, null),
                day(2, CardResult.FAILED, MissReason.TIRED, null),
                day(2, CardResult.DONE, null, 1.0),
                day(2, CardResult.DONE, null, 0.34),
                day(3, CardResult.UNHANDLED, null, null),
                day(4, CardResult.DONE, null, 1.0)));
    // 가짜 카드는 given(...) 밖에서 먼저 만듭니다. (given 안에서 다른 가짜를 만들면 Mockito 가 거부합니다)
    List<Card> cards =
        List.of(
            card(1, LocalTime.of(6, 40)),
            card(2, LocalTime.of(21, 40)),
            card(3, LocalTime.of(1, 0)),
            card(4, LocalTime.of(15, 0)));
    given(cardRepository.findByUserIdAndIdIn(any(), any())).willReturn(cards);

    assertThat(loader.load(USER_ID, TODAY).rhythmGap())
        .isEqualTo(new CallContext.RhythmGap(true, 2, 3, 1, 4));
  }

  @Test
  @DisplayName("설문에 아침형/저녁형 답이 없으면 비교하지 않는다")
  void noRhythmWithoutAnswer() {
    given(cardResultService.getJudgedDays(any(), any(), any()))
        .willReturn(List.of(day(1, CardResult.DONE, null, 1.0)));

    assertThat(loader.load(USER_ID, TODAY).rhythmGap()).isNull();
  }

  @Test
  @DisplayName("못 채운 이유는 미실행 카드의 원인과 타이머를 일찍 끝내며 고른 이유를 같은 글자로 합쳐 많은 순으로 센다 ('기타' 는 뺌)")
  void causes() {
    given(cardResultService.getJudgedDays(any(), any(), any()))
        .willReturn(
            List.of(
                day(1, CardResult.FAILED, MissReason.TIRED, null),
                day(1, CardResult.FAILED, MissReason.ETC, null),
                day(2, CardResult.POSTPONED, MissReason.PHONE, null)));
    StudySession tired = session(2);
    tired.recordEndReport(Map.of(), ShortfallReason.TIRED, null, null, null, Set.of());
    StudySession stuck = session(3);
    stuck.recordEndReport(Map.of(), ShortfallReason.STUCK, null, null, null, Set.of());
    given(studySessionRepository.findStartedBetween(any(), any(), any()))
        .willReturn(List.of(tired, stuck));

    assertThat(loader.load(USER_ID, TODAY).causes())
        .containsExactly(
            new CallContext.CauseCount("피곤·수면", 2),
            new CallContext.CauseCount("폰·SNS", 1),
            new CallContext.CauseCount("막힘", 1));
  }

  // ---- 약속 · 감정 · 실행률 · 수면 (최근 기록) ----

  // daysAgo 일 전 체크인. 약속과 감정 태그만 채웁니다.
  private static CheckIn checkIn(
      int daysAgo, String promiseIf, String promiseThen, EmotionTag... emotions) {
    CheckIn checkIn =
        new CheckIn(
            USER_ID,
            TODAY.minusDays(daysAgo),
            CheckInSource.QUICK,
            3,
            3,
            null,
            null,
            null,
            promiseIf,
            promiseThen,
            null);
    checkIn.replaceEmotions(Set.of(emotions));
    return checkIn;
  }

  // daysAgo 일 전 카드 하루치. runRate: 실행률 (실행 1, 미실행 0)
  private static JudgedCardDay rateDay(int daysAgo, CardResult result, double runRate) {
    return new JudgedCardDay(
        1L,
        TODAY.minusDays(daysAgo),
        false,
        CardPriority.FLEXIBLE,
        result,
        runRate,
        null,
        null,
        null);
  }

  @Test
  @DisplayName("약속은 오늘 체크인의 것이 먼저고, 없으면 어제 것을 쓴다. 한쪽만 적었으면 쓰지 않는다")
  void promise() {
    CheckIn yesterday = checkIn(1, "폰을 보고 싶으면", "물 한 잔 마신다");
    CheckIn today = checkIn(0, "졸리면", "5분 스트레칭한다");
    assertThat(CallContextLoader.promiseOf(List.of(yesterday, today), TODAY))
        .isEqualTo(new CallContext.Promise("졸리면", "5분 스트레칭한다", 0));

    CheckIn todayHalf = checkIn(0, "졸리면", null);
    assertThat(CallContextLoader.promiseOf(List.of(yesterday, todayHalf), TODAY))
        .isEqualTo(new CallContext.Promise("폰을 보고 싶으면", "물 한 잔 마신다", 1));

    // 이틀 전 약속은 쓰지 않습니다.
    assertThat(CallContextLoader.promiseOf(List.of(checkIn(2, "a", "b")), TODAY)).isNull();
  }

  @Test
  @DisplayName("약속 글에 위기 표현이 있으면 대본에 넣지 않는다")
  void promiseWithCrisisText() {
    CheckIn crisis = checkIn(0, "너무 힘들면", "죽고 싶다");

    assertThat(CallContextLoader.promiseOf(List.of(crisis), TODAY)).isNull();
  }

  @Test
  @DisplayName("감정 태그는 최근 체크인에서 고른 횟수를 세어 많은 것부터 돌려준다")
  void emotions() {
    given(checkInRepository.findWithEmotionsBetween(USER_ID, TODAY.minusDays(6), TODAY))
        .willReturn(
            List.of(
                checkIn(3, null, null, EmotionTag.TIRED, EmotionTag.ANXIOUS),
                checkIn(2, null, null, EmotionTag.TIRED),
                checkIn(1, null, null, EmotionTag.TIRED, EmotionTag.CALM)));

    assertThat(loader.load(USER_ID, TODAY).recent().emotions())
        .containsExactly(
            new CallContext.EmotionCount("피곤함", 3),
            new CallContext.EmotionCount("불안함", 1),
            new CallContext.EmotionCount("평온함", 1));
  }

  @Test
  @DisplayName("실행률은 최근 7일(어제까지)과 그 앞 2주로 나눠 평균을 내고, 휴식한 날은 뺀다")
  void runRate() {
    List<JudgedCardDay> days =
        List.of(
            rateDay(1, CardResult.DONE, 1),
            rateDay(7, CardResult.FAILED, 0),
            rateDay(5, CardResult.REST, 0),
            rateDay(8, CardResult.DONE, 1),
            rateDay(21, CardResult.DONE, 1));

    assertThat(CallContextLoader.runRateOf(days, TODAY))
        .isEqualTo(new CallContext.RunRate(2, 0.5, 2, 1.0));
    // 한쪽 기간에 카드가 없으면 비교하지 않습니다.
    assertThat(CallContextLoader.runRateOf(List.of(rateDay(1, CardResult.DONE, 1)), TODAY))
        .isNull();
  }

  @Test
  @DisplayName("수면은 오늘 날짜의 기록이 먼저고, 없으면 어제 것을 쓴다")
  void sleep() {
    given(
            dailySignalRepository.findByUserIdAndSignalDateBetweenOrderBySignalDateAsc(
                USER_ID, TODAY.minusDays(1), TODAY))
        .willReturn(
            List.of(
                new DailySignal(USER_ID, TODAY.minusDays(1), 420, null, null, null, null),
                new DailySignal(USER_ID, TODAY, 310, null, null, null, null)));
    assertThat(loader.load(USER_ID, TODAY).recent().sleep())
        .isEqualTo(new CallContext.Sleep(310, 0));

    given(
            dailySignalRepository.findByUserIdAndSignalDateBetweenOrderBySignalDateAsc(
                USER_ID, TODAY.minusDays(1), TODAY))
        .willReturn(
            List.of(
                new DailySignal(USER_ID, TODAY.minusDays(1), 300, null, null, null, null),
                new DailySignal(USER_ID, TODAY, null, null, null, 4000, null)));
    assertThat(loader.load(USER_ID, TODAY).recent().sleep())
        .isEqualTo(new CallContext.Sleep(300, 1));
  }

  // ---- 이번 주 목표 · 자기 실험 (지금 하고 있는 약속) ----

  private static Card titled(long id, String title) {
    Card card = mock(Card.class);
    given(card.getId()).willReturn(id);
    given(card.getTitle()).willReturn(title);
    return card;
  }

  private static Experiment experiment(
      ExperimentStrategy strategy, LocalDate start, Long... cardIds) {
    return new Experiment(USER_ID, "가설", null, strategy, Set.of(cardIds), start, 10, Instant.EPOCH);
  }

  @Test
  @DisplayName("이번 주 목표는 제목과 지금 숫자를 꺼내고, 시작 지연 목표는 분 · 나머지는 % 로 적는다")
  void weekGoal() {
    given(weekGoalService.thisWeekOf(USER_ID, TODAY))
        .willReturn(
            Optional.of(
                new WeekGoalDto.Item(
                    TODAY.minusDays(6),
                    "startDelay",
                    18,
                    23,
                    "시작 지연 18분 안으로",
                    "missed",
                    20,
                    40,
                    false)));

    assertThat(loader.load(USER_ID, TODAY).ongoing().weekGoal())
        .isEqualTo(new CallContext.WeekGoalLine("시작 지연 18분 안으로", 20, "분"));
  }

  @Test
  @DisplayName("자기 실험은 진행 중이고 14일이 안 끝난 것만, 늦게 시작한 것부터 붙인 카드 제목과 며칠째를 꺼낸다")
  void experiments() {
    given(experimentRepository.findByUserIdAndStatus(USER_ID, ExperimentStatus.RUNNING))
        .willReturn(
            List.of(
                experiment(ExperimentStrategy.PHONE_BAG, TODAY.minusDays(4), 1L),
                // 14일이 끝났지만 아직 판정 전 → 넣지 않음
                experiment(ExperimentStrategy.PLAN_SHORTER, TODAY.minusDays(20), 2L),
                experiment(ExperimentStrategy.START_COUNTDOWN, TODAY, 3L, 9L)));
    // 9번 카드는 지워져서 제목이 없습니다. (가짜 카드는 given 밖에서 먼저 만들어야 합니다)
    List<Card> cards = List.of(titled(1, "수학 문제집"), titled(3, "영어 단어"));
    given(cardRepository.findByUserIdAndIdIn(any(), any())).willReturn(cards);

    assertThat(loader.load(USER_ID, TODAY).ongoing().experiments())
        .containsExactly(
            new CallContext.ExperimentLine("5초 카운트다운으로 시작하기", List.of("영어 단어"), 1),
            new CallContext.ExperimentLine("시작할 때 폰을 가방에 넣기", List.of("수학 문제집"), 5));
  }
}
