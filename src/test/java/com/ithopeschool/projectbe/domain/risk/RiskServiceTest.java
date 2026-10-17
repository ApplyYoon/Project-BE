package com.ithopeschool.projectbe.domain.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.dailysignal.DailySignal;
import com.ithopeschool.projectbe.domain.dailysignal.DailySignalRepository;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.risk.dto.RiskForecastListResponse;
import com.ithopeschool.projectbe.domain.risk.dto.RiskForecastRequest;
import com.ithopeschool.projectbe.domain.risk.dto.RiskForecastResponse;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

// Service 단위 테스트: DB(Repository)를 가짜로 바꿔서, 날짜 검사 · 만들기/고치기/확정 · 맞았는지 채우기를 확인합니다.
// 위험을 계산하는 규칙 자체는 RiskRuleTest 가 확인합니다.
//
// 지금은 2026-10-11(일) 오후 6시(한국 시간)입니다. 오늘 10/11 · 내일 10/12(월요일)
// 재료 기간은 어제(10/10)까지 56일이라 8/16 ~ 10/10 이고, 그 안에 월요일이 8번(8/17 ~ 10/5) 있습니다.
class RiskServiceTest {

  private static final Long USER_ID = 7L;
  private static final Instant NOW = Instant.parse("2026-10-11T09:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);
  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
  private static final long MINUTE = 60_000L;

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final RiskForecastRepository riskForecastRepository = mock(RiskForecastRepository.class);
  private final StudyPlanRepository studyPlanRepository = mock(StudyPlanRepository.class);
  private final StudySessionRepository studySessionRepository = mock(StudySessionRepository.class);
  private final DailySignalRepository dailySignalRepository = mock(DailySignalRepository.class);
  private final RiskService service = serviceAt(NOW);

  // 가짜 DB 에 넣을 것들. 테스트마다 채웁니다.
  private final List<StudyPlan> plans = new ArrayList<>();
  private final List<StudySession> sessions = new ArrayList<>();
  private final List<DailySignal> signals = new ArrayList<>();

  private RiskService serviceAt(Instant now) {
    return new RiskService(
        currentUserProvider,
        riskForecastRepository,
        studyPlanRepository,
        studySessionRepository,
        dailySignalRepository,
        Clock.fixed(now, ZoneOffset.UTC));
  }

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(
            studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
                any(), any(), any()))
        .willReturn(plans);
    given(studySessionRepository.findStartedBetween(any(), any(), any())).willReturn(sessions);
    given(
            dailySignalRepository.findByUserIdAndSignalDateBetweenOrderBySignalDateAsc(
                any(), any(), any()))
        .willReturn(signals);
    // 기본은 "그날 예측이 아직 없다". 저장하면 받은 것을 그대로 돌려준다.
    given(riskForecastRepository.findByUserIdAndForecastDate(any(), any()))
        .willReturn(Optional.empty());
    given(riskForecastRepository.saveAndFlush(any()))
        .willAnswer(invocation -> invocation.getArgument(0));
  }

  // date 의 60분 공부 계획 하나와, minutes 분 공부한 기록 하나 (한국 시간 저녁 7시 시작)
  private void day(LocalDate date, int studyMinutes) {
    plans.add(new StudyPlan(USER_ID, 1L, date, "수학", 60, null, PlanKind.STUDY));
    Instant start = date.atTime(19, 0).toInstant(ZoneOffset.ofHours(9));
    sessions.add(
        new StudySession(
            USER_ID,
            1L,
            start,
            start.plusMillis(studyMinutes * MINUTE),
            0,
            0,
            null,
            null,
            null,
            PlanKind.STUDY));
  }

  // 8/16 ~ 10/10 의 모든 날에 계획이 있고, 월요일은 30분(50%) · 나머지는 60분(100%) 공부한 기록
  // → 월요일 평균 50%, 전체 평균 (8 × 50 + 48 × 100) / 56 = 92.9% → 차이 42.9%p 라서 약한 요일
  private void weakMondays() {
    for (LocalDate date = LocalDate.of(2026, 8, 16);
        !date.isAfter(LocalDate.of(2026, 10, 10));
        date = date.plusDays(1)) {
      day(date, date.getDayOfWeek() == DayOfWeek.MONDAY ? 30 : 60);
    }
  }

  // 월요일(10/12) 새벽 2시(한국 시간)에 잠든 하루 신호 = 늦게 잠
  private void lateSleep() {
    Instant sleptAt = Instant.parse("2026-10-11T17:00:00Z");
    signals.add(new DailySignal(USER_ID, MONDAY, 360, sleptAt, null, null, null));
  }

  private RiskForecast savedForecast() {
    ArgumentCaptor<RiskForecast> captor = ArgumentCaptor.forClass(RiskForecast.class);
    verify(riskForecastRepository).saveAndFlush(captor.capture());
    return captor.getValue();
  }

  private static void assertError(Runnable action, ErrorCode code, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(code);
  }

  // ─── 예측하기 ───

  @Test
  @DisplayName("약한 요일만 켜지면 WATCH 로 새 줄을 만든다 (수면 기록이 없는 학생)")
  void createsWatchForWeakWeekday() {
    weakMondays();

    RiskForecastResponse response = service.forecast(new RiskForecastRequest(MONDAY));

    RiskForecast saved = savedForecast();
    assertThat(saved.getUserId()).isEqualTo(USER_ID);
    assertThat(saved.getForecastDate()).isEqualTo(MONDAY);
    assertThat(saved.getRiskLevel()).isEqualTo(RiskLevel.WATCH);
    assertThat(saved.getReasons()).containsExactly(RiskReason.WEAK_WEEKDAY);
    assertThat(saved.getActualAchievementRate()).isNull();
    assertThat(saved.getWasCorrect()).isNull();
    assertThat(response.riskLevel()).isEqualTo(RiskLevel.WATCH);
    assertThat(response.reasons())
        .extracting(RiskForecastResponse.Reason::label)
        .containsExactly("약한 요일");
  }

  @Test
  @DisplayName("약한 요일 + 늦게 잠이면 HIGH, 이유는 둘 다 담긴다")
  void createsHighForBothReasons() {
    weakMondays();
    lateSleep();

    RiskForecastResponse response = service.forecast(new RiskForecastRequest(MONDAY));

    assertThat(savedForecast().getReasons())
        .containsExactly(RiskReason.WEAK_WEEKDAY, RiskReason.LATE_SLEEP);
    assertThat(response.riskLevel()).isEqualTo(RiskLevel.HIGH);
    assertThat(response.reasons()).hasSize(2);
  }

  @Test
  @DisplayName("기록이 하나도 없어도 위험 없음(NONE)으로 한 줄을 남긴다")
  void recordsNoneToo() {
    RiskForecastResponse response = service.forecast(new RiskForecastRequest(MONDAY));

    RiskForecast saved = savedForecast();
    assertThat(saved.getRiskLevel()).isEqualTo(RiskLevel.NONE);
    assertThat(saved.getReasons()).isEmpty();
    assertThat(response.riskLevel()).isEqualTo(RiskLevel.NONE);
    assertThat(response.reasons()).isEmpty();
  }

  @Test
  @DisplayName("오늘도 예측할 수 있다")
  void canForecastToday() {
    service.forecast(new RiskForecastRequest(TODAY));

    assertThat(savedForecast().getForecastDate()).isEqualTo(TODAY);
  }

  @Test
  @DisplayName("재료는 어제까지 56일이다: 계획 · 공부 기록을 그 기간으로 찾는다 (오늘은 넣지 않는다)")
  void usesTheWindowEndingYesterday() {
    service.forecast(new RiskForecastRequest(MONDAY));

    verify(studyPlanRepository)
        .findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
            USER_ID, LocalDate.of(2026, 8, 16), LocalDate.of(2026, 10, 10));
    // 첫날 04:00 ~ 오늘(10/11) 04:00 직전. 한국 시간 새벽 4시 = 전날 19:00Z
    verify(studySessionRepository)
        .findStartedBetween(
            USER_ID, Instant.parse("2026-08-15T19:00:00Z"), Instant.parse("2026-10-10T19:00:00Z"));
    // 수면은 그날의 하루 전과 당일 날짜의 것을 찾는다
    verify(dailySignalRepository)
        .findByUserIdAndSignalDateBetweenOrderBySignalDateAsc(USER_ID, TODAY, MONDAY);
  }

  @Test
  @DisplayName("다시 예측하면 새 줄을 만들지 않고 그 줄의 위험 수준과 이유를 최신으로 고친다")
  void renewsTheSameDay() {
    RiskForecast old =
        new RiskForecast(
            USER_ID,
            MONDAY,
            RiskLevel.HIGH,
            EnumSet.of(RiskReason.WEAK_WEEKDAY, RiskReason.LATE_SLEEP));
    given(riskForecastRepository.findByUserIdAndForecastDate(USER_ID, MONDAY))
        .willReturn(Optional.of(old));
    // 이번에는 약한 요일도 아니고 수면 기록도 없다 → 위험 없음

    RiskForecastResponse response = service.forecast(new RiskForecastRequest(MONDAY));

    verify(riskForecastRepository, never()).saveAndFlush(any());
    assertThat(old.getRiskLevel()).isEqualTo(RiskLevel.NONE);
    assertThat(old.getReasons()).isEmpty();
    assertThat(response.riskLevel()).isEqualTo(RiskLevel.NONE);
  }

  @Test
  @DisplayName("실제 달성률이 이미 채워진 줄은 확정이라, 다시 예측해도 고치지 않고 그대로 돌려준다")
  void confirmedForecastIsNotChanged() {
    RiskForecast confirmed =
        new RiskForecast(USER_ID, MONDAY, RiskLevel.HIGH, EnumSet.of(RiskReason.LATE_SLEEP));
    confirmed.confirm(30, true);
    given(riskForecastRepository.findByUserIdAndForecastDate(USER_ID, MONDAY))
        .willReturn(Optional.of(confirmed));

    RiskForecastResponse response = service.forecast(new RiskForecastRequest(MONDAY));

    assertThat(confirmed.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
    assertThat(response.riskLevel()).isEqualTo(RiskLevel.HIGH);
    assertThat(response.actualAchievementRate()).isEqualTo(30);
    assertThat(response.wasCorrect()).isTrue();
    // 계산도 하지 않는다 (재료를 읽지 않음)
    verify(studyPlanRepository, never())
        .findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(any(), any(), any());
    verify(riskForecastRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("지난 날이나 모레 이후는 예측할 수 없다 (400)")
  void onlyTodayAndTomorrow() {
    LocalDate yesterday = TODAY.minusDays(1);
    LocalDate dayAfterTomorrow = TODAY.plusDays(2);

    assertError(
        () -> service.forecast(new RiskForecastRequest(yesterday)),
        ErrorCode.VALIDATION_ERROR,
        "위험 예측은 오늘이나 내일만 할 수 있어요");
    assertError(
        () -> service.forecast(new RiskForecastRequest(dayAfterTomorrow)),
        ErrorCode.VALIDATION_ERROR,
        "위험 예측은 오늘이나 내일만 할 수 있어요");
    verify(riskForecastRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("새벽 4시 전에는 아직 어제다: 일요일 새벽 3시 30분의 \"오늘\" 은 토요일이다")
  void todayChangesAtFourAm() {
    // 2026-10-11 03:30 (한국 시간) = 10/10 18:30Z → 오늘은 10/10
    RiskService beforeFour = serviceAt(Instant.parse("2026-10-10T18:30:00Z"));

    assertError(
        () -> beforeFour.forecast(new RiskForecastRequest(LocalDate.of(2026, 10, 12))),
        ErrorCode.VALIDATION_ERROR,
        "위험 예측은 오늘이나 내일만 할 수 있어요");
    beforeFour.forecast(new RiskForecastRequest(LocalDate.of(2026, 10, 11)));
    assertThat(savedForecast().getForecastDate()).isEqualTo(LocalDate.of(2026, 10, 11));
  }

  @Test
  @DisplayName("동시에 같은 날이 들어와 DB 가 겹침을 막으면 500 이 아니라 409 로 알린다")
  void concurrentInsertIsConflict() {
    given(riskForecastRepository.saveAndFlush(any()))
        .willThrow(new DataIntegrityViolationException("uk_risk_forecasts_user_id_forecast_date"));

    assertError(
        () -> service.forecast(new RiskForecastRequest(MONDAY)),
        ErrorCode.CONFLICT,
        "같은 날의 예측이 동시에 들어왔어요. 잠시 후 다시 불러 주세요");
  }

  @Test
  @DisplayName("로그인하지 않았으면 아무것도 읽거나 저장하지 않고 에러가 난다")
  void needsLogin() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    assertThatThrownBy(() -> service.forecast(new RiskForecastRequest(MONDAY)))
        .isInstanceOf(ApiException.class);
    verifyNoInteractions(riskForecastRepository, studyPlanRepository, dailySignalRepository);
  }

  // ─── 맞았는지 채우기 ───

  @Test
  @DisplayName("새로 예측할 때 내 지난 예측 중 비어 있는 것도 함께 채운다 (오늘 이전, 최근 14일)")
  void forecastAlsoConfirmsMyPastOnes() {
    // 10/9(금)에 "주의" 라고 예측, 그날 계획 60분 중 30분 공부 → 50% → 못 채운 날 → 맞음
    RiskForecast past =
        new RiskForecast(
            USER_ID,
            LocalDate.of(2026, 10, 9),
            RiskLevel.WATCH,
            EnumSet.of(RiskReason.WEAK_WEEKDAY));
    day(LocalDate.of(2026, 10, 9), 30);
    given(
            riskForecastRepository.findByUserIdAndActualAchievementRateIsNullAndForecastDateBetween(
                USER_ID, LocalDate.of(2026, 9, 27), LocalDate.of(2026, 10, 10)))
        .willReturn(List.of(past));

    service.forecast(new RiskForecastRequest(MONDAY));

    assertThat(past.getActualAchievementRate()).isEqualTo(50);
    assertThat(past.getWasCorrect()).isTrue();
  }

  @Test
  @DisplayName("그날 계획이 없어서 달성률을 구할 수 없으면 비워 둔다 (0 으로 채우지 않는다)")
  void noPlanDayStaysEmpty() {
    RiskForecast noPlanDay =
        new RiskForecast(
            USER_ID, LocalDate.of(2026, 10, 9), RiskLevel.NONE, EnumSet.noneOf(RiskReason.class));
    given(
            riskForecastRepository.findByUserIdAndActualAchievementRateIsNullAndForecastDateBetween(
                any(), any(), any()))
        .willReturn(List.of(noPlanDay));

    int confirmed = service.confirmPast(USER_ID, TODAY);

    assertThat(confirmed).isZero();
    assertThat(noPlanDay.getActualAchievementRate()).isNull();
    assertThat(noPlanDay.getWasCorrect()).isNull();
  }

  @Test
  @DisplayName("위험 없음이라 했고 계획을 다 채웠으면 맞다")
  void noneAndFullDayIsCorrect() {
    RiskForecast none =
        new RiskForecast(
            USER_ID, LocalDate.of(2026, 10, 9), RiskLevel.NONE, EnumSet.noneOf(RiskReason.class));
    day(LocalDate.of(2026, 10, 9), 60);
    given(
            riskForecastRepository.findByUserIdAndActualAchievementRateIsNullAndForecastDateBetween(
                any(), any(), any()))
        .willReturn(List.of(none));

    int confirmed = service.confirmPast(USER_ID, TODAY);

    assertThat(confirmed).isEqualTo(1);
    assertThat(none.getActualAchievementRate()).isEqualTo(100);
    assertThat(none.getWasCorrect()).isTrue();
  }

  @Test
  @DisplayName("모든 학생의 비어 있는 예측을 학생별 기록으로 채운다 (예약 작업)")
  void confirmAllUsesEachUsersOwnRecords() {
    RiskForecast mine =
        new RiskForecast(
            USER_ID, LocalDate.of(2026, 10, 9), RiskLevel.HIGH, EnumSet.of(RiskReason.LATE_SLEEP));
    RiskForecast other =
        new RiskForecast(
            8L, LocalDate.of(2026, 10, 9), RiskLevel.NONE, EnumSet.noneOf(RiskReason.class));
    given(
            riskForecastRepository.findByActualAchievementRateIsNullAndForecastDateBetween(
                LocalDate.of(2026, 9, 27), LocalDate.of(2026, 10, 10)))
        .willReturn(List.of(mine, other));
    // 7번 학생: 계획 60분 중 60분 → 100%  /  8번 학생: 계획 60분 중 0분 → 0%
    LocalDate date = LocalDate.of(2026, 10, 9);
    given(
            studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
                eq(USER_ID), any(), any()))
        .willReturn(List.of(new StudyPlan(USER_ID, 1L, date, "수학", 60, null, PlanKind.STUDY)));
    given(
            studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
                eq(8L), any(), any()))
        .willReturn(List.of(new StudyPlan(8L, 1L, date, "수학", 60, null, PlanKind.STUDY)));
    Instant start = date.atTime(19, 0).toInstant(ZoneOffset.ofHours(9));
    given(studySessionRepository.findStartedBetween(eq(USER_ID), any(), any()))
        .willReturn(
            List.of(
                new StudySession(
                    USER_ID,
                    1L,
                    start,
                    start.plusMillis(60 * MINUTE),
                    0,
                    0,
                    null,
                    null,
                    null,
                    PlanKind.STUDY)));
    given(studySessionRepository.findStartedBetween(eq(8L), any(), any())).willReturn(List.of());

    int confirmed = service.confirmAll(TODAY);

    assertThat(confirmed).isEqualTo(2);
    // 7번: 위험하다고 했는데 100% 채움 → 틀림
    assertThat(mine.getActualAchievementRate()).isEqualTo(100);
    assertThat(mine.getWasCorrect()).isFalse();
    // 8번: 위험 없다고 했는데 0% → 틀림
    assertThat(other.getActualAchievementRate()).isZero();
    assertThat(other.getWasCorrect()).isFalse();
  }

  @Test
  @DisplayName("채울 예측이 없으면 계획·공부 기록을 읽지도 않고 0 을 돌려준다")
  void confirmAllWithNothingPending() {
    assertThat(service.confirmAll(TODAY)).isZero();

    verifyNoInteractions(studyPlanRepository, studySessionRepository);
  }

  // ─── 통화·목표 조정에 넘길 위험 수준 ───

  @Test
  @DisplayName("levelOn 은 지금 기록으로 위험 수준을 계산하기만 하고 아무것도 저장하지 않는다")
  void levelOnDoesNotSave() {
    weakMondays();
    lateSleep();

    assertThat(service.levelOn(USER_ID, MONDAY)).isEqualTo(RiskLevel.HIGH);

    verify(riskForecastRepository, never()).saveAndFlush(any());
    verify(riskForecastRepository, never()).save(any());
  }

  // ─── 보기 ───

  @Test
  @DisplayName("기록된 예측을 하나 보여 주고, 없으면 404 이다")
  void getOneOrNotFound() {
    RiskForecast forecast =
        new RiskForecast(USER_ID, MONDAY, RiskLevel.WATCH, EnumSet.of(RiskReason.LATE_SLEEP));
    given(riskForecastRepository.findByUserIdAndForecastDate(USER_ID, MONDAY))
        .willReturn(Optional.of(forecast));

    assertThat(service.get(MONDAY).riskLevel()).isEqualTo(RiskLevel.WATCH);
    assertError(() -> service.get(TODAY), ErrorCode.NOT_FOUND, "그날의 위험 예측이 없어요");
  }

  @Test
  @DisplayName("목록은 기간 안의 내 예측과 맞은 개수를 센다 (확인 안 된 것은 세지 않는다)")
  void listCountsCorrectOnes() {
    RiskForecast right =
        new RiskForecast(
            USER_ID, LocalDate.of(2026, 10, 5), RiskLevel.HIGH, EnumSet.of(RiskReason.LATE_SLEEP));
    right.confirm(40, true);
    RiskForecast wrong =
        new RiskForecast(
            USER_ID, LocalDate.of(2026, 10, 6), RiskLevel.NONE, EnumSet.noneOf(RiskReason.class));
    wrong.confirm(20, true);
    RiskForecast pending =
        new RiskForecast(USER_ID, TODAY, RiskLevel.NONE, EnumSet.noneOf(RiskReason.class));
    given(
            riskForecastRepository.findByUserIdAndForecastDateBetweenOrderByForecastDateAsc(
                USER_ID, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
        .willReturn(List.of(right, wrong, pending));

    RiskForecastListResponse response =
        service.list(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

    assertThat(response.forecasts()).hasSize(3);
    assertThat(response.confirmedCount()).isEqualTo(2);
    assertThat(response.correctCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("목록의 기간은 끝이 시작보다 앞이면 400, 62일을 넘으면 400 이다 (62일까지는 된다)")
  void listChecksThePeriod() {
    LocalDate from = LocalDate.of(2026, 8, 1);

    assertError(
        () -> service.list(from, from.minusDays(1)),
        ErrorCode.VALIDATION_ERROR,
        "끝 날짜는 시작 날짜와 같거나 뒤여야 해요");
    assertError(
        () -> service.list(from, from.plusDays(62)),
        ErrorCode.VALIDATION_ERROR,
        "한 번에 62일까지만 볼 수 있어요");
    // 양 끝을 포함해 정확히 62일은 된다
    assertThat(service.list(from, from.plusDays(61)).forecasts()).isEmpty();
  }
}
