package com.ithopeschool.projectbe.domain.stats;

import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.checkin.CheckIn;
import com.ithopeschool.projectbe.domain.checkin.CheckInRepository;
import com.ithopeschool.projectbe.domain.exam.Exam;
import com.ithopeschool.projectbe.domain.exam.ExamRepository;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.stats.AfterEventEffect.EventType;
import com.ithopeschool.projectbe.domain.stats.dto.RhythmStatsResponse;
import com.ithopeschool.projectbe.domain.summary.DaySummary;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 리듬·패턴 분석 로직입니다. (이슈 #37, docs/API.md "리듬·패턴")
// 계획·공부 기록·체크인·시험을 꺼내서 세 계산에 넘기기만 합니다. 규칙은 각 계산 쪽에 있습니다.
//   CollapseCycle(며칠째 무너지고 며칠 만에 회복) · RhythmEfficiency(요일·시간대) · AfterEventEffect(이벤트 다음 날)
//
// 기간은 "어제까지 30일" 입니다.
//   - 오늘은 아직 끝나지 않아서, 넣으면 낮에 부를 때마다 오늘이 "못 채운 날" 로 잡힙니다. (실패 원인 #39 와 같음)
//   - 리듬은 요일마다·시간대마다 나눠 보는 숫자라, 14일이면 요일 하나에 이틀뿐이라 거의 다 가설이 됩니다.
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 내 데이터만 봅니다.
// ⚠️ 체크인의 '기타' 사유 글(missReasonNote)은 쓰지 않습니다. 이유 종류가 '약속·행사' 인지만 봅니다.
@Service
@RequiredArgsConstructor
public class RhythmService {

  // 몇 일을 보는지 (어제 포함)
  public static final int PERIOD_DAYS = 30;

  private final CurrentUserProvider currentUserProvider;
  private final StudyPlanRepository studyPlanRepository;
  private final StudySessionRepository studySessionRepository;
  private final CheckInRepository checkInRepository;
  private final ExamRepository examRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public RhythmStatsResponse getRhythm() {
    Long userId = currentUserProvider.getCurrentUserId();

    // 오늘은 하루의 기준(새벽 4시)으로 정합니다. 기간의 마지막 날은 어제입니다.
    LocalDate today = DayRule.dateOf(clock.instant());
    LocalDate to = today.minusDays(1);
    LocalDate from = to.minusDays(PERIOD_DAYS - 1);

    // 기간의 계획과, 첫날 04:00 ~ 오늘 04:00 직전에 시작한 공부 기록
    List<StudyPlan> plans =
        studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(userId, from, to);
    List<StudySession> sessions =
        studySessionRepository.findStartedBetween(
            userId, DayRule.startOf(from), DayRule.startOf(today));

    List<DaySummary.DayPlan> dayPlans = new ArrayList<>();
    for (StudyPlan plan : plans) {
      dayPlans.add(
          new DaySummary.DayPlan(plan.getPlanDate(), plan.getTargetMinutes(), plan.getKind()));
    }

    List<DaySummary.DaySession> daySessions = new ArrayList<>();
    List<RhythmEfficiency.Session> studySessions = new ArrayList<>();
    List<AfterEventEffect.Event> events = new ArrayList<>();
    for (StudySession session : sessions) {
      daySessions.add(
          new DaySummary.DaySession(
              session.getStartedAt(),
              session.getEndedAt(),
              session.getPausedMs(),
              session.getPauseCount(),
              session.getKind()));
      // 노는 기록은 공부가 아니라서 시간대 효율과 이벤트에서 뺍니다. (DaySummary 는 스스로 뺍니다)
      if (session.getKind() == PlanKind.PLAY) {
        continue;
      }
      studySessions.add(
          new RhythmEfficiency.Session(
              session.getStartedAt(), session.getEndedAt(), session.getPausedMs()));
      // 타이머를 멈추며 '약속·행사' 를 고른 날 → 그 기록을 시작한 날의 약속
      if (session.getStopReason() == MissReason.EVENT) {
        events.add(
            new AfterEventEffect.Event(
                DayRule.dateOf(session.getStartedAt()), EventType.APPOINTMENT));
      }
    }

    // 체크인의 "어제 못 채운 이유" 가 '약속·행사' → 체크인 날짜의 하루 전의 약속 (실패 원인 #39 와 같은 날짜 맞추기)
    // 기간 첫날의 체크인 = 기간 전날의 약속이고, 그 다음 날(첫날)은 기간 안이라 셉니다.
    for (CheckIn checkIn :
        checkInRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
            userId, from, today)) {
      if (checkIn.getMissReason() == MissReason.EVENT) {
        events.add(
            new AfterEventEffect.Event(
                checkIn.getCheckInDate().minusDays(1), EventType.APPOINTMENT));
      }
    }

    // 내 시험들. 기간 밖의 시험은 AfterEventEffect 가 "다음 날이 기간 안인지" 로 걸러냅니다.
    for (Exam exam : examRepository.findByUserIdOrderByExamDateDescIdDesc(userId)) {
      events.add(new AfterEventEffect.Event(exam.getExamDate(), EventType.EXAM));
    }

    // 날마다의 순공과 달성률은 하루 요약표와 같은 계산(DaySummary)으로 구합니다.
    List<CollapseCycle.Day> cycleDays = new ArrayList<>();
    List<RhythmEfficiency.Day> rhythmDays = new ArrayList<>();
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      DaySummary summary = DaySummary.of(date, dayPlans, daySessions);
      cycleDays.add(new CollapseCycle.Day(date, summary.achievementRate()));
      rhythmDays.add(
          new RhythmEfficiency.Day(date, summary.studyMinutes(), summary.achievementRate()));
    }

    return RhythmStatsResponse.of(
        from,
        to,
        CollapseCycle.of(cycleDays, from, to),
        RhythmEfficiency.of(rhythmDays, studySessions, from, to),
        AfterEventEffect.of(rhythmDays, events, from, to));
  }
}
