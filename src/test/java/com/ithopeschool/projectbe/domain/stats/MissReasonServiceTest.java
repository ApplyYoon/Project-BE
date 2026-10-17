package com.ithopeschool.projectbe.domain.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.checkin.CheckIn;
import com.ithopeschool.projectbe.domain.checkin.CheckInRepository;
import com.ithopeschool.projectbe.domain.checkin.CheckInSource;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.stats.dto.MissReasonStatsResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Service 단위 테스트: DB(Repository)를 가짜로 바꿔서, 기간과 원인 날짜를 제대로 맞춰 넘기는지 확인합니다.
// 세는 규칙 자체는 MissReasonStatsTest 가 확인합니다.
class MissReasonServiceTest {

  private static final Long USER_ID = 7L;
  // 지금은 2026-10-17 오후 6시(한국 시간) → 오늘 10/17, 기간의 마지막 날은 어제 10/16
  private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
  private static final long MINUTE = 60_000L;

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final StudyPlanRepository studyPlanRepository = mock(StudyPlanRepository.class);
  private final StudySessionRepository studySessionRepository = mock(StudySessionRepository.class);
  private final CheckInRepository checkInRepository = mock(CheckInRepository.class);
  private final MissReasonService service =
      new MissReasonService(
          currentUserProvider,
          studyPlanRepository,
          studySessionRepository,
          checkInRepository,
          Clock.fixed(NOW, ZoneOffset.UTC));

  // 가짜 DB 에 넣을 것들. 테스트마다 채웁니다.
  private final List<StudyPlan> plans = new ArrayList<>();
  private final List<StudySession> sessions = new ArrayList<>();
  private final List<CheckIn> checkIns = new ArrayList<>();

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(
            studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
                any(), any(), any()))
        .willReturn(plans);
    given(studySessionRepository.findStartedBetween(any(), any(), any())).willReturn(sessions);
    given(
            checkInRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
                any(), any(), any()))
        .willReturn(checkIns);
  }

  // 10월 day 일에 60분 공부 계획 하나
  private void plan(int day) {
    plans.add(
        new StudyPlan(USER_ID, 1L, LocalDate.of(2026, 10, day), "수학", 60, null, PlanKind.STUDY));
  }

  // 10월 day 일 오후 7시(한국 시간)에 minutes 분 공부한 기록 (멈추며 고른 이유 stopReason)
  private void session(int day, int minutes, MissReason stopReason, PlanKind kind) {
    Instant start = Instant.parse(String.format("2026-10-%02dT10:00:00Z", day));
    sessions.add(
        new StudySession(
            USER_ID,
            1L,
            start,
            start.plusMillis(minutes * MINUTE),
            0,
            0,
            null,
            null,
            stopReason,
            kind));
  }

  // 10월 day 일의 체크인 ("어제 못 채운 이유" missReason)
  private void checkIn(int day, MissReason missReason) {
    checkIns.add(
        new CheckIn(
            USER_ID,
            LocalDate.of(2026, 10, day),
            CheckInSource.MANUAL,
            null,
            null,
            missReason,
            null,
            null,
            null,
            null,
            null));
  }

  @Test
  @DisplayName("어제까지 30일치를 내 번호로 꺼낸다 (체크인은 하루 뒤까지, 공부 기록은 새벽 4시 기준)")
  void readsLast30DaysUntilYesterday() {
    MissReasonStatsResponse response = service.getMissReasons();

    // 30일 = 9/17 ~ 10/16, 14일 = 10/3 ~ 10/16
    assertThat(response.last30Days().from()).isEqualTo(LocalDate.of(2026, 9, 17));
    assertThat(response.last30Days().to()).isEqualTo(LocalDate.of(2026, 10, 16));
    assertThat(response.last14Days().from()).isEqualTo(LocalDate.of(2026, 10, 3));
    assertThat(response.last14Days().to()).isEqualTo(LocalDate.of(2026, 10, 16));

    verify(studyPlanRepository)
        .findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
            USER_ID, LocalDate.of(2026, 9, 17), LocalDate.of(2026, 10, 16));
    verify(studySessionRepository)
        .findStartedBetween(
            USER_ID,
            Instant.parse("2026-09-16T19:00:00Z"), // 9/17 04:00 (한국 시간)
            Instant.parse("2026-10-16T19:00:00Z")); // 10/17 04:00 (한국 시간)
    // 9/18 체크인의 이유 = 9/17 의 이유 ... 오늘(10/17) 체크인의 이유 = 어제(10/16) 의 이유
    verify(checkInRepository)
        .findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
            USER_ID, LocalDate.of(2026, 9, 18), TODAY);
  }

  @Test
  @DisplayName("체크인의 이유는 하루 전 날의 원인이고, 다음 날 다 채우면 회복 1일")
  void checkInReasonIsForTheDayBefore() {
    // 10/15: 60분 계획에 30분 (못 채움) / 10/16: 60분 계획에 60분 (다 채움)
    plan(15);
    session(15, 30, null, PlanKind.STUDY);
    plan(16);
    session(16, 60, null, PlanKind.STUDY);
    // 10/16 체크인에 "어제 못 채운 이유: 폰·SNS"
    checkIn(16, MissReason.PHONE);

    MissReasonStatsResponse.Period last14 = service.getMissReasons().last14Days();

    assertThat(last14.missedDayCount()).isEqualTo(1);
    assertThat(last14.topReasons()).containsExactly("phone");
    MissReasonStatsResponse.Reason phone = last14.reasons().get(0);
    assertThat(phone.dates()).containsExactly(LocalDate.of(2026, 10, 15));
    assertThat(phone.averageRecoveryDays()).isEqualTo(1.0);
    // 10/15 은 목요일 (0번째 = 월요일)
    assertThat(phone.weekdayCounts()).containsExactly(0, 0, 0, 1, 0, 0, 0);
  }

  @Test
  @DisplayName("타이머를 멈추며 고른 이유는 그날의 원인이고, 노는 기록의 이유는 세지 않는다")
  void timerStopReasonIsForThatDay() {
    plan(14);
    session(14, 20, MissReason.TIRED, PlanKind.STUDY);
    session(14, 10, MissReason.UNWILLING, PlanKind.PLAY);

    MissReasonStatsResponse.Period last14 = service.getMissReasons().last14Days();

    assertThat(last14.topReasons()).containsExactly("tired");
    assertThat(last14.reasons()).hasSize(1);
  }

  @Test
  @DisplayName("14일보다 오래된 날은 30일 결과에만 들어간다")
  void olderDaysOnlyInLast30() {
    // 10/1 못 채움 (이유: 계획 과다) → 14일(10/3 ~)에는 없고 30일에는 있음
    plan(1);
    session(1, 10, MissReason.TOO_MUCH, PlanKind.STUDY);

    MissReasonStatsResponse response = service.getMissReasons();

    assertThat(response.last14Days().reasons()).isEmpty();
    assertThat(response.last14Days().topReasons()).isEmpty();
    assertThat(response.last30Days().topReasons()).containsExactly("tooMuch");
  }

  @Test
  @DisplayName("오늘은 아직 안 끝나서 넣지 않는다 (오늘 덜 했어도 못 채운 날이 아님)")
  void todayIsNotCounted() {
    plan(17);
    session(17, 10, MissReason.PHONE, PlanKind.STUDY);

    MissReasonStatsResponse response = service.getMissReasons();

    assertThat(response.last14Days().missedDayCount()).isZero();
    assertThat(response.last14Days().reasons()).isEmpty();
  }

  @Test
  @DisplayName("로그인하지 않았으면 401 이고, DB 를 보지 않는다")
  void unauthorized() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    assertThatThrownBy(service::getMissReasons)
        .isInstanceOf(ApiException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.UNAUTHORIZED);
    verifyNoInteractions(studyPlanRepository, studySessionRepository, checkInRepository);
  }
}
