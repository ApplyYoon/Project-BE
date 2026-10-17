package com.ithopeschool.projectbe.domain.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.checkin.dto.CheckInQuestionsResponse;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Service 단위 테스트: DB(Repository)를 가짜로 바꿔서, 어제 데이터를 제대로 모아 질문을 고르는지 확인합니다.
class CheckInQuestionServiceTest {

  private static final Long USER_ID = 7L;
  // 지금은 2026-10-17 오후 6시(한국 시간)
  private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");
  private static final LocalDate TODAY = LocalDate.parse("2026-10-17");
  private static final LocalDate YESTERDAY = LocalDate.parse("2026-10-16");
  // 10/16 오후 7시(한국 시간)
  private static final Instant YESTERDAY_7PM = Instant.parse("2026-10-16T10:00:00Z");
  private static final long MINUTE = 60_000L;

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final StudyPlanRepository studyPlanRepository = mock(StudyPlanRepository.class);
  private final StudySessionRepository studySessionRepository = mock(StudySessionRepository.class);
  private final CheckInRepository checkInRepository = mock(CheckInRepository.class);

  private CheckInQuestionService serviceAt(Instant now) {
    return new CheckInQuestionService(
        currentUserProvider,
        studyPlanRepository,
        studySessionRepository,
        checkInRepository,
        Clock.fixed(now, ZoneOffset.UTC));
  }

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(studyPlanRepository.findByUserIdAndPlanDateOrderById(any(), any())).willReturn(List.of());
    given(studySessionRepository.findStartedBetween(any(), any(), any())).willReturn(List.of());
    given(checkInRepository.findByUserIdAndCheckInDate(any(), any())).willReturn(Optional.empty());
  }

  // 어제 계획 하나 (목표 minutes 분)
  private void planYesterday(int minutes, PlanKind kind) {
    given(studyPlanRepository.findByUserIdAndPlanDateOrderById(USER_ID, YESTERDAY))
        .willReturn(List.of(new StudyPlan(USER_ID, 1L, YESTERDAY, "수학", minutes, null, kind)));
  }

  // 어제 오후 7시에 시작한 기록 하나 (minutes 분, 그만둔 이유 stopReason)
  private static StudySession sessionYesterday(int minutes, MissReason stopReason) {
    return new StudySession(
        USER_ID,
        1L,
        YESTERDAY_7PM,
        YESTERDAY_7PM.plusMillis(minutes * MINUTE),
        0,
        0,
        null,
        null,
        stopReason,
        PlanKind.STUDY);
  }

  private void sessionsYesterday(StudySession... sessions) {
    given(
            studySessionRepository.findStartedBetween(
                USER_ID,
                Instant.parse("2026-10-15T19:00:00Z"), // 10/16 04:00 (한국 시간)
                Instant.parse("2026-10-16T19:00:00Z"))) // 10/17 04:00 (한국 시간)
        .willReturn(List.of(sessions));
  }

  @Test
  @DisplayName("어제 60분 계획에 30분만 했으면(50%) 못 채운 이유를 묻는다")
  void asksMissReasonWhenMissed() {
    planYesterday(60, PlanKind.STUDY);
    sessionsYesterday(sessionYesterday(30, null));

    CheckInQuestionsResponse response = serviceAt(NOW).getTodayQuestions();

    assertThat(response.date()).isEqualTo(TODAY);
    assertThat(response.basic()).containsExactly("mood");
    assertThat(response.detail()).containsExactly("condition", "expectedMinutes", "missReason");
  }

  @Test
  @DisplayName("어제 계획을 다 채웠거나, 계획이 없었으면 이유를 묻지 않는다")
  void skipsMissReasonWhenReachedOrNoPlan() {
    planYesterday(60, PlanKind.STUDY);
    sessionsYesterday(sessionYesterday(60, null));
    assertThat(serviceAt(NOW).getTodayQuestions().detail()).doesNotContain("missReason");

    // 노는 계획만 있던 날은 공부 계획이 없는 날이라 달성률을 계산할 수 없습니다(미달 아님).
    planYesterday(60, PlanKind.PLAY);
    sessionsYesterday();
    assertThat(serviceAt(NOW).getTodayQuestions().detail()).doesNotContain("missReason");
  }

  @Test
  @DisplayName("어제 못 채웠어도 그만둔 이유를 고른 기록이 있으면 묻지 않는다")
  void skipsMissReasonWhenStopReasonExists() {
    planYesterday(60, PlanKind.STUDY);
    sessionsYesterday(sessionYesterday(20, null), sessionYesterday(10, MissReason.PHONE));

    assertThat(serviceAt(NOW).getTodayQuestions().detail()).doesNotContain("missReason");
  }

  @Test
  @DisplayName("어제 체크인에 약속이 있으면 지켰는지 묻는다")
  void asksPromiseKeptWhenPromisedYesterday() {
    CheckIn withPromise =
        new CheckIn(
            USER_ID,
            YESTERDAY,
            CheckInSource.MANUAL,
            null,
            3,
            null,
            null,
            null,
            "폰을 보고 싶으면",
            "물 한 잔 마신다",
            null);
    given(checkInRepository.findByUserIdAndCheckInDate(USER_ID, YESTERDAY))
        .willReturn(Optional.of(withPromise));

    assertThat(serviceAt(NOW).getTodayQuestions().detail()).contains("promiseKept");
  }

  @Test
  @DisplayName("어제 체크인이 없거나 약속이 비어 있으면 지켰는지 묻지 않는다")
  void skipsPromiseKeptWithoutPromise() {
    assertThat(serviceAt(NOW).getTodayQuestions().detail()).doesNotContain("promiseKept");

    CheckIn noPromise =
        new CheckIn(
            USER_ID, YESTERDAY, CheckInSource.QUICK, 3, 3, null, null, 60, null, null, null);
    given(checkInRepository.findByUserIdAndCheckInDate(USER_ID, YESTERDAY))
        .willReturn(Optional.of(noPromise));
    assertThat(serviceAt(NOW).getTodayQuestions().detail()).doesNotContain("promiseKept");
  }

  @Test
  @DisplayName("새벽 1시에 부르면 '오늘' 은 아직 전날이고, 어제는 그 전날이다 (새벽 4시 기준)")
  void usesFourAmDayBoundary() {
    // 10/18 새벽 1시(한국 시간) → 오늘 10/17, 어제 10/16
    CheckInQuestionsResponse response =
        serviceAt(Instant.parse("2026-10-17T16:00:00Z")).getTodayQuestions();

    assertThat(response.date()).isEqualTo(TODAY);
    verify(checkInRepository).findByUserIdAndCheckInDate(USER_ID, YESTERDAY);
    verify(studyPlanRepository).findByUserIdAndPlanDateOrderById(USER_ID, YESTERDAY);
  }
}
