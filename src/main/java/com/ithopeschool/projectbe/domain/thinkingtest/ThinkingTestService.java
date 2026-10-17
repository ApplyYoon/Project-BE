package com.ithopeschool.projectbe.domain.thinkingtest;

import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.bias.Weakness;
import com.ithopeschool.projectbe.domain.call.CallRecord;
import com.ithopeschool.projectbe.domain.call.CallRecordRepository;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.session.StudyTime;
import com.ithopeschool.projectbe.domain.stats.StudyFacts;
import com.ithopeschool.projectbe.domain.thinkingtest.dto.ExamThinkingGapResponse;
import com.ithopeschool.projectbe.domain.thinkingtest.dto.ThinkingGapResponse;
import com.ithopeschool.projectbe.domain.thinkingtest.dto.ThinkingTestResponse;
import com.ithopeschool.projectbe.domain.thinkingtest.dto.ThinkingTestSaveRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 생각 습관 테스트 결과를 저장하고, "내 생각 vs 현실" 을 견주는 로직입니다. (이슈 #35, docs/API.md "생각 습관 테스트")
//
// 모든 메서드는 currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고,
// 내 번호로만 찾아서 다른 사람의 결과는 보이지 않습니다.
@Service
@RequiredArgsConstructor
public class ThinkingTestService {

  // "생각 vs 현실" 을 볼 기간(일)의 기본값과 범위
  public static final int DEFAULT_GAP_DAYS = 14;
  public static final int MIN_GAP_DAYS = 7;
  public static final int MAX_GAP_DAYS = 60;

  // 시험 날짜로 받을 수 있는 범위 (공부 기록 조회와 같은 범위)
  private static final LocalDate MIN_EXAM_DATE = LocalDate.of(1970, 1, 1);
  private static final LocalDate MAX_EXAM_DATE = LocalDate.of(9999, 12, 31);

  private final CurrentUserProvider currentUserProvider;
  private final ThinkingTestRepository thinkingTestRepository;
  private final CallRecordRepository callRecordRepository;
  private final StudySessionRepository studySessionRepository;
  private final StudyPlanRepository studyPlanRepository;
  private final Clock clock;

  // 테스트 결과 저장. 다시 하면 덮어쓰지 않고 새 줄을 더합니다. (처음과 지금을 견줄 수 있게)
  @Transactional
  public ThinkingTestResponse save(ThinkingTestSaveRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    Map<CognitiveBias, Double> scores = new EnumMap<>(CognitiveBias.class);
    for (ThinkingTestSaveRequest.Score score : request.scores()) {
      // 같은 습관이 두 번 오면, 8개를 보냈어도 빠진 습관이 생깁니다.
      if (scores.put(CognitiveBias.valueOf(score.habit()), score.average()) != null) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "습관 점수(scores)는 습관마다 하나씩 보내 주세요");
      }
    }
    Set<Weakness> weaknesses = EnumSet.noneOf(Weakness.class);
    if (request.weaknesses() != null) {
      for (String name : request.weaknesses()) {
        weaknesses.add(Weakness.valueOf(name));
      }
    }

    ThinkingTest saved =
        thinkingTestRepository.save(
            new ThinkingTest(userId, clock.instant(), request.testVersion(), scores, weaknesses));
    return ThinkingTestResponse.from(saved);
  }

  // 내 가장 최근 테스트 결과. 아직 안 했으면 404 입니다.
  @Transactional(readOnly = true)
  public ThinkingTestResponse getLatest() {
    Long userId = currentUserProvider.getCurrentUserId();
    return ThinkingTestResponse.from(findLatest(userId));
  }

  // 최근 days 일의 통화로 "내 생각 vs 현실" 을 견줍니다. 테스트를 안 했으면 404 입니다.
  // days 가 비었으면 14일, 7 ~ 60 이 아니면 400 입니다.
  @Transactional(readOnly = true)
  public ThinkingGapResponse getGap(Integer days) {
    Long userId = currentUserProvider.getCurrentUserId();
    int period = days == null ? DEFAULT_GAP_DAYS : days;
    if (period < MIN_GAP_DAYS || period > MAX_GAP_DAYS) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "days 는 " + MIN_GAP_DAYS + " ~ " + MAX_GAP_DAYS + " 이에요");
    }
    ThinkingTest test = findLatest(userId);
    LocalDate today = DayRule.dateOf(clock.instant());
    List<CallRecord> calls =
        callRecordRepository.findByUserIdAndCallDateGreaterThanEqualOrderByStartedAtDescIdDesc(
            userId, today.minusDays(period - 1));
    return ThinkingGapResponse.from(gapOf(test, calls, period));
  }

  // 시험 전 2주(시험 14일 전 ~ 하루 전)의 기록으로 "내 생각 vs 현실" 을 견줍니다. 시험 결과 화면에 씁니다. (ExamThinkingGap)
  // 시험 번호가 아니라 시험 날짜를 받습니다. FE 의 시험은 아직 기기에만 저장돼서 서버의 시험 번호가 없기 때문입니다.
  // 테스트를 안 했으면 404 입니다.
  @Transactional(readOnly = true)
  public ExamThinkingGapResponse getExamGap(LocalDate examDate) {
    Long userId = currentUserProvider.getCurrentUserId();
    if (examDate.isBefore(MIN_EXAM_DATE) || examDate.isAfter(MAX_EXAM_DATE)) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "examDate 는 1970-01-01 부터 9999-12-31 사이로 보내 주세요");
    }
    ThinkingTest test = findLatest(userId);
    LocalDate from = ExamThinkingGap.fromDateOf(examDate);
    LocalDate to = ExamThinkingGap.toDateOf(examDate);

    List<ExamThinkingGap.ReasonFact> facts = new ArrayList<>();
    List<StudyFacts.FactSession> sessions = new ArrayList<>();
    // 기간 안에 "시작한" 기록들. 노는 기록은 공부가 아니라서 뺍니다.
    for (StudySession session :
        studySessionRepository.findStartedBetween(
            userId, DayRule.startOf(from), DayRule.startOf(to.plusDays(1)))) {
      if (session.getKind() != PlanKind.STUDY) {
        continue;
      }
      // 목표 전에 멈추며 고른 이유. 이유를 안 골랐으면 비어 있습니다.
      if (session.getStopReason() != null) {
        facts.add(
            new ExamThinkingGap.ReasonFact(
                DayRule.dateOf(session.getStartedAt()), session.getStopReason()));
      }
      long studyMillis =
          StudyTime.studyMillis(
              session.getStartedAt(), session.getEndedAt(), session.getPausedMs());
      int targetMinutes = session.getTargetMinutes() == null ? 0 : session.getTargetMinutes();
      sessions.add(
          new StudyFacts.FactSession(
              session.getPlanId(), session.getStartedAt(), studyMillis, targetMinutes));
    }
    // 통화의 이유는 "어제" 계획을 못 채운 이유라서 하루 전 날짜로 셉니다.
    // 그래서 기간 첫날의 다음 날부터 시험 날의 통화까지 봅니다. (시험 날 통화의 이유 = 시험 전날의 이유)
    for (CallRecord call :
        callRecordRepository.findByUserIdAndCallDateBetweenOrderByStartedAtAscIdAsc(
            userId, from.plusDays(1), examDate)) {
      // 마무리했고 위기 표현이 없던 통화만 씁니다. (gapOf 와 같은 기준)
      if (call.isFinished() && !call.isCrisisDetected() && call.getMissReason() != null) {
        facts.add(
            new ExamThinkingGap.ReasonFact(call.getCallDate().minusDays(1), call.getMissReason()));
      }
    }
    // 기간 안의 공부 계획들. 노는 계획은 뺍니다.
    List<StudyFacts.FactPlan> plans = new ArrayList<>();
    for (StudyPlan plan :
        studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
            userId, from, to)) {
      if (plan.getKind() == PlanKind.STUDY) {
        plans.add(
            new StudyFacts.FactPlan(
                plan.getId(), plan.getPlanDate(), plan.getTargetMinutes(), plan.getStartTime()));
      }
    }

    StudyFacts studyFacts = StudyFacts.of(plans, sessions, from, to);
    // 4개 축 중 첫 번째가 "계획할 때" 입니다. (StudyStyle.AXES 의 순서)
    StudyStyle.AxisResult planning = StudyStyle.stylesOf(test.getScores()).get(0);
    return ExamThinkingGapResponse.from(
        ExamThinkingGap.of(examDate, test.getWeaknesses(), planning, facts, studyFacts.planRate()));
  }

  // 테스트 결과와 통화들로 견줍니다. 마무리했고 위기 표현이 없던 통화만 씁니다. (CallService 도 같이 씁니다)
  public static ThinkingGap gapOf(ThinkingTest test, List<CallRecord> calls, int days) {
    List<ThinkingGap.CallFact> facts = new ArrayList<>();
    for (CallRecord call : calls) {
      if (call.isFinished() && !call.isCrisisDetected()) {
        facts.add(
            new ThinkingGap.CallFact(
                call.getCallDate(), call.getFoundBias(), call.getMissReason()));
      }
    }
    return ThinkingGap.of(
        StudyStyle.topHabitsOf(test.getScores()), test.getWeaknesses(), facts, days);
  }

  private ThinkingTest findLatest(Long userId) {
    return thinkingTestRepository
        .findFirstByUserIdOrderByIdDesc(userId)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "아직 생각 습관 테스트를 하지 않았어요"));
  }
}
