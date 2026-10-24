package com.ithopeschool.projectbe.domain.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.care.Stage4Service;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.dto.PlanActualResponse;
import com.ithopeschool.projectbe.domain.session.dto.StudySessionResponse;
import com.ithopeschool.projectbe.domain.session.dto.StudyTotalResponse;
import com.ithopeschool.projectbe.domain.session.dto.StudyTotalResponse.SubjectTotalResponse;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// 공부 기록 "조회" 규칙(이슈 #14)을 하나씩 확인합니다. DB 는 가짜(Repository 를 흉내 낸 것)로 바꾸고
// "어떤 기간으로 물었는지" 와 "받은 기록으로 무엇을 계산했는지" 를 봅니다.
// 진짜 DB 로 "자정을 넘긴 기록이 오늘로 잡히는지" 확인하는 것은 StudySessionQueryFlowTest 가 합니다.
class StudySessionQueryServiceTest {

  private static final Long ME = 1L;
  private static final long MINUTE = 60_000;

  private CurrentUserProvider currentUserProvider;
  private StudySessionRepository studySessionRepository;
  private StudyPlanRepository studyPlanRepository;

  @BeforeEach
  void setUp() {
    currentUserProvider = mock(CurrentUserProvider.class);
    studySessionRepository = mock(StudySessionRepository.class);
    studyPlanRepository = mock(StudyPlanRepository.class);
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    given(studySessionRepository.findStartedBetween(any(), any(), any())).willReturn(List.of());
    given(studySessionRepository.findByUserIdAndPlanIdIn(any(), any())).willReturn(List.of());
  }

  // 시계가 now 를 가리키는 Service 를 만듭니다. ("오늘" 이 언제인지 정하려고)
  private StudySessionService serviceAt(Instant now) {
    return new StudySessionService(
        currentUserProvider,
        studySessionRepository,
        mock(SubjectRepository.class),
        studyPlanRepository,
        mock(CardRepository.class),
        mock(Stage4Service.class),
        Clock.fixed(now, ZoneOffset.UTC));
  }

  private StudySessionService service() {
    return serviceAt(Instant.parse("2026-10-20T03:00:00Z"));
  }

  // 한국 시간을 순간(Instant)으로 바꾸는 도우미입니다.
  private static Instant kst(int year, int month, int day, int hour, int minute, int second) {
    return ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZoneId.of("Asia/Seoul"))
        .toInstant();
  }

  private static Instant kst(int month, int day, int hour, int minute) {
    return kst(2026, month, day, hour, minute, 0);
  }

  private static LocalDate date(int month, int day) {
    return LocalDate.of(2026, month, day);
  }

  private static StudySession session(
      Long subjectId,
      PlanKind kind,
      Instant start,
      long durationMillis,
      long pausedMs,
      Long planId) {
    return new StudySession(
        ME,
        subjectId,
        start,
        start.plusMillis(durationMillis),
        0,
        pausedMs,
        planId,
        null,
        null,
        kind);
  }

  private static StudySession study(Long subjectId, Instant start, long durationMillis) {
    return session(subjectId, PlanKind.STUDY, start, durationMillis, 0, null);
  }

  private static void assertApiError(Runnable call, ErrorCode code, String message) {
    assertThatThrownBy(call::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(code);
  }

  // ---- 기간별 목록 ----

  @Test
  @DisplayName("기간은 from 날의 새벽 4시부터 to 다음 날 새벽 4시 직전까지로 묻는다 (하루의 기준)")
  void periodUsesDayBoundary() {
    service().getSessions(date(10, 1), date(10, 7));

    // 10월 1일 04:00(한국) = 9월 30일 19:00Z, 10월 8일 04:00(한국) = 10월 7일 19:00Z
    verify(studySessionRepository)
        .findStartedBetween(
            ME, Instant.parse("2026-09-30T19:00:00Z"), Instant.parse("2026-10-07T19:00:00Z"));
  }

  @Test
  @DisplayName("같은 날 하루만 묻을 수도 있다 (from 과 to 가 같음)")
  void oneDayPeriod() {
    service().getSessions(date(10, 5), date(10, 5));

    verify(studySessionRepository)
        .findStartedBetween(
            ME, Instant.parse("2026-10-04T19:00:00Z"), Instant.parse("2026-10-05T19:00:00Z"));
  }

  @Test
  @DisplayName("찾은 기록을 FE StudySession 모양으로 바꿔 돌려준다 (일시정지·계획·목표·이유·종류 포함)")
  void mapsSessions() {
    StudySession withAll =
        new StudySession(
            ME,
            3L,
            Instant.ofEpochMilli(1_790_000_000_000L),
            Instant.ofEpochMilli(1_790_003_000_000L),
            2,
            300_000L,
            7L,
            50,
            MissReason.PHONE,
            PlanKind.STUDY);
    ReflectionTestUtils.setField(withAll, "id", 12L);
    StudySession play =
        session(
            null, PlanKind.PLAY, Instant.ofEpochMilli(1_790_010_000_000L), 30 * MINUTE, 0, null);
    ReflectionTestUtils.setField(play, "id", 13L);
    given(studySessionRepository.findStartedBetween(any(), any(), any()))
        .willReturn(List.of(withAll, play));

    List<StudySessionResponse> result = service().getSessions(date(10, 1), date(10, 7));

    assertThat(result)
        .containsExactly(
            new StudySessionResponse(
                "12",
                "3",
                1_790_000_000_000L,
                1_790_003_000_000L,
                2,
                300_000L,
                "7",
                50,
                "phone",
                "study"),
            new StudySessionResponse(
                "13",
                "",
                1_790_010_000_000L,
                1_790_010_000_000L + 30 * MINUTE,
                0,
                0,
                null,
                null,
                null,
                "play"));
  }

  @Test
  @DisplayName("기록이 없는 기간은 빈 목록이다")
  void emptyPeriod() {
    assertThat(service().getSessions(date(10, 1), date(10, 7))).isEmpty();
  }

  @Test
  @DisplayName("31일(시작일·끝일 포함)까지는 된다")
  void thirtyOneDaysIsAllowed() {
    service().getSessions(date(10, 1), date(10, 31));

    verify(studySessionRepository).findStartedBetween(any(), any(), any());
  }

  @Test
  @DisplayName("31일보다 길면 400 이고 DB 에는 묻지 않는다")
  void thirtyTwoDaysIsRejected() {
    assertApiError(
        () -> service().getSessions(date(10, 1), date(11, 1)),
        ErrorCode.VALIDATION_ERROR,
        "한 번에 31일까지만 조회할 수 있어요");
    verify(studySessionRepository, never()).findStartedBetween(any(), any(), any());
  }

  @Test
  @DisplayName("시작 날짜가 끝 날짜보다 늦으면 400")
  void fromAfterTo() {
    assertApiError(
        () -> service().getSessions(date(10, 7), date(10, 1)),
        ErrorCode.VALIDATION_ERROR,
        "시작 날짜는 끝 날짜보다 늦을 수 없어요");
    verify(studySessionRepository, never()).findStartedBetween(any(), any(), any());
  }

  @Test
  @DisplayName("터무니없이 먼 날짜는 400 이다 (DB 까지 가서 500 이 나면 안 됨)")
  void farAwayDatesAreRejected() {
    String message = "날짜는 1970-01-01 부터 9999-12-31 사이로 보내 주세요";

    assertApiError(
        () -> service().getSessions(LocalDate.of(1969, 12, 31), LocalDate.of(1970, 1, 1)),
        ErrorCode.VALIDATION_ERROR,
        message);
    assertApiError(
        () -> service().getSessions(LocalDate.MIN, LocalDate.MIN),
        ErrorCode.VALIDATION_ERROR,
        message);
    assertApiError(
        () -> service().getSessions(LocalDate.of(9999, 12, 31), LocalDate.MAX),
        ErrorCode.VALIDATION_ERROR,
        message);
    verify(studySessionRepository, never()).findStartedBetween(any(), any(), any());
  }

  @Test
  @DisplayName("범위의 양 끝 날짜(1970-01-01, 9999-12-31)는 된다")
  void edgeDatesAreAllowed() {
    service().getSessions(LocalDate.of(1970, 1, 1), LocalDate.of(1970, 1, 1));
    service().getSessions(LocalDate.of(9999, 12, 31), LocalDate.of(9999, 12, 31));

    verify(studySessionRepository, times(2)).findStartedBetween(any(), any(), any());
  }

  // ---- 오늘 순공시간 ----

  @Test
  @DisplayName("오늘은 하루의 기준(새벽 4시)을 따른다: 새벽 1시 30분이면 아직 어제 날짜다")
  void todayFollowsDayRuleAfterMidnight() {
    // 10월 6일 01:30(한국) → 오늘은 10월 5일. 찾는 기간은 10월 5일 04:00 ~ 10월 6일 04:00(한국)
    StudyTotalResponse result = serviceAt(kst(10, 6, 1, 30)).getTodayTotal();

    assertThat(result.date()).isEqualTo(date(10, 5));
    verify(studySessionRepository)
        .findStartedBetween(
            ME, Instant.parse("2026-10-04T19:00:00Z"), Instant.parse("2026-10-05T19:00:00Z"));
  }

  @Test
  @DisplayName("오늘이 바뀌는 순간: 03:59:59 는 어제, 04:00:00 은 오늘")
  void todayChangesAtFourOClock() {
    assertThat(serviceAt(kst(2026, 10, 6, 3, 59, 59)).getTodayTotal().date())
        .isEqualTo(date(10, 5));
    assertThat(serviceAt(kst(2026, 10, 6, 4, 0, 0)).getTodayTotal().date()).isEqualTo(date(10, 6));
  }

  @Test
  @DisplayName("한낮에는 그냥 그날 날짜다 (04:00 ~ 다음 날 04:00)")
  void todayInTheDaytime() {
    StudyTotalResponse result = serviceAt(kst(10, 6, 15, 0)).getTodayTotal();

    assertThat(result.date()).isEqualTo(date(10, 6));
    verify(studySessionRepository)
        .findStartedBetween(
            ME, Instant.parse("2026-10-05T19:00:00Z"), Instant.parse("2026-10-06T19:00:00Z"));
  }

  @Test
  @DisplayName("오늘 기록이 없으면 0분이고 과목 목록은 빈 배열이다")
  void noRecordsToday() {
    StudyTotalResponse result = serviceAt(kst(10, 6, 15, 0)).getTodayTotal();

    assertThat(result.studyMinutes()).isZero();
    assertThat(result.subjects()).isEmpty();
  }

  @Test
  @DisplayName("순공은 일시정지를 뺀 시간이고, 노는 기록은 빠지고, 과목별로 나눠 준다")
  void todayTotalAndSubjects() {
    Instant start = kst(10, 6, 10, 0);
    given(studySessionRepository.findStartedBetween(any(), any(), any()))
        .willReturn(
            List.of(
                // 수학 19:00 ~ 20:10, 40분 멈춤 → 30분 (FE 예시)
                session(3L, PlanKind.STUDY, kst(10, 6, 19, 0), 70 * MINUTE, 40 * MINUTE, null),
                // 영어 45분
                study(4L, start, 45 * MINUTE),
                // 수학 20분
                study(3L, kst(10, 6, 12, 0), 20 * MINUTE),
                // 노는 기록 60분은 빠짐
                session(null, PlanKind.PLAY, kst(10, 6, 14, 0), 60 * MINUTE, 0, null)));

    StudyTotalResponse result = serviceAt(kst(10, 6, 23, 0)).getTodayTotal();

    assertThat(result.studyMinutes()).isEqualTo(95);
    assertThat(result.subjects())
        .containsExactly(new SubjectTotalResponse("3", 50), new SubjectTotalResponse("4", 45));
  }

  // ---- 계획별 실제 시간 ----

  // 번호가 정해진 계획을 만듭니다. (실제로는 DB 가 번호를 매깁니다)
  private static StudyPlan plan(Long id, PlanKind kind, int targetMinutes) {
    StudyPlan plan =
        new StudyPlan(
            ME,
            kind == PlanKind.PLAY ? null : 3L,
            date(10, 5),
            "계획 " + id,
            targetMinutes,
            null,
            kind);
    ReflectionTestUtils.setField(plan, "id", id);
    return plan;
  }

  @Test
  @DisplayName("계획마다 그 계획으로 켠 기록의 순공을 더해 실제 시간·진행률을 돌려준다")
  void planActuals() {
    given(studyPlanRepository.findByUserIdAndPlanDateOrderById(ME, date(10, 5)))
        .willReturn(
            List.of(
                plan(7L, PlanKind.STUDY, 50), // 20분 + 30분 = 50분 → 채움
                plan(8L, PlanKind.STUDY, 50), // 32분 → 64%
                plan(9L, PlanKind.STUDY, 50), // 49분 59초 → 49분, 99%
                plan(10L, PlanKind.STUDY, 60))); // 기록 없음
    Instant t = kst(10, 5, 9, 0);
    given(studySessionRepository.findByUserIdAndPlanIdIn(any(), any()))
        .willReturn(
            List.of(
                session(3L, PlanKind.STUDY, t, 20 * MINUTE, 0, 7L),
                session(3L, PlanKind.STUDY, t.plusSeconds(3600), 30 * MINUTE, 0, 7L),
                session(3L, PlanKind.STUDY, t.plusSeconds(7200), 32 * MINUTE, 0, 8L),
                session(3L, PlanKind.STUDY, t.plusSeconds(10800), 49 * MINUTE + 59_000, 0, 9L)));

    List<PlanActualResponse> result = service().getPlanActuals(date(10, 5));

    assertThat(result)
        .containsExactly(
            new PlanActualResponse("7", 50, 100, true, 0),
            new PlanActualResponse("8", 32, 64, false, 18),
            new PlanActualResponse("9", 49, 99, false, 1),
            new PlanActualResponse("10", 0, 0, false, 60));
  }

  @Test
  @DisplayName("실제 시간은 일시정지한 시간을 뺀 값이다 (70분 중 40분 멈춤 → 30분)")
  void planActualSubtractsPausedTime() {
    given(studyPlanRepository.findByUserIdAndPlanDateOrderById(ME, date(10, 5)))
        .willReturn(List.of(plan(7L, PlanKind.STUDY, 50)));
    given(studySessionRepository.findByUserIdAndPlanIdIn(any(), any()))
        .willReturn(
            List.of(session(3L, PlanKind.STUDY, kst(10, 5, 19, 0), 70 * MINUTE, 40 * MINUTE, 7L)));

    assertThat(service().getPlanActuals(date(10, 5)))
        .containsExactly(new PlanActualResponse("7", 30, 60, false, 20));
  }

  @Test
  @DisplayName("노는 계획도 실제 시간을 돌려준다 (노는 기록의 길이, 계획보다 더 놀았으면 100%)")
  void playPlanActual() {
    given(studyPlanRepository.findByUserIdAndPlanDateOrderById(ME, date(10, 5)))
        .willReturn(List.of(plan(9L, PlanKind.PLAY, 60)));
    given(studySessionRepository.findByUserIdAndPlanIdIn(any(), any()))
        .willReturn(List.of(session(null, PlanKind.PLAY, kst(10, 5, 20, 0), 95 * MINUTE, 0, 9L)));

    assertThat(service().getPlanActuals(date(10, 5)))
        .containsExactly(new PlanActualResponse("9", 95, 100, true, 0));
  }

  @Test
  @DisplayName("계획이 없는 날은 빈 목록이고, 기록은 찾지도 않는다")
  void noPlans() {
    given(studyPlanRepository.findByUserIdAndPlanDateOrderById(ME, date(10, 5)))
        .willReturn(List.of());

    assertThat(service().getPlanActuals(date(10, 5))).isEmpty();
    verify(studySessionRepository, never()).findByUserIdAndPlanIdIn(any(), any());
  }

  @Test
  @DisplayName("기록은 그날 계획들의 번호로만 찾는다")
  void asksOnlyForThatDaysPlans() {
    given(studyPlanRepository.findByUserIdAndPlanDateOrderById(ME, date(10, 5)))
        .willReturn(List.of(plan(7L, PlanKind.STUDY, 50), plan(9L, PlanKind.PLAY, 30)));

    service().getPlanActuals(date(10, 5));

    verify(studySessionRepository).findByUserIdAndPlanIdIn(ME, List.of(7L, 9L));
  }

  @Test
  @DisplayName("계획 조회의 날짜가 터무니없이 멀면 400 이다")
  void planActualFarAwayDate() {
    assertApiError(
        () -> service().getPlanActuals(LocalDate.MAX),
        ErrorCode.VALIDATION_ERROR,
        "날짜는 1970-01-01 부터 9999-12-31 사이로 보내 주세요");
    verifyNoInteractions(studyPlanRepository);
  }

  // ---- 로그인 ----

  @Test
  @DisplayName("로그인하지 않았으면 세 조회 모두 401 이고, DB 는 건드리지 않는다")
  void requiresLogin() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    assertApiError(
        () -> service().getSessions(date(10, 1), date(10, 7)), ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
    assertApiError(() -> service().getTodayTotal(), ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
    assertApiError(
        () -> service().getPlanActuals(date(10, 5)), ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
    verifyNoInteractions(studySessionRepository, studyPlanRepository);
  }
}
