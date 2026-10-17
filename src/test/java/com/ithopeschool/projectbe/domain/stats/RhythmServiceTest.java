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
import com.ithopeschool.projectbe.domain.exam.Exam;
import com.ithopeschool.projectbe.domain.exam.ExamRepository;
import com.ithopeschool.projectbe.domain.exam.ExamType;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.stats.dto.RhythmStatsResponse;
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

// Service 단위 테스트: DB(Repository)를 가짜로 바꿔서, 기간과 재료(날·기록·이벤트)를 제대로 맞춰 넘기는지 확인합니다.
// 계산 규칙 자체는 CollapseCycleTest · RhythmEfficiencyTest · AfterEventEffectTest 가 확인합니다.
class RhythmServiceTest {

  private static final Long USER_ID = 7L;
  // 지금은 2026-10-17 오후 6시(한국 시간) → 오늘 10/17, 기간은 9/17 ~ 어제 10/16
  private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
  private static final long MINUTE = 60_000L;

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final StudyPlanRepository studyPlanRepository = mock(StudyPlanRepository.class);
  private final StudySessionRepository studySessionRepository = mock(StudySessionRepository.class);
  private final CheckInRepository checkInRepository = mock(CheckInRepository.class);
  private final ExamRepository examRepository = mock(ExamRepository.class);
  private final RhythmService service =
      new RhythmService(
          currentUserProvider,
          studyPlanRepository,
          studySessionRepository,
          checkInRepository,
          examRepository,
          Clock.fixed(NOW, ZoneOffset.UTC));

  // 가짜 DB 에 넣을 것들. 테스트마다 채웁니다.
  private final List<StudyPlan> plans = new ArrayList<>();
  private final List<StudySession> sessions = new ArrayList<>();
  private final List<CheckIn> checkIns = new ArrayList<>();
  private final List<Exam> exams = new ArrayList<>();

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
    given(examRepository.findByUserIdOrderByExamDateDescIdDesc(any())).willReturn(exams);
  }

  // 10월 day 일에 60분 공부 계획 하나
  private void plan(int day) {
    plans.add(
        new StudyPlan(USER_ID, 1L, LocalDate.of(2026, 10, day), "수학", 60, null, PlanKind.STUDY));
  }

  // 10월 day 일 hour 시(한국 시간)에 minutes 분 공부한 기록 (멈추며 고른 이유 stopReason)
  private void session(int day, int hour, int minutes, MissReason stopReason, PlanKind kind) {
    // 한국 시간 = UTC + 9시간
    Instant start = LocalDate.of(2026, 10, day).atTime(hour, 0).toInstant(ZoneOffset.ofHours(9));
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
  @DisplayName("어제까지 30일치를 내 번호로 꺼낸다 (체크인은 오늘까지, 공부 기록은 새벽 4시 기준)")
  void readsLast30DaysUntilYesterday() {
    RhythmStatsResponse response = service.getRhythm();

    assertThat(response.from()).isEqualTo(LocalDate.of(2026, 9, 17));
    assertThat(response.to()).isEqualTo(LocalDate.of(2026, 10, 16));
    verify(studyPlanRepository)
        .findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
            USER_ID, LocalDate.of(2026, 9, 17), LocalDate.of(2026, 10, 16));
    verify(studySessionRepository)
        .findStartedBetween(
            USER_ID,
            Instant.parse("2026-09-16T19:00:00Z"), // 9/17 04:00 (한국 시간)
            Instant.parse("2026-10-16T19:00:00Z")); // 10/17 04:00 (한국 시간)
    // 9/17 체크인의 이유 = 9/16 의 약속 → 다음 날 9/17 은 기간 안. 오늘(10/17) 체크인 = 어제(10/16) 의 약속
    verify(checkInRepository)
        .findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
            USER_ID, LocalDate.of(2026, 9, 17), TODAY);
    verify(examRepository).findByUserIdOrderByExamDateDescIdDesc(USER_ID);
  }

  @Test
  @DisplayName("날마다의 달성률로 며칠째 무너지고 며칠 만에 회복했는지 계산한다")
  void findsCollapseAndRecovery() {
    // 10/12 ~ 10/16 매일 60분 계획. 12·13 다 채움, 14 30분(못 채움), 15·16 다 채움
    for (int day = 12; day <= 16; day++) {
      plan(day);
    }
    session(12, 19, 60, null, PlanKind.STUDY);
    session(13, 19, 60, null, PlanKind.STUDY);
    session(14, 19, 30, null, PlanKind.STUDY);
    session(15, 19, 60, null, PlanKind.STUDY);
    session(16, 19, 60, null, PlanKind.STUDY);

    RhythmStatsResponse response = service.getRhythm();

    assertThat(response.collapse().commonDay()).isEqualTo(3);
    assertThat(response.collapse().events())
        .containsExactly(new CollapseCycle.CollapseEvent(LocalDate.of(2026, 10, 14), 3));
    assertThat(response.recovery().averageDays()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("시험 날짜와 '약속·행사' 이유(체크인은 하루 전, 타이머는 그날)를 이벤트로 넘긴다")
  void collectsExamAndAppointmentEvents() {
    // 매일 60분 계획에 60분 공부 (10/1 ~ 10/16)
    for (int day = 1; day <= 16; day++) {
      plan(day);
      session(day, 19, 60, null, PlanKind.STUDY);
    }
    // 시험 10/10 → 다음 날 10/11
    exams.add(new Exam(USER_ID, "중간고사", ExamType.SCHOOL, LocalDate.of(2026, 10, 10)));
    // 10/6 체크인 "어제 못 채운 이유: 약속·행사" → 10/5 의 약속 → 다음 날 10/6
    checkIn(6, MissReason.EVENT);
    // 10/12 타이머를 멈추며 '약속·행사' → 10/12 의 약속 → 다음 날 10/13
    session(12, 21, 10, MissReason.EVENT, PlanKind.STUDY);
    // 노는 기록의 이유는 이벤트가 아님 (10/14 → 10/15 가 들어가면 안 됨)
    session(14, 21, 10, MissReason.EVENT, PlanKind.PLAY);

    RhythmStatsResponse.AfterEvents afterEvents = service.getRhythm().afterEvents();

    assertThat(afterEvents.byEvent())
        .extracting(RhythmStatsResponse.EventItem::event)
        .containsExactly("exam", "event");
    assertThat(afterEvents.byEvent().get(0).nextDays().dates())
        .containsExactly(LocalDate.of(2026, 10, 11));
    assertThat(afterEvents.byEvent().get(1).nextDays().dates())
        .containsExactly(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 13));
  }

  @Test
  @DisplayName("요일에는 이름표가 붙고, 시간대 효율에는 노는 기록이 들어가지 않는다")
  void weekdayLabelsAndStudyOnlyTimeOfDay() {
    plan(16);
    // 10/16(금) 밤 11시 공부 30분, 같은 날 밤 10시 노는 기록 60분
    session(16, 23, 30, null, PlanKind.STUDY);
    session(16, 22, 60, null, PlanKind.PLAY);

    RhythmStatsResponse response = service.getRhythm();

    assertThat(response.byWeekday())
        .extracting(RhythmStatsResponse.WeekdayItem::weekday)
        .containsExactly("mon", "tue", "wed", "thu", "fri", "sat", "sun");
    RhythmStatsResponse.TimeOfDayItem night = response.byTimeOfDay().get(3);
    assertThat(night.timeOfDay()).isEqualTo("night");
    assertThat(night.sessionCount()).isEqualTo(1);
    assertThat(night.averageSessionMinutes()).isEqualTo(30);
  }

  @Test
  @DisplayName("오늘은 아직 안 끝나서 넣지 않는다")
  void todayIsNotCounted() {
    plan(17);
    session(17, 10, 10, null, PlanKind.STUDY);

    RhythmStatsResponse response = service.getRhythm();

    // 오늘(10/17) 은 기간 밖이라 어느 요일에도 근거 날짜가 없습니다.
    assertThat(response.byWeekday()).allSatisfy(item -> assertThat(item.dates()).isEmpty());
  }

  @Test
  @DisplayName("로그인하지 않았으면 401 이고, DB 를 보지 않는다")
  void unauthorized() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    assertThatThrownBy(service::getRhythm)
        .isInstanceOf(ApiException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.UNAUTHORIZED);
    verifyNoInteractions(
        studyPlanRepository, studySessionRepository, checkInRepository, examRepository);
  }
}
