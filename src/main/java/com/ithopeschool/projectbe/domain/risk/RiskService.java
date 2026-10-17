package com.ithopeschool.projectbe.domain.risk;

import com.ithopeschool.projectbe.domain.checkin.CheckInQuestions;
import com.ithopeschool.projectbe.domain.dailysignal.DailySignal;
import com.ithopeschool.projectbe.domain.dailysignal.DailySignalRepository;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.risk.dto.RiskForecastListResponse;
import com.ithopeschool.projectbe.domain.risk.dto.RiskForecastRequest;
import com.ithopeschool.projectbe.domain.risk.dto.RiskForecastResponse;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.summary.DaySummary;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 위험 예측을 만들고·기록하고·보여 주고·맞았는지 확인하는 로직입니다. (이슈 #49, docs/RISK-FORECAST.md)
// 계획·공부 기록·하루 신호를 꺼내서 RiskRule 에 넘기기만 합니다. 규칙은 RiskRule 에 있습니다.
//
// 예측할 수 있는 날은 오늘과 내일뿐입니다. (오늘은 하루의 기준인 새벽 4시로 정합니다)
//   - 이미 지난 날을 "예측" 하면 결과를 알고 계산하는 것이라 막습니다.
//   - 모레 이후는 재료(어제까지의 기록)가 너무 오래돼서 막습니다.
//
// 규칙의 재료는 "어제까지" 56일의 기록입니다. 오늘은 아직 끝나지 않아서 넣지 않습니다. (리듬 분석 #37 과 같음)
// 그래서 같은 날을 몇 번 예측해도 같은 재료에서 나옵니다. 달라지는 것은 수면 기록이 늦게 들어올 때뿐입니다.
//
// 맞았는지 확인하기(confirm): 그날이 지난 뒤 그날의 달성률을 구해 예측 줄에 채웁니다. 두 곳에서 합니다.
//   1) 예약 작업 risk-confirm (RiskConfirmJob): 매일 모든 학생의 것을 채웁니다.
//   2) 내가 새로 예측을 할 때: 내 지난 예측 중 아직 안 채운 것을 함께 채웁니다. (예약 작업이 늦어도 빠지지 않게)
// 조회(GET)는 줄을 고치지 않습니다. 동의를 안 한 사람의 저장을 막는 검사(#71)가 GET 에는 걸려 있지 않기 때문입니다.
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 내 데이터만 봅니다.
// ⚠️ 수면 시각은 건강 데이터라 로그에 찍지 않고, 예측 줄에도 저장하지 않습니다. (이유 이름만 남깁니다)
@Service
@RequiredArgsConstructor
public class RiskService {

  // 기간으로 조회할 수 있는 최대 일수 (양 끝 포함)
  static final int MAX_LIST_DAYS = 62;

  // 맞았는지 못 채운 지난 예측을 이만큼(일) 전까지 거슬러 올라가 채웁니다. (오늘 제외)
  static final int CONFIRM_LOOKBACK_DAYS = 14;

  private final CurrentUserProvider currentUserProvider;
  private final RiskForecastRepository riskForecastRepository;
  private final StudyPlanRepository studyPlanRepository;
  private final StudySessionRepository studySessionRepository;
  private final DailySignalRepository dailySignalRepository;
  private final Clock clock;

  // 그날을 예측해서 기록합니다. 그날 이미 예측한 줄이 있으면 최신 결과로 고치고, 없으면 새로 만듭니다.
  // 위험 없음(NONE)도 줄을 남깁니다. 새로 만들 때도, 고칠 때도 같은 답입니다.
  @Transactional
  public RiskForecastResponse forecast(RiskForecastRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    LocalDate today = today();
    LocalDate date = request.date();
    if (date.isBefore(today) || date.isAfter(today.plusDays(1))) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "위험 예측은 오늘이나 내일만 할 수 있어요");
    }

    // 지난 예측 중 결과를 못 채운 것이 있으면 이 김에 채웁니다.
    confirmPast(userId, today);

    Optional<RiskForecast> found = riskForecastRepository.findByUserIdAndForecastDate(userId, date);
    if (found.isPresent() && found.get().isConfirmed()) {
      // 실제 달성률이 이미 채워진 예측은 확정입니다. 결과를 본 뒤에 예측을 바꾸지 못하게 그대로 돌려줍니다.
      return RiskForecastResponse.from(found.get());
    }

    RiskRule.Assessment assessment = assess(userId, date, today);
    if (found.isPresent()) {
      // 같은 날을 다시 예측했으면 위험 수준과 이유만 최신으로 고칩니다. @Transactional 이라 끝날 때 DB 에 저장됩니다.
      RiskForecast forecast = found.get();
      forecast.renew(assessment.level(), reasonSetOf(assessment));
      return RiskForecastResponse.from(forecast);
    }
    try {
      RiskForecast saved =
          riskForecastRepository.saveAndFlush(
              new RiskForecast(userId, date, assessment.level(), reasonSetOf(assessment)));
      return RiskForecastResponse.from(saved);
    } catch (DataIntegrityViolationException e) {
      // 위에서 확인한 직후에 같은 날의 예측이 한 번 더 들어온 경우입니다. (앱을 열 때 두 번 불림 등)
      // DB 의 "한 사람·하루에 한 줄" 규칙(uk_risk_forecasts_user_id_forecast_date)이 막아 준 것입니다.
      // 이 요청의 작업은 이미 중단된 상태라, 500 이 아니라 409 로 "다시 한 번 불러 주세요" 라고 알립니다.
      throw new ApiException(ErrorCode.CONFLICT, "같은 날의 예측이 동시에 들어왔어요. 잠시 후 다시 불러 주세요");
    }
  }

  // 내가 기록한 그날의 예측 하나. 안 했으면 404 입니다. (줄을 고치지 않습니다)
  @Transactional(readOnly = true)
  public RiskForecastResponse get(LocalDate date) {
    Long userId = currentUserProvider.getCurrentUserId();
    RiskForecast forecast =
        riskForecastRepository
            .findByUserIdAndForecastDate(userId, date)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "그날의 위험 예측이 없어요"));
    return RiskForecastResponse.from(forecast);
  }

  // 내가 기록한 from ~ to (둘 다 포함) 사이의 예측들과, 그중 얼마나 맞았는지. 최대 62일까지 볼 수 있습니다.
  @Transactional(readOnly = true)
  public RiskForecastListResponse list(LocalDate from, LocalDate to) {
    Long userId = currentUserProvider.getCurrentUserId();
    if (to.isBefore(from)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "끝 날짜는 시작 날짜와 같거나 뒤여야 해요");
    }
    if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_LIST_DAYS) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "한 번에 " + MAX_LIST_DAYS + "일까지만 볼 수 있어요");
    }
    return RiskForecastListResponse.of(
        from,
        to,
        riskForecastRepository.findByUserIdAndForecastDateBetweenOrderByForecastDateAsc(
            userId, from, to));
  }

  // 그 학생의 그날(date)이 위험한 날인지 "지금 기록으로" 다시 계산합니다. 아무것도 저장하지 않습니다.
  // 통화 대본(CallScript)과 목표 조정(GoalAdjust)에 "표시" 를 넘길 때 씁니다. (로그인 확인은 부르는 쪽에서 합니다)
  // 저장된 예측을 읽지 않고 다시 계산하는 이유: 수면 기록이 늦게 들어와도 통화 때 가장 새 기록을 쓰기 위해서입니다.
  @Transactional(readOnly = true)
  public RiskLevel levelOn(Long userId, LocalDate date) {
    return assess(userId, date, today()).level();
  }

  // 그 학생의 지난 예측 중 결과를 못 채운 것을 채웁니다. 채운 개수를 돌려줍니다.
  // today 보다 앞의 날만 봅니다. (아직 끝나지 않은 날은 채우지 않습니다)
  @Transactional
  public int confirmPast(Long userId, LocalDate today) {
    List<RiskForecast> pending =
        riskForecastRepository.findByUserIdAndActualAchievementRateIsNullAndForecastDateBetween(
            userId, today.minusDays(CONFIRM_LOOKBACK_DAYS), today.minusDays(1));
    return confirm(userId, pending);
  }

  // 모든 학생의 지난 예측 중 결과를 못 채운 것을 채웁니다. 채운 개수를 돌려줍니다. (예약 작업 risk-confirm 이 부릅니다)
  // 같은 작업이 두 번 돌아도 이미 채운 줄은 "비어 있는 것" 에 들지 않아서 같은 결과입니다.
  @Transactional
  public int confirmAll(LocalDate today) {
    List<RiskForecast> pending =
        riskForecastRepository.findByActualAchievementRateIsNullAndForecastDateBetween(
            today.minusDays(CONFIRM_LOOKBACK_DAYS), today.minusDays(1));
    // 학생마다 계획·공부 기록을 읽어야 해서, 학생별로 묶어서 처리합니다.
    Map<Long, List<RiskForecast>> byUser = new LinkedHashMap<>();
    for (RiskForecast forecast : pending) {
      byUser.computeIfAbsent(forecast.getUserId(), id -> new ArrayList<>()).add(forecast);
    }
    int confirmed = 0;
    for (Map.Entry<Long, List<RiskForecast>> entry : byUser.entrySet()) {
      confirmed += confirm(entry.getKey(), entry.getValue());
    }
    return confirmed;
  }

  // 한 학생의 예측들에 실제 달성률과 맞았는지를 채웁니다. 채운 개수를 돌려줍니다.
  // 그날 계획이 없어서 달성률을 구할 수 없으면(null) 채우지 않고 비워 둡니다. (맞았는지 알 수 없음)
  private int confirm(Long userId, List<RiskForecast> pending) {
    if (pending.isEmpty()) {
      return 0;
    }
    LocalDate from = pending.get(0).getForecastDate();
    LocalDate to = from;
    for (RiskForecast forecast : pending) {
      if (forecast.getForecastDate().isBefore(from)) {
        from = forecast.getForecastDate();
      }
      if (forecast.getForecastDate().isAfter(to)) {
        to = forecast.getForecastDate();
      }
    }
    Map<LocalDate, Integer> rateByDate = new LinkedHashMap<>();
    for (RiskRule.Day day : achievementDays(userId, from, to)) {
      rateByDate.put(day.date(), day.achievementRate());
    }

    int confirmed = 0;
    for (RiskForecast forecast : pending) {
      Integer rate = rateByDate.get(forecast.getForecastDate());
      if (rate == null) {
        continue;
      }
      // "못 채운 날" 은 체크인·통화와 같은 기준(달성률 100% 미만)입니다.
      forecast.confirm(rate, CheckInQuestions.isMissedDay(rate));
      confirmed++;
    }
    return confirmed;
  }

  // 위험을 계산합니다. 재료는 today 의 어제까지 PERIOD_DAYS 일의 달성률과, 그날 아침의 수면 기록입니다.
  private RiskRule.Assessment assess(Long userId, LocalDate targetDate, LocalDate today) {
    LocalDate to = today.minusDays(1);
    LocalDate from = to.minusDays(RiskRule.PERIOD_DAYS - 1);
    List<RiskRule.Day> days = achievementDays(userId, from, to);

    // 어젯밤 수면: 잠든 시각이 있는 기록 중, 그날의 하루 전·당일 날짜의 것을 후보로 RiskRule 이 시각으로 고릅니다.
    List<Instant> sleptAtCandidates = new ArrayList<>();
    for (DailySignal signal :
        dailySignalRepository.findByUserIdAndSignalDateBetweenOrderBySignalDateAsc(
            userId, targetDate.minusDays(1), targetDate)) {
      sleptAtCandidates.add(signal.getSleptAt());
    }
    Instant sleptAt = RiskRule.lastNightSleptAt(targetDate, sleptAtCandidates);
    return RiskRule.assess(targetDate, days, sleptAt);
  }

  // from ~ to (둘 다 포함) 날마다의 달성률을 하루 요약표와 같은 계산(DaySummary)으로 구합니다.
  // 그날 계획이 없으면 달성률은 null 입니다.
  private List<RiskRule.Day> achievementDays(Long userId, LocalDate from, LocalDate to) {
    List<StudyPlan> plans =
        studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(userId, from, to);
    // 첫날 04:00 부터 to 다음 날 04:00 직전까지 시작한 공부 기록
    List<StudySession> sessions =
        studySessionRepository.findStartedBetween(
            userId, DayRule.startOf(from), DayRule.startOf(to.plusDays(1)));

    List<DaySummary.DayPlan> dayPlans = new ArrayList<>();
    for (StudyPlan plan : plans) {
      dayPlans.add(
          new DaySummary.DayPlan(plan.getPlanDate(), plan.getTargetMinutes(), plan.getKind()));
    }
    List<DaySummary.DaySession> daySessions = new ArrayList<>();
    for (StudySession session : sessions) {
      daySessions.add(
          new DaySummary.DaySession(
              session.getStartedAt(),
              session.getEndedAt(),
              session.getPausedMs(),
              session.getPauseCount(),
              session.getKind()));
    }

    List<RiskRule.Day> days = new ArrayList<>();
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      days.add(
          new RiskRule.Day(date, DaySummary.of(date, dayPlans, daySessions).achievementRate()));
    }
    return days;
  }

  private static Set<RiskReason> reasonSetOf(RiskRule.Assessment assessment) {
    return assessment.reasons().isEmpty()
        ? EnumSet.noneOf(RiskReason.class)
        : EnumSet.copyOf(assessment.reasons());
  }

  // 오늘 날짜 (새벽 4시에 바뀜)
  private LocalDate today() {
    return DayRule.dateOf(clock.instant());
  }
}
