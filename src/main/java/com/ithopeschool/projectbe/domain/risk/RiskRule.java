package com.ithopeschool.projectbe.domain.risk;

import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// "내일(또는 오늘) 무너질 가능성이 높은 날인가" 를 규칙으로 계산합니다. (이슈 #49 분석 ⑧ 위험 예측)
// AI 가 아니라 코드가 숫자로 정합니다. (기획서 원칙: 숫자 계산은 코드가, 해석과 문장은 AI 가)
// 아침 말투 조정(통화 대본 #28), 목표 축소 제안(목표 조정 #63)에 "위험한 날" 표시로 쓰입니다.
// 규칙과 근거는 docs/RISK-FORECAST.md 에도 같은 내용으로 적어 두었습니다.
//
// 이슈의 예시: "늦게 잔 다음 날이 월요일이고, 최근 월요일 달성률이 낮을 때". 이 예시의 두 조건을 규칙 두 개로 나눴습니다.
//
// 규칙 ① 약한 요일 (WEAK_WEEKDAY)
//   예측하는 날과 같은 요일의 평균 달성률이, 쓴 날 전체의 평균 달성률보다 15%p 이상 낮으면 켜집니다.
//   - 달성률이 있는 날(계획이 있던 날)만 셉니다. 계획이 없던 날은 "0%" 가 아니라 "계산 불가" 라서 뺍니다. (docs/API.md 5장)
//   - 그 요일에 달성률이 있는 날이 5일 이상이어야 합니다. 5일 미만이면 이 규칙은 쓰지 않습니다.
//     (팀 원칙: 비교의 근거가 5일 미만이면 "가설" 로만 말하고, 표시에는 넘기지 않습니다)
//   - 평균은 모두 더해서 나눈 뒤에 비교하고, 화면에 보여 줄 숫자만 마지막에 한 번 반올림합니다.
//   - 예측하는 날(과 그 뒤의 날)은 재료로 쓰지 않습니다. 결과를 보고 예측하는 일을 막기 위해서입니다.
//
// 규칙 ② 늦게 잠 (LATE_SLEEP)
//   어젯밤 잠든 시각이 새벽 1시(한국 시간) 이후이면 켜집니다.
//   - "어젯밤" = 예측하는 날의 새벽 4시(하루의 기준)보다 10시간 전(전날 오후 6시)부터 6시간 뒤(그날 오전 10시) 사이에 잠든 것.
//     그 안에 여러 번 잠들었다면 가장 먼저 잠든 시각을 씁니다. (밤에 잠든 시각이지, 낮잠이 아닙니다)
//   - 수면 데이터는 앱·워치가 있는 학생만 보내는 선택 데이터입니다. 없으면 "모름" 이라 이 규칙은 쓰지 않습니다. 0 으로 세지 않습니다.
//     (그래서 수면을 모르는 학생은 위험 수준이 "주의" 까지만 나옵니다)
//
// 위험 수준: 켜진 규칙이 둘 다이면 HIGH(높음), 하나면 WATCH(주의), 없으면 NONE(없음)
//
// 예시 (예측하는 날: 월요일)
//   최근 월요일 5번의 평균 달성률 52%, 계획이 있던 날 전체의 평균 71% (19%p 낮음), 월요일 새벽 2시에 잠
//     →  HIGH (이유: 약한 요일 · 늦게 잠)
//   같은 상황인데 수면 데이터가 없음  →  WATCH (이유: 약한 요일)
//   최근 월요일이 4번뿐  →  규칙 ① 은 쓰지 않음
public final class RiskRule {

  // 재료로 보는 기간(일, 예측 시점의 어제까지). 요일마다 최대 8번이 나와서, 5주쯤 쓰면 근거 5일이 채워집니다.
  // (30일이면 요일 하나에 4번뿐이라 근거 5일이 거의 채워지지 않아서 56일로 정했습니다)
  public static final int PERIOD_DAYS = 56;

  // 규칙 ① 에 필요한 "그 요일에 달성률이 있는 날" 의 최소 개수. (팀 원칙: 5일)
  public static final int MIN_WEEKDAY_DAYS = 5;

  // 규칙 ① 이 켜지는 차이(%p). 그 요일의 평균이 전체 평균보다 이만큼 이상 낮아야 합니다.
  public static final double WEAK_WEEKDAY_GAP_POINTS = 15.0;

  // "어젯밤" 을 찾는 범위: 예측하는 날의 새벽 4시 기준으로 이만큼 전부터
  static final Duration NIGHT_WINDOW_BEFORE = Duration.ofHours(10);
  // 그리고 이만큼 뒤까지 (그 시각은 포함하지 않음)
  static final Duration NIGHT_WINDOW_AFTER = Duration.ofHours(6);
  // 이 시각 이후에 잠들면 "늦게 잠": 예측하는 날의 새벽 4시보다 3시간 전 = 새벽 1시
  static final Duration LATE_SLEEP_BEFORE_DAY_START = Duration.ofHours(3);

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private RiskRule() {}

  // 하루의 재료입니다. date: 날짜 / achievementRate: 그날 달성률(0 ~ 100). 계획이 없던 날은 null
  public record Day(LocalDate date, Integer achievementRate) {}

  // 규칙 ① 의 근거입니다. 근거가 5일 미만이라 규칙을 쓰지 않았으면 null 입니다.
  // weekdayAverageRate: 그 요일의 평균 달성률(%) / overallAverageRate: 전체 평균 달성률(%)
  // days: 그 요일에 달성률이 있던 날 수 / weak: 규칙 ① 이 켜졌는지
  public record WeekdayEvidence(
      int weekdayAverageRate, int overallAverageRate, int days, boolean weak) {}

  // 계산 결과입니다.
  // level: 위험 수준 / reasons: 켜진 규칙들(약한 요일 → 늦게 잠 순서)
  // weekdayEvidence: 규칙 ① 의 근거. 근거가 모자라면 null
  // sleptAt: 규칙 ② 에 쓴 어젯밤 잠든 시각. 수면 데이터가 없으면 null
  public record Assessment(
      RiskLevel level,
      List<RiskReason> reasons,
      WeekdayEvidence weekdayEvidence,
      Instant sleptAt) {}

  // 위험을 계산합니다.
  //
  // targetDate: 예측하는 날 (하루의 기준을 따르는 날짜)
  // days: 날짜마다의 달성률. 기간(PERIOD_DAYS) 안의 날만 넘깁니다. 순서는 뒤섞여 있어도 되고,
  //       targetDate 와 그 뒤의 날이 섞여 있어도 쓰지 않습니다.
  // sleptAt: 어젯밤 잠든 시각(lastNightSleptAt 의 결과). 모르면 null
  public static Assessment assess(LocalDate targetDate, List<Day> days, Instant sleptAt) {
    List<RiskReason> reasons = new ArrayList<>();

    WeekdayEvidence evidence = weekdayEvidenceOf(targetDate, days);
    if (evidence != null && evidence.weak()) {
      reasons.add(RiskReason.WEAK_WEEKDAY);
    }
    if (sleptAt != null && isLateSleep(targetDate, sleptAt)) {
      reasons.add(RiskReason.LATE_SLEEP);
    }

    RiskLevel level =
        reasons.size() >= 2
            ? RiskLevel.HIGH
            : reasons.size() == 1 ? RiskLevel.WATCH : RiskLevel.NONE;
    return new Assessment(level, List.copyOf(reasons), evidence, sleptAt);
  }

  // 규칙 ① 의 근거를 계산합니다. 그 요일에 달성률이 있는 날이 5일 미만이면 null 입니다.
  static WeekdayEvidence weekdayEvidenceOf(LocalDate targetDate, List<Day> days) {
    long weekdaySum = 0;
    int weekdayCount = 0;
    long overallSum = 0;
    int overallCount = 0;
    for (Day day : days) {
      // 예측하는 날과 그 뒤의 날은 쓰지 않습니다. 달성률이 없는 날(계획 없음)도 뺍니다.
      if (!day.date().isBefore(targetDate) || day.achievementRate() == null) {
        continue;
      }
      overallSum += day.achievementRate();
      overallCount++;
      if (day.date().getDayOfWeek() == targetDate.getDayOfWeek()) {
        weekdaySum += day.achievementRate();
        weekdayCount++;
      }
    }
    if (weekdayCount < MIN_WEEKDAY_DAYS) {
      return null;
    }
    // 반올림하기 전의 평균으로 비교하고, 보여 줄 숫자만 마지막에 한 번 반올림합니다.
    double weekdayAverage = (double) weekdaySum / weekdayCount;
    double overallAverage = (double) overallSum / overallCount;
    boolean weak = overallAverage - weekdayAverage >= WEAK_WEEKDAY_GAP_POINTS;
    return new WeekdayEvidence(
        (int) Math.round(weekdayAverage), (int) Math.round(overallAverage), weekdayCount, weak);
  }

  // 후보 중에서 "어젯밤" 에 잠든 시각을 고릅니다. 범위 안에 없으면 null 입니다. (수면 데이터가 없는 것과 같습니다)
  //
  // candidates: 수면 기록들의 잠든 시각. 모르는 것(null)이 섞여 있어도 됩니다.
  //   예측하는 날: 월요일 → 일요일 오후 6시 이상 ~ 월요일 오전 10시 미만에 잠든 것 중 가장 이른 것
  public static Instant lastNightSleptAt(LocalDate targetDate, List<Instant> candidates) {
    Instant dayStart = DayRule.startOf(targetDate);
    Instant from = dayStart.minus(NIGHT_WINDOW_BEFORE);
    Instant to = dayStart.plus(NIGHT_WINDOW_AFTER);
    Instant earliest = null;
    for (Instant candidate : candidates) {
      if (candidate == null || candidate.isBefore(from) || !candidate.isBefore(to)) {
        continue;
      }
      if (earliest == null || candidate.isBefore(earliest)) {
        earliest = candidate;
      }
    }
    return earliest;
  }

  // 그 시각에 잠든 것이 "늦게 잠" 인지 알려줍니다. 예측하는 날의 새벽 1시 정각부터 늦게 잔 것입니다.
  //   월요일 예측: 일요일 밤 11시 59분 → false / 월요일 00:59 → false / 월요일 01:00 → true / 월요일 03:00 → true
  static boolean isLateSleep(LocalDate targetDate, Instant sleptAt) {
    Instant lateFrom = DayRule.startOf(targetDate).minus(LATE_SLEEP_BEFORE_DAY_START);
    return !sleptAt.isBefore(lateFrom);
  }
}
