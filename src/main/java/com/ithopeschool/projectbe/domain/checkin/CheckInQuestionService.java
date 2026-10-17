package com.ithopeschool.projectbe.domain.checkin;

import com.ithopeschool.projectbe.domain.checkin.dto.CheckInQuestionsResponse;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.summary.DaySummary;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 오늘 체크인에서 물어볼 질문을 정하는 로직입니다. (이슈 #25, docs/API.md "오늘 물어볼 질문")
// 어제의 계획·공부 기록·체크인을 꺼내서 CheckInQuestions.pick 에 넘기기만 합니다. 고르는 규칙은 그쪽에 있습니다.
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 내 데이터만 봅니다.
@Service
@RequiredArgsConstructor
public class CheckInQuestionService {

  private final CurrentUserProvider currentUserProvider;
  private final StudyPlanRepository studyPlanRepository;
  private final StudySessionRepository studySessionRepository;
  private final CheckInRepository checkInRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public CheckInQuestionsResponse getTodayQuestions() {
    Long userId = currentUserProvider.getCurrentUserId();

    // 오늘과 어제는 하루의 기준(새벽 4시)으로 정합니다. 새벽 1시에 부르면 "오늘" 은 아직 전날입니다.
    LocalDate today = DayRule.dateOf(clock.instant());
    LocalDate yesterday = today.minusDays(1);

    // 어제 계획과, 어제 04:00 ~ 오늘 04:00 직전에 시작한 공부 기록
    List<StudyPlan> plans = studyPlanRepository.findByUserIdAndPlanDateOrderById(userId, yesterday);
    List<StudySession> sessions =
        studySessionRepository.findStartedBetween(
            userId, DayRule.startOf(yesterday), DayRule.startOf(today));

    // 어제 달성률은 하루 요약표와 같은 계산(DaySummary)으로 구합니다. 노는 계획·기록은 거기서 빠집니다.
    List<DaySummary.DayPlan> dayPlans = new ArrayList<>();
    for (StudyPlan plan : plans) {
      dayPlans.add(
          new DaySummary.DayPlan(plan.getPlanDate(), plan.getTargetMinutes(), plan.getKind()));
    }
    List<DaySummary.DaySession> daySessions = new ArrayList<>();
    boolean hasStopReason = false;
    for (StudySession session : sessions) {
      daySessions.add(
          new DaySummary.DaySession(
              session.getStartedAt(),
              session.getEndedAt(),
              session.getPausedMs(),
              session.getPauseCount(),
              session.getKind()));
      if (session.getStopReason() != null) {
        hasStopReason = true;
      }
    }
    Integer yesterdayRate = DaySummary.of(yesterday, dayPlans, daySessions).achievementRate();

    // 어제 체크인에 약속이 있었는지. 약속은 두 칸이 같이 있거나 같이 비어 있어서 한 칸만 봅니다.
    Optional<CheckIn> yesterdayCheckIn =
        checkInRepository.findByUserIdAndCheckInDate(userId, yesterday);
    boolean hasPromise =
        yesterdayCheckIn.isPresent() && yesterdayCheckIn.get().getPromiseIf() != null;

    CheckInQuestions questions = CheckInQuestions.pick(yesterdayRate, hasStopReason, hasPromise);
    return new CheckInQuestionsResponse(today, questions.basic(), questions.detail());
  }
}
