package com.ithopeschool.projectbe.domain.stats;

import com.ithopeschool.projectbe.domain.checkin.CheckIn;
import com.ithopeschool.projectbe.domain.checkin.CheckInRepository;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.stats.dto.MissReasonStatsResponse;
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

// 실패 원인 분석 로직입니다. (이슈 #39, docs/API.md "실패 원인")
// 계획·공부 기록·체크인을 꺼내서 MissReasonStats.of 에 넘기기만 합니다. 세는 규칙은 그쪽에 있습니다.
//
// 기간은 "어제까지" 입니다. 오늘은 아직 끝나지 않아서, 넣으면 낮에 부를 때마다 오늘이 "못 채운 날" 로 잡힙니다.
//   최근 14일 = 어제 포함 14일 / 최근 30일 = 어제 포함 30일
// 30일치를 한 번만 꺼내고, 14일은 같은 재료로 기간만 좁혀서 계산합니다.
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 내 데이터만 봅니다.
// ⚠️ 체크인의 '기타' 사유 글(missReasonNote)은 쓰지 않고 돌려주지도 않습니다. 원인 종류만 셉니다.
@Service
@RequiredArgsConstructor
public class MissReasonService {

  private final CurrentUserProvider currentUserProvider;
  private final StudyPlanRepository studyPlanRepository;
  private final StudySessionRepository studySessionRepository;
  private final CheckInRepository checkInRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public MissReasonStatsResponse getMissReasons() {
    Long userId = currentUserProvider.getCurrentUserId();

    // 오늘은 하루의 기준(새벽 4시)으로 정합니다. 기간의 마지막 날은 어제입니다.
    LocalDate today = DayRule.dateOf(clock.instant());
    LocalDate to = today.minusDays(1);
    LocalDate from30 = MissReasonStats.fromDateOf(to, MissReasonStats.LONG_DAYS);
    LocalDate from14 = MissReasonStats.fromDateOf(to, MissReasonStats.SHORT_DAYS);

    // 30일치 계획과, 첫날 04:00 ~ 오늘 04:00 직전에 시작한 공부 기록
    List<StudyPlan> plans =
        studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
            userId, from30, to);
    List<StudySession> sessions =
        studySessionRepository.findStartedBetween(
            userId, DayRule.startOf(from30), DayRule.startOf(today));

    List<DaySummary.DayPlan> dayPlans = new ArrayList<>();
    for (StudyPlan plan : plans) {
      dayPlans.add(
          new DaySummary.DayPlan(plan.getPlanDate(), plan.getTargetMinutes(), plan.getKind()));
    }

    List<DaySummary.DaySession> daySessions = new ArrayList<>();
    List<MissReasonStats.ReasonFact> facts = new ArrayList<>();
    for (StudySession session : sessions) {
      daySessions.add(
          new DaySummary.DaySession(
              session.getStartedAt(),
              session.getEndedAt(),
              session.getPausedMs(),
              session.getPauseCount(),
              session.getKind()));
      // 타이머를 목표 전에 멈추며 고른 이유 → 그 기록을 시작한 날의 원인. 노는 기록은 공부가 아니라서 뺍니다.
      if (session.getKind() != PlanKind.PLAY && session.getStopReason() != null) {
        facts.add(
            new MissReasonStats.ReasonFact(
                DayRule.dateOf(session.getStartedAt()), session.getStopReason()));
      }
    }

    // 체크인의 이유는 "어제" 계획을 못 채운 이유라서 하루 전 날짜로 셉니다.
    // 그래서 기간 첫날의 다음 날부터 오늘의 체크인까지 봅니다. (오늘 체크인의 이유 = 어제의 이유)
    // 통화에서 말한 이유도 통화를 마무리할 때 체크인에 채워지므로(CallCheckIn) 여기서 같이 들어옵니다.
    for (CheckIn checkIn :
        checkInRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
            userId, from30.plusDays(1), today)) {
      if (checkIn.getMissReason() != null) {
        facts.add(
            new MissReasonStats.ReasonFact(
                checkIn.getCheckInDate().minusDays(1), checkIn.getMissReason()));
      }
    }

    // 날마다의 달성률은 하루 요약표와 같은 계산(DaySummary)으로 구합니다. 계획이 없는 날은 null 입니다.
    List<MissReasonStats.Day> days = new ArrayList<>();
    for (LocalDate date = from30; !date.isAfter(to); date = date.plusDays(1)) {
      Integer rate = DaySummary.of(date, dayPlans, daySessions).achievementRate();
      days.add(new MissReasonStats.Day(date, rate));
    }

    return MissReasonStatsResponse.of(
        MissReasonStats.of(days, facts, from14, to), MissReasonStats.of(days, facts, from30, to));
  }
}
