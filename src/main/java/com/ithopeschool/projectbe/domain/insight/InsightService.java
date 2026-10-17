package com.ithopeschool.projectbe.domain.insight;

import com.ithopeschool.projectbe.domain.insight.dto.HypothesesResponse;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswer;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswerRepository;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 인사이트 로직입니다. 지금은 "첫 가설 + 첫 리포트 카운트다운" 만 있습니다. (이슈 #35)
//
// 가설은 DB 에 따로 저장하지 않고, 부를 때마다 "지금 내 설문 답" 으로 새로 만듭니다.
// 그래서 나중에 "내 정보 더 알려주기" 로 답이 들어오면, 다음에 부를 때 그 답의 가설이 바로 생깁니다.
// 모든 조회는 currentUserProvider.getCurrentUserId() 로 얻은 내 번호로만 합니다. 로그인하지 않으면 401 입니다.
@Service
@RequiredArgsConstructor
public class InsightService {

  private final CurrentUserProvider currentUserProvider;
  private final OnboardingAnswerRepository onboardingAnswerRepository;
  private final StudyPlanRepository studyPlanRepository;
  private final StudySessionRepository studySessionRepository;
  private final Clock clock;

  // 내 첫 가설과 첫 리포트까지 남은 날. 설문을 안 했어도 404 가 아니라 빈 가설 + 기본 문장을 돌려줍니다.
  @Transactional(readOnly = true)
  public HypothesesResponse getHypotheses() {
    Long userId = currentUserProvider.getCurrentUserId();

    // 지금 내 설문 (답한 시각이 가장 늦은 줄). 안 했으면 null
    OnboardingAnswer latest =
        onboardingAnswerRepository
            .findFirstByUserIdOrderByAnsweredAtDescIdDesc(userId)
            .orElse(null);
    List<FirstHypothesis.Hypothesis> hypotheses = FirstHypothesis.from(latest);

    LocalDate today = DayRule.dateOf(clock.instant());
    FirstReportCountdown.Status countdown =
        FirstReportCountdown.of(findFirstRecordDate(userId), today);

    return HypothesesResponse.of(hypotheses, countdown);
  }

  // 첫 기록 날짜: 내 공부 계획의 가장 이른 날짜와, 내 공부 기록을 가장 처음 시작한 날 중 더 이른 날. 둘 다 없으면 null
  private LocalDate findFirstRecordDate(Long userId) {
    Optional<StudyPlan> firstPlan =
        studyPlanRepository.findFirstByUserIdOrderByPlanDateAscIdAsc(userId);
    Optional<StudySession> firstSession =
        studySessionRepository.findFirstByUserIdOrderByStartedAtAscIdAsc(userId);

    LocalDate planDate = firstPlan.map(StudyPlan::getPlanDate).orElse(null);
    // 공부 기록은 시작한 순간을 "새벽 4시 기준 날짜" 로 바꿉니다. (docs/API.md 2-4)
    LocalDate sessionDate =
        firstSession.map(session -> DayRule.dateOf(session.getStartedAt())).orElse(null);

    if (planDate == null) {
      return sessionDate;
    }
    if (sessionDate == null) {
      return planDate;
    }
    return planDate.isBefore(sessionDate) ? planDate : sessionDate;
  }
}
