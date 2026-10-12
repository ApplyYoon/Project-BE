package com.ithopeschool.projectbe.domain.report;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

// 시험일(D-day)까지의 누적 공부 시간을 "지금까지 / 지금 페이스 / 하루 20분 더" 세 가지로 계산합니다.
// (이슈 #45 의 「예상 성장 곡선」, 기획서 v2 3장 「결과물」·5장 「당근 상세」)
// 과장 없이 시나리오로만 보여 줍니다. "지금 페이스면 이만큼, 하루 20분 더하면 이만큼" 입니다.
//
// 정해 둔 규칙
//   1. "지금 페이스" 는 최근 14일(기록이 그보다 적으면 있는 만큼)의 하루 평균 순공시간입니다.
//   2. 남은 날(시험일까지)은 오늘을 뺀 날수입니다. 시험일이 이미 지났으면 0 으로 봅니다.
//   3. 기록이 7일 미만이면 과장해서 보여주지 않으려고 그래프 자체를 숨깁니다. (hasEnoughData 가 false)
//   4. 기록이 28일(4주) 미만이면 "4주 전의 나" 비교를 숨깁니다. (hasFourWeekComparison 이 false)
//   5. 4주 비교는 "최근 7일 평균" 과 "4주 전 7일 평균" 의 하루 평균 차이입니다.
//
// 예시 (입력 → 결과)
//   기록이 5일치뿐                                   →  hasEnoughData false (나머지 값은 비어 있음)
//   10일치 기록, 하루 평균 60분, 시험일이 10일 뒤     →  지금 페이스: 지금까지 누적 + 60분 × 10일
//                                                      하루 20분 더: 지금까지 누적 + 80분 × 10일
//   기록이 20일치뿐 (28일 미만)                       →  hasFourWeekComparison false
//
// FE 의 getGrowthCurve (src/lib/growthCurve.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 그래프가 어긋나지 않습니다.
//
// hasEnoughData: 그래프를 그릴 만큼 기록이 쌓였는지 (7일 미만이면 false)
// actual: 실제 기록 (첫 기록 날짜 ~ 오늘, 누적)
// currentPace: 지금 페이스로 시험일까지 쭉 갔을 때 (오늘, 시험일 두 점)
// withExtra: 하루 20분 더 했을 때 (오늘, 시험일 두 점)
// currentPaceTotalMinutes: 지금 페이스면 시험일까지 누적 몇 분일지
// withExtraTotalMinutes: 하루 20분 더 하면 시험일까지 누적 몇 분일지
// hasFourWeekComparison: "4주 전의 나" 와 비교할 수 있는지 (기록이 28일 미만이면 false)
// dailyMinutesChangeFromFourWeeksAgo: 최근 7일 평균이 4주 전 7일 평균보다 하루 몇 분 늘었는지 (줄었으면 음수)
public record GrowthCurve(
    boolean hasEnoughData,
    List<GrowthPoint> actual,
    List<GrowthPoint> currentPace,
    List<GrowthPoint> withExtra,
    int currentPaceTotalMinutes,
    int withExtraTotalMinutes,
    boolean hasFourWeekComparison,
    int dailyMinutesChangeFromFourWeeksAgo) {

  // 하루 추가로 더 공부한다고 가정하는 시간(분)
  public static final int EXTRA_MINUTES_PER_DAY = 20;

  // "지금 페이스" 를 구할 때 보는 최근 날 수
  public static final int RECENT_DAYS_FOR_PACE = 14;

  // 이 날수 미만이면 그래프 대신 안내 문구를 보여줍니다.
  public static final int MIN_DAYS_TO_SHOW = 7;

  // "4주 전의 나" 와 비교할 수 있는 최소 날 수
  public static final int MIN_DAYS_FOR_COMPARISON = 28;

  private static final int WEEK_DAYS = 7;

  // 기록이 모자라서 그래프를 그릴 수 없을 때의 결과입니다.
  public static final GrowthCurve EMPTY =
      new GrowthCurve(false, List.of(), List.of(), List.of(), 0, 0, false, 0);

  // 그래프의 한 점입니다. 그 날짜까지의 누적 순공시간(분)
  public record GrowthPoint(LocalDate date, int cumulativeMinutes) {}

  // 예상 성장 곡선을 계산합니다.
  //
  // today: 오늘 날짜 (하루의 기준인 새벽 4시로 정한 날짜, DayRule)
  // examDate: 시험일(D-day)
  // dailyStudyMinutes: "첫 기록 날짜" 부터 오늘까지, 하루에 하나씩 넣은 순공시간(분). 맨 뒤가 오늘입니다.
  //   첫 기록 날짜는 계획·공부 기록을 통틀어 가장 이른 날짜입니다. 공부를 안 한 날도 0 으로 넣어야 날짜가 밀리지 않습니다.
  //   기록이 하나도 없으면 빈 목록을 넘깁니다.
  public static GrowthCurve of(
      LocalDate today, LocalDate examDate, List<Integer> dailyStudyMinutes) {
    int totalDays = dailyStudyMinutes.size();
    if (totalDays < MIN_DAYS_TO_SHOW) {
      return EMPTY;
    }

    // 첫 기록 날짜부터 오늘까지, 하루씩 순공시간을 더해서 누적선을 만듭니다.
    LocalDate firstRecordDate = today.minusDays(totalDays - 1);
    List<GrowthPoint> actual = new ArrayList<>();
    int cumulativeMinutes = 0;
    for (int i = 0; i < totalDays; i++) {
      cumulativeMinutes += dailyStudyMinutes.get(i);
      actual.add(new GrowthPoint(firstRecordDate.plusDays(i), cumulativeMinutes));
    }

    // 최근 14일(또는 있는 만큼) 평균 하루 순공시간 = "지금 페이스"
    int recentDaysCount = Math.min(RECENT_DAYS_FOR_PACE, totalDays);
    double averageDailyMinutes =
        (double) sum(dailyStudyMinutes, totalDays - recentDaysCount, totalDays) / recentDaysCount;

    // 시험일까지 남은 날 (오늘은 빼고 셉니다. 시험일이 지났으면 0일)
    long daysUntilExam = Math.max(0, ChronoUnit.DAYS.between(today, examDate));

    int currentPaceTotalMinutes =
        (int) Math.round(cumulativeMinutes + averageDailyMinutes * daysUntilExam);
    int withExtraTotalMinutes =
        (int)
            Math.round(
                cumulativeMinutes + (averageDailyMinutes + EXTRA_MINUTES_PER_DAY) * daysUntilExam);

    List<GrowthPoint> currentPace =
        List.of(
            new GrowthPoint(today, cumulativeMinutes),
            new GrowthPoint(examDate, currentPaceTotalMinutes));
    List<GrowthPoint> withExtra =
        List.of(
            new GrowthPoint(today, cumulativeMinutes),
            new GrowthPoint(examDate, withExtraTotalMinutes));

    // 4주(28일) 미만이면 비교할 "4주 전" 이 없으므로 비교를 숨깁니다.
    boolean hasFourWeekComparison = totalDays >= MIN_DAYS_FOR_COMPARISON;
    int dailyMinutesChange = 0;
    if (hasFourWeekComparison) {
      double recentWeekAverage =
          (double) sum(dailyStudyMinutes, totalDays - WEEK_DAYS, totalDays) / WEEK_DAYS;
      int fourWeeksAgoStart = totalDays - MIN_DAYS_FOR_COMPARISON;
      double fourWeeksAgoWeekAverage =
          (double) sum(dailyStudyMinutes, fourWeeksAgoStart, fourWeeksAgoStart + WEEK_DAYS)
              / WEEK_DAYS;
      dailyMinutesChange = (int) Math.round(recentWeekAverage - fourWeeksAgoWeekAverage);
    }

    return new GrowthCurve(
        true,
        actual,
        currentPace,
        withExtra,
        currentPaceTotalMinutes,
        withExtraTotalMinutes,
        hasFourWeekComparison,
        dailyMinutesChange);
  }

  // 목록의 from 번째부터 to 번째 앞까지를 더합니다. (from 은 넣고 to 는 넣지 않습니다)
  private static long sum(List<Integer> values, int from, int to) {
    long total = 0;
    for (int i = from; i < to; i++) {
      total += values.get(i);
    }
    return total;
  }
}
