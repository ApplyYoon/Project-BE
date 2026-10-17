package com.ithopeschool.projectbe.domain.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.insight.dto.HypothesesResponse;
import com.ithopeschool.projectbe.domain.onboarding.FailureCause;
import com.ithopeschool.projectbe.domain.onboarding.Grade;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswer;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswerRepository;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Service 단위 테스트: DB(Repository)를 가짜로 바꿔서 "내 설문 → 가설" 과 "첫 기록 날짜 → 카운트다운" 을 확인합니다.
class InsightServiceTest {

  private static final Long USER_ID = 7L;
  // 지금은 2026-10-17 오후 6시(한국 시간) → 오늘 10/17
  private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final OnboardingAnswerRepository onboardingAnswerRepository =
      mock(OnboardingAnswerRepository.class);
  private final StudyPlanRepository studyPlanRepository = mock(StudyPlanRepository.class);
  private final StudySessionRepository studySessionRepository = mock(StudySessionRepository.class);
  private final InsightService service =
      new InsightService(
          currentUserProvider,
          onboardingAnswerRepository,
          studyPlanRepository,
          studySessionRepository,
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    // 따로 정하지 않으면 설문·계획·기록 모두 없음
    given(onboardingAnswerRepository.findFirstByUserIdOrderByAnsweredAtDescIdDesc(USER_ID))
        .willReturn(Optional.empty());
    given(studyPlanRepository.findFirstByUserIdOrderByPlanDateAscIdAsc(USER_ID))
        .willReturn(Optional.empty());
    given(studySessionRepository.findFirstByUserIdOrderByStartedAtAscIdAsc(USER_ID))
        .willReturn(Optional.empty());
  }

  private void givenLatestAnswer(EnumSet<FailureCause> causes) {
    OnboardingAnswer.Values values = new OnboardingAnswer.Values();
    values.grade = Grade.values()[0];
    values.targetExam = "수능";
    values.targetExamDate = LocalDate.of(2027, 11, 18);
    values.dailyAvailableMinutes = 180;
    values.pastFailureCauses = causes;
    given(onboardingAnswerRepository.findFirstByUserIdOrderByAnsweredAtDescIdDesc(USER_ID))
        .willReturn(Optional.of(new OnboardingAnswer(USER_ID, NOW, values)));
  }

  @Test
  @DisplayName("설문도 기록도 없으면 404 가 아니라 빈 가설 + 기본 문장 + 남은 14일")
  void nothingYet() {
    HypothesesResponse response = service.getHypotheses();

    assertThat(response.hypotheses()).isEmpty();
    assertThat(response.defaultSentence()).isEqualTo(FirstHypothesis.DEFAULT_SENTENCE);
    assertThat(response.firstReport())
        .isEqualTo(new HypothesesResponse.FirstReport(null, 14, 0, 14, false));
  }

  @Test
  @DisplayName("지금 내 설문의 답으로 가설을 만들고, 가설이 있으면 기본 문장은 null")
  void hypothesesFromLatestAnswer() {
    givenLatestAnswer(EnumSet.of(FailureCause.WEEKEND, FailureCause.PHONE));

    HypothesesResponse response = service.getHypotheses();

    assertThat(response.hypotheses())
        .extracting(HypothesesResponse.Hypothesis::answer)
        .containsExactly("weekend", "phone");
    assertThat(response.hypotheses())
        .extracting(HypothesesResponse.Hypothesis::analysisIssue)
        .containsExactly(37, 39);
    assertThat(response.hypotheses().get(0).analysis()).isEqualTo("rhythm");
    assertThat(response.defaultSentence()).isNull();
  }

  @Test
  @DisplayName("첫 기록 날짜 = 계획 날짜와 공부 기록을 시작한 날(새벽 4시 기준) 중 더 이른 날")
  void firstRecordDateIsTheEarlier() {
    // 첫 계획은 10/10
    given(studyPlanRepository.findFirstByUserIdOrderByPlanDateAscIdAsc(USER_ID))
        .willReturn(Optional.of(new StudyPlan(USER_ID, 1L, LocalDate.of(2026, 10, 10), "수학", 60)));
    // 첫 공부 기록은 한국 시간 10/09 새벽 2시 시작 → 새벽 4시 전이라 10/08 로 칩니다.
    Instant oct9TwoAm = Instant.parse("2026-10-08T17:00:00Z");
    given(studySessionRepository.findFirstByUserIdOrderByStartedAtAscIdAsc(USER_ID))
        .willReturn(
            Optional.of(new StudySession(USER_ID, 1L, oct9TwoAm, oct9TwoAm.plusSeconds(3600), 0)));

    HypothesesResponse.FirstReport firstReport = service.getHypotheses().firstReport();

    // 10/08 ~ 10/17 = 10일 지남, 4일 남음
    assertThat(firstReport.firstRecordDate()).isEqualTo(LocalDate.of(2026, 10, 8));
    assertThat(firstReport.daysPassed()).isEqualTo(10);
    assertThat(firstReport.daysLeft()).isEqualTo(4);
    assertThat(firstReport.ready()).isFalse();
  }

  @Test
  @DisplayName("계획만 있어도 그 날짜부터 센다 (14일이 차면 ready)")
  void onlyPlans() {
    given(studyPlanRepository.findFirstByUserIdOrderByPlanDateAscIdAsc(USER_ID))
        .willReturn(Optional.of(new StudyPlan(USER_ID, 1L, TODAY.minusDays(13), "수학", 60)));

    HypothesesResponse.FirstReport firstReport = service.getHypotheses().firstReport();

    assertThat(firstReport.daysPassed()).isEqualTo(14);
    assertThat(firstReport.ready()).isTrue();
  }

  @Test
  @DisplayName("로그인하지 않았으면 401 이고 DB 를 보지 않는다")
  void unauthorized() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    assertThatThrownBy(service::getHypotheses).isInstanceOf(ApiException.class);
    verifyNoInteractions(onboardingAnswerRepository, studyPlanRepository, studySessionRepository);
  }
}
