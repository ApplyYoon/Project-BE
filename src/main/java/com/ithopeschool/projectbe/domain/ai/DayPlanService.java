package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.DayPlanRequest;
import com.ithopeschool.projectbe.domain.ai.dto.DayPlanResponse;
import com.ithopeschool.projectbe.domain.exam.Exam;
import com.ithopeschool.projectbe.domain.exam.ExamRepository;
import com.ithopeschool.projectbe.domain.exam.ExamScore;
import com.ithopeschool.projectbe.domain.exam.ExamScoreRepository;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswer;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswerRepository;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.psychtest.PsychScale;
import com.ithopeschool.projectbe.domain.psychtest.PsychScore;
import com.ithopeschool.projectbe.domain.psychtest.PsychTest;
import com.ithopeschool.projectbe.domain.psychtest.PsychTestRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.session.StudyTime;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// AI 하루 계획의 로직입니다. 내 기록을 근거로 하루 계획 초안을 짭니다. (2026-10-17 "AI 일일계획 · 내 데이터 기반 · 근거 바탕으로")
// ⚠️ 아무것도 저장하지 않습니다. 학생이 고친 뒤 FE 가 /study-plans/batch 로 저장합니다.
//
// 순서 (숫자는 코드가, 말은 AI 가)
//   1. 날짜가 오늘부터 14일 안인지 봅니다. 아니면 400.
//   2. 서버 DB 에서 내 기록을 모읍니다. 최근 14일 = 어제까지 14일
//      - 공부 기록(study_sessions): 한 번 길이 · 날마다의 순공 · 과목별 순공
//      - 공부 계획(study_plans): 완료율 · 과목별 최근 제목 / 그날 이미 세운 계획 시간
//      - 온보딩 설문: 한 번에 집중 가능한 시간 · 평일 하루 공부 가능 시간
//      - 심리검사: 주의 분산 · 계획 낙관이 높은지
//      - 과목 · 시험(그날부터 14일 안의 시험과 그 과목)
//   3. 한 칸 길이 · 하루 총량 · 칸들 · 과목 순서를 코드가 정하고 근거 문장을 만듭니다. (DayPlanRule)
//   4. AI 가 칸마다 할 일을 씁니다. 꺼짐 · 하루 한도 · 실패면 기본 할 일(그 과목의 최근 계획 제목)을 씁니다. (DayPlanPrompt)
//      그래서 AI 가 없어도 계획 초안은 늘 나갑니다.
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 내 기록만 읽습니다.
// AI 를 기다리는 동안 DB 연결을 붙잡지 않으려고 @Transactional 을 붙이지 않았습니다.
@Service
@RequiredArgsConstructor
public class DayPlanService {

  // 기록을 보는 기간 (어제까지 14일)
  static final int LOOKBACK_DAYS = 14;
  // 계획을 짤 수 있는 날: 오늘부터 이 날 수 뒤까지
  static final int MAX_DAYS_AHEAD = 14;
  // AI 에게 보여 주는 과목별 최근 계획 제목 수
  static final int RECENT_TITLES = 3;

  private final CurrentUserProvider currentUserProvider;
  private final StudySessionRepository studySessionRepository;
  private final StudyPlanRepository studyPlanRepository;
  private final OnboardingAnswerRepository onboardingAnswerRepository;
  private final PsychTestRepository psychTestRepository;
  private final SubjectRepository subjectRepository;
  private final ExamRepository examRepository;
  private final ExamScoreRepository examScoreRepository;
  private final AiClient aiClient;
  private final Clock clock;

  public DayPlanResponse suggest(DayPlanRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    LocalDate today = DayRule.dateOf(clock.instant());
    LocalDate date = request.date();

    // 1) 날짜
    if (date.isBefore(today) || date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "오늘부터 " + MAX_DAYS_AHEAD + "일 안의 날짜를 보내 주세요");
    }

    // 2) 내 기록
    LocalDate from = today.minusDays(LOOKBACK_DAYS);
    List<Integer> sessionMinutes = new ArrayList<>();
    Map<LocalDate, Long> dailyMillis = new HashMap<>();
    Map<Long, Long> subjectMillis = new HashMap<>();
    for (StudySession session :
        studySessionRepository.findStartedBetween(
            userId, DayRule.startOf(from), DayRule.startOf(today))) {
      if (session.getKind() != PlanKind.STUDY) {
        continue;
      }
      long millis =
          StudyTime.studyMillis(
              session.getStartedAt(), session.getEndedAt(), session.getPausedMs());
      sessionMinutes.add(StudyTime.toMinutes(millis));
      dailyMillis.merge(DayRule.dateOf(session.getStartedAt()), millis, Long::sum);
      if (session.getSubjectId() != null) {
        subjectMillis.merge(session.getSubjectId(), millis, Long::sum);
      }
    }
    List<Integer> dailyMinutes = new ArrayList<>();
    for (long millis : dailyMillis.values()) {
      dailyMinutes.add(StudyTime.toMinutes(millis));
    }

    List<StudyPlan> recentPlans =
        studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
            userId, from, today.minusDays(1));
    Integer planRate = planRateOf(recentPlans);
    Map<Long, List<String>> titlesBySubject = recentTitlesOf(recentPlans);

    Optional<OnboardingAnswer> onboarding =
        onboardingAnswerRepository.findFirstByUserIdOrderByAnsweredAtDescIdDesc(userId);
    Integer selfFocus = onboarding.map(OnboardingAnswer::getMaxFocusMinutes).orElse(null);
    Integer selfDaily =
        onboarding.map(OnboardingAnswer::getDailyAvailableMinutes).filter(m -> m > 0).orElse(null);

    Optional<PsychTest> psychTest = psychTestRepository.findFirstByUserIdOrderByIdDesc(userId);
    boolean distractionHigh = isHigh(psychTest, PsychScale.DISTRACTION);
    boolean planOptimismHigh = isHigh(psychTest, PsychScale.PLAN_OPTIMISM);

    int alreadyPlanned = 0;
    for (StudyPlan plan : studyPlanRepository.findByUserIdAndPlanDateOrderById(userId, date)) {
      if (plan.getKind() == PlanKind.STUDY) {
        alreadyPlanned += plan.getTargetMinutes();
      }
    }

    // 3) 숫자 정하기
    DayPlanRule.Choice block =
        DayPlanRule.blockMinutesOf(sessionMinutes, selfFocus, distractionHigh);
    DayPlanRule.Total total =
        DayPlanRule.totalMinutesOf(
            dailyMinutes, selfDaily, planRate, planOptimismHigh, alreadyPlanned);
    List<String> evidence = new ArrayList<>();
    evidence.add(block.evidence());
    evidence.addAll(total.evidence());

    List<Subject> subjects = subjectRepository.findByUserIdOrderById(userId);
    if (subjects.isEmpty()) {
      evidence.add("과목이 아직 없어서 계획을 짤 수 없어요. 과목을 먼저 만들어 주세요");
      return new DayPlanResponse(date, block.minutes(), total.minutes(), List.of(), evidence);
    }
    List<Integer> blockMinutes = DayPlanRule.blocksOf(total.minutes(), block.minutes());
    if (blockMinutes.isEmpty()) {
      evidence.add("그날은 이미 충분히 계획했어요");
      return new DayPlanResponse(date, block.minutes(), total.minutes(), List.of(), evidence);
    }
    int count = blockMinutes.size();

    Map<Long, Integer> examDays = examDaysBySubject(userId, date);
    List<DayPlanRule.SubjectInfo> infos = new ArrayList<>();
    for (Subject subject : subjects) {
      infos.add(
          new DayPlanRule.SubjectInfo(
              subject.getId(),
              subject.getName(),
              examDays.get(subject.getId()),
              StudyTime.toMinutes(subjectMillis.getOrDefault(subject.getId(), 0L))));
    }
    List<DayPlanRule.SubjectInfo> ordered = DayPlanRule.subjectOrder(infos);
    List<DayPlanRule.SubjectInfo> rotation = DayPlanRule.subjectRotation(ordered, count);
    evidence.addAll(DayPlanRule.subjectEvidence(ordered));

    // 4) 칸마다 할 일 (AI, 못 쓰면 기본 할 일)
    List<DayPlanPrompt.Block> blocks = new ArrayList<>();
    List<String> fallbacks = new ArrayList<>();
    Map<String, List<String>> titlesByName = new LinkedHashMap<>();
    for (int i = 0; i < rotation.size(); i++) {
      DayPlanRule.SubjectInfo subject = rotation.get(i);
      blocks.add(
          new DayPlanPrompt.Block(subject.name(), blockMinutes.get(i), subject.examDaysLeft()));
      List<String> titles = titlesBySubject.getOrDefault(subject.id(), List.of());
      fallbacks.add(titles.isEmpty() ? subject.name() + " 공부" : titles.get(0));
      titlesByName.put(subject.name(), titles);
    }
    Optional<DayPlanPrompt.Answer> answer =
        aiClient.ask(
            userId,
            GptUsageKind.ASSIST,
            DayPlanPrompt.question(date, blocks, titlesByName),
            DayPlanPrompt.Answer.class);
    List<String> contents = DayPlanPrompt.contentsOf(answer.orElse(null), fallbacks);

    List<DayPlanResponse.Plan> plans = new ArrayList<>();
    for (int i = 0; i < rotation.size(); i++) {
      plans.add(
          new DayPlanResponse.Plan(
              date,
              String.valueOf(rotation.get(i).id()),
              contents.get(i),
              blockMinutes.get(i),
              null,
              PlanKind.STUDY.toValue()));
    }
    return new DayPlanResponse(date, block.minutes(), total.minutes(), plans, evidence);
  }

  // 최근 공부 계획의 완료율(%). 공부 계획이 없으면 null
  private static Integer planRateOf(List<StudyPlan> plans) {
    int count = 0;
    int done = 0;
    for (StudyPlan plan : plans) {
      if (plan.getKind() == PlanKind.STUDY) {
        count++;
        if (plan.isDone()) {
          done++;
        }
      }
    }
    return count == 0 ? null : (int) Math.round(done * 100.0 / count);
  }

  // 과목 번호 → 최근 계획 제목들 (최근 것부터, 겹치지 않게 3개까지)
  private static Map<Long, List<String>> recentTitlesOf(List<StudyPlan> plans) {
    Map<Long, List<String>> titles = new HashMap<>();
    // 날짜 순서로 왔으니 뒤에서부터 보면 최근 것부터입니다.
    for (int i = plans.size() - 1; i >= 0; i--) {
      StudyPlan plan = plans.get(i);
      if (plan.getKind() != PlanKind.STUDY || plan.getSubjectId() == null) {
        continue;
      }
      List<String> list = titles.computeIfAbsent(plan.getSubjectId(), id -> new ArrayList<>());
      String title = plan.getContent().trim();
      if (list.size() < RECENT_TITLES && !list.contains(title)) {
        list.add(title);
      }
    }
    return titles;
  }

  // 과목 번호 → 그날부터 14일 안에 있는 가장 가까운 시험까지 남은 날
  private Map<Long, Integer> examDaysBySubject(Long userId, LocalDate date) {
    Map<Long, Integer> daysByExam = new HashMap<>();
    for (Exam exam : examRepository.findByUserIdOrderByExamDateDescIdDesc(userId)) {
      long days = ChronoUnit.DAYS.between(date, exam.getExamDate());
      if (days >= 0 && days <= DayPlanRule.EXAM_SOON_DAYS) {
        daysByExam.put(exam.getId(), (int) days);
      }
    }
    Map<Long, Integer> daysBySubject = new HashMap<>();
    if (daysByExam.isEmpty()) {
      return daysBySubject;
    }
    for (ExamScore score :
        examScoreRepository.findByUserIdAndExamIdInOrderById(userId, daysByExam.keySet())) {
      daysBySubject.merge(score.getSubjectId(), daysByExam.get(score.getExamId()), Math::min);
    }
    return daysBySubject;
  }

  // 심리검사에서 그 척도가 높은지. 검사가 없으면 false
  private static boolean isHigh(Optional<PsychTest> test, PsychScale scale) {
    return test.map(
            t ->
                PsychScore.levelOf(PsychScore.scoresOf(t.getAnswers()).get(scale))
                    == PsychScore.Level.HIGH)
        .orElse(false);
  }
}
