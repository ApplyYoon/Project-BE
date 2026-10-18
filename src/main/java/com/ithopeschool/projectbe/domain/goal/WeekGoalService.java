package com.ithopeschool.projectbe.domain.goal;

import com.ithopeschool.projectbe.domain.checkin.CheckIn;
import com.ithopeschool.projectbe.domain.checkin.CheckInRepository;
import com.ithopeschool.projectbe.domain.exam.Exam;
import com.ithopeschool.projectbe.domain.exam.ExamRepository;
import com.ithopeschool.projectbe.domain.goal.dto.WeekGoalDto;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.safety.LowMoodStreak;
import com.ithopeschool.projectbe.domain.safety.SafetyBrake;
import com.ithopeschool.projectbe.domain.safety.SafetyService;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.session.StudyTime;
import com.ithopeschool.projectbe.domain.stats.StudyFacts;
import com.ithopeschool.projectbe.domain.stats.WeeklyFacts;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// "이번 주 목표" 로직입니다. 제안 · 저장 · 목록과 달성 여부 · 주가 끝난 뒤 판정. (이슈 #88)
// 제안과 판정은 AI 를 부르지 않고 WeeklyGoal 규칙과 기록(계획 · 타이머)으로만 합니다. "지켰어?" 를 묻지 않습니다.
//
// 정해 둔 규칙
//   1. 한 주는 월요일 ~ 일요일, 하루는 새벽 4시 기준입니다. (WeeklyFacts.mondayOf, DayRule)
//   2. 제안은 지난주(7일) 숫자로 만듭니다. 안전 브레이크(위기 표현 · 기분 저하 · 저장된 보호 조치)나 시험 기간이면 제안하지 않습니다.
//   3. 저장은 이번 주나 다음 주만 됩니다. 한 주에 하나이고, 다시 보내면 덮어씁니다. (지난 주는 바꿀 수 없음, 400)
//   4. 목록의 상태: 판정한 주는 적어 둔 결과를, 아직 판정 전인 주는 지금까지의 기록으로 본 값을 돌려줍니다.
//   5. 판정(judgeEndedWeeks)은 끝난 주의 판정 전 목표만 채웁니다. 이미 판정한 것은 건드리지 않아서, 두 번 돌아도 결과가 같습니다.
//
// 모든 주소는 currentUserProvider.getCurrentUserId() 로 얻은 내 번호로만 읽고 씁니다. 로그인하지 않으면 401 입니다.
@Service
@RequiredArgsConstructor
public class WeekGoalService {

  // 안전 브레이크를 볼 최근 기간 (오늘 포함, CallService 와 같음)
  static final int SAFETY_DAYS = 7;
  // 목록으로 한 번에 볼 수 있는 기간의 최대 주 수 (너무 긴 요청을 막는 한도)
  static final int MAX_WEEKS = 53;

  private final CurrentUserProvider currentUserProvider;
  private final WeekGoalRepository weekGoalRepository;
  private final StudyPlanRepository studyPlanRepository;
  private final StudySessionRepository studySessionRepository;
  private final CheckInRepository checkInRepository;
  private final ExamRepository examRepository;
  private final SafetyService safetyService;
  private final Clock clock;

  // 이번 주 목표 제안 (규칙 2)
  @Transactional(readOnly = true)
  public WeekGoalDto.Proposal propose() {
    Long userId = currentUserProvider.getCurrentUserId();
    Instant now = clock.instant();
    LocalDate thisMonday = WeeklyFacts.mondayOf(now);
    WeeklyGoal.WeekFacts lastWeek =
        factsOf(userId, thisMonday.minusDays(7), thisMonday.minusDays(1));
    List<WeeklyGoal> candidates = WeeklyGoal.candidates(lastWeek, brakeOf(userId, now));
    return new WeekGoalDto.Proposal(
        thisMonday, candidates.stream().map(WeekGoalDto.Candidate::from).toList());
  }

  // 이번 주(또는 다음 주) 목표 저장. 있으면 덮어씁니다. (규칙 3)
  @Transactional
  public WeekGoalDto.Item save(LocalDate weekStart, WeekGoalDto.SaveRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    Instant now = clock.instant();
    if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "주의 시작(월요일) 날짜를 보내 주세요");
    }
    LocalDate thisMonday = WeeklyFacts.mondayOf(now);
    if (weekStart.isBefore(thisMonday)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "지난 주의 목표는 바꿀 수 없어요");
    }
    if (weekStart.isAfter(thisMonday.plusDays(7))) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "이번 주나 다음 주 목표만 정할 수 있어요");
    }
    WeeklyGoal.Kind kind = WeekGoalDto.kindOf(request.kind());
    if (kind == null) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "목표 종류는 completion, startDelay, planRate, keep 중에서 골라 주세요");
    }
    if (kind != WeeklyGoal.Kind.START_DELAY
        && (request.target() > 100 || request.baseline() > 100)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "비율 목표는 100 이하로 보내 주세요");
    }
    WeeklyGoal goal = new WeeklyGoal(kind, request.target(), request.baseline());

    WeekGoal saved =
        weekGoalRepository
            .findByUserIdAndWeekStart(userId, weekStart)
            .map(
                existing -> {
                  existing.change(goal, now);
                  return existing;
                })
            .orElseGet(() -> weekGoalRepository.save(new WeekGoal(userId, weekStart, goal, now)));
    return itemOf(userId, saved, DayRule.dateOf(now));
  }

  // from ~ to 사이에 시작한 주의 목표와 달성 여부 (규칙 4)
  @Transactional(readOnly = true)
  public List<WeekGoalDto.Item> list(LocalDate from, LocalDate to) {
    Long userId = currentUserProvider.getCurrentUserId();
    if (from.isAfter(to)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "시작 날짜는 끝 날짜보다 늦을 수 없어요");
    }
    if (from.plusWeeks(MAX_WEEKS).isBefore(to)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "한 번에 1년(53주)까지만 볼 수 있어요");
    }
    LocalDate today = DayRule.dateOf(clock.instant());
    List<WeekGoalDto.Item> items = new ArrayList<>();
    for (WeekGoal goal :
        weekGoalRepository.findByUserIdAndWeekStartBetweenOrderByWeekStartAsc(userId, from, to)) {
      items.add(itemOf(userId, goal, today));
    }
    return items;
  }

  // 그 사람의 이번 주 목표 한 줄. 안 정했으면 빈 Optional 입니다. (AI 통화 · 채팅 대본의 [기록에서 꺼낸 것], CallContextLoader)
  // userId 는 부르는 쪽이 로그인 토큰으로 얻은 번호(CurrentUserProvider.getCurrentUserId())입니다.
  @Transactional(readOnly = true)
  public Optional<WeekGoalDto.Item> thisWeekOf(Long userId, LocalDate today) {
    return weekGoalRepository
        .findByUserIdAndWeekStart(userId, WeeklyFacts.mondayOf(today))
        .map(goal -> itemOf(userId, goal, today));
  }

  // 끝난 주의 판정 전 목표를 모든 학생 것으로 판정합니다. 판정한 개수를 돌려줍니다. (규칙 5, WeekGoalJudgeJob)
  // today: 오늘 (새벽 4시 기준). 지난 일요일까지 끝난 주만 판정합니다.
  @Transactional
  public int judgeEndedWeeks(LocalDate today) {
    LocalDate lastEndedMonday = WeeklyFacts.mondayOf(today).minusDays(7);
    Instant now = clock.instant();
    int judged = 0;
    for (WeekGoal goal :
        weekGoalRepository.findByStatusIsNullAndWeekStartLessThanEqualOrderById(lastEndedMonday)) {
      WeeklyGoal.WeekFacts facts =
          factsOf(goal.getUserId(), goal.getWeekStart(), goal.getWeekStart().plusDays(6));
      goal.judge(goal.toGoal().progress(facts), now);
      judged++;
    }
    return judged;
  }

  // 목표 한 줄의 응답. 판정했으면 적어 둔 결과, 아니면 그 주의 지금까지 기록으로 계산합니다. (규칙 4)
  private WeekGoalDto.Item itemOf(Long userId, WeekGoal goal, LocalDate today) {
    WeeklyGoal rule = goal.toGoal();
    WeeklyGoal.Progress progress;
    if (goal.getStatus() != null) {
      // 적어 둔 숫자로 막대 길이를 다시 셉니다. (판정 때와 같은 규칙이라 상태도 같게 나옵니다)
      Integer value = goal.getResultValue();
      progress = rule.progress(new WeeklyGoal.WeekFacts(value, 0, value, value));
    } else {
      LocalDate sunday = goal.getWeekStart().plusDays(6);
      LocalDate end = today.isBefore(sunday) ? today : sunday;
      progress =
          end.isBefore(goal.getWeekStart())
              ? new WeeklyGoal.Progress(null, WeeklyGoal.Status.UNKNOWN, 0)
              : rule.progress(factsOf(userId, goal.getWeekStart(), end));
    }
    return new WeekGoalDto.Item(
        goal.getWeekStart(),
        WeekGoalDto.kindValue(goal.getKind()),
        goal.getTarget(),
        goal.getBaseline(),
        rule.title(),
        WeekGoalDto.statusValue(progress.status()),
        progress.current(),
        progress.percent(),
        goal.getStatus() != null);
  }

  // from ~ to 의 계획 · 타이머 기록으로 주간 목표 숫자를 냅니다. 노는 계획과 노는 기록은 뺍니다. (StudyFacts 와 같은 규칙)
  WeeklyGoal.WeekFacts factsOf(Long userId, LocalDate from, LocalDate to) {
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
    List<StudyFacts.FactSession> sessions = new ArrayList<>();
    for (StudySession session :
        studySessionRepository.findStartedBetween(
            userId, DayRule.startOf(from), DayRule.startOf(to.plusDays(1)))) {
      if (session.getKind() != PlanKind.STUDY) {
        continue;
      }
      long studyMillis =
          StudyTime.studyMillis(
              session.getStartedAt(), session.getEndedAt(), session.getPausedMs());
      int targetMinutes = session.getTargetMinutes() == null ? 0 : session.getTargetMinutes();
      sessions.add(
          new StudyFacts.FactSession(
              session.getPlanId(), session.getStartedAt(), studyMillis, targetMinutes));
    }
    StudyFacts facts = StudyFacts.of(plans, sessions, from, to);
    return new WeeklyGoal.WeekFacts(
        facts.completionRate().value(),
        facts.completionRate().sampleCount(),
        facts.startDelayMinutes().value(),
        facts.planRate().value());
  }

  // 지금의 안전 브레이크. 저장된 보호 조치가 켜져 있으면 채찍 멈춤으로 봅니다. (CallService 와 같은 기준)
  private SafetyBrake brakeOf(Long userId, Instant now) {
    LocalDate today = DayRule.dateOf(now);
    List<String> texts = new ArrayList<>();
    List<LowMoodStreak.MoodRecord> moods = new ArrayList<>();
    for (CheckIn checkIn :
        checkInRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
            userId, today.minusDays(SAFETY_DAYS - 1), today)) {
      texts.add(checkIn.getMissReasonNote());
      texts.add(checkIn.getPromiseIf());
      texts.add(checkIn.getPromiseThen());
      if (checkIn.getMood() != null) {
        moods.add(new LowMoodStreak.MoodRecord(checkIn.getCheckInDate(), checkIn.getMood()));
      }
    }
    List<LocalDate> examDates = new ArrayList<>();
    for (Exam exam : examRepository.findByUserIdOrderByExamDateDescIdDesc(userId)) {
      examDates.add(exam.getExamDate());
    }
    SafetyBrake brake = SafetyBrake.check(now, texts, moods, examDates);
    if (safetyService.isActive(userId)) {
      return new SafetyBrake(true, true, brake.examPeriod(), brake.showHelp(), brake.quiet());
    }
    return brake;
  }
}
