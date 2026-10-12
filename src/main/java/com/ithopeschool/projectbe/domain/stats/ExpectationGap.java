package com.ithopeschool.projectbe.domain.stats;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// "예상한 공부 시간" 과 "실제로 공부한 시간" 의 차이(인식-실제 갭)를 계산합니다. (이슈 #40 분석 ⑤)
// 사람은 자기가 얼마나 공부할지를 실제보다 높게 잡는 일이 많습니다. 그 차이를 숫자로 보여주는 것이 '아하' 인사이트의 재료입니다.
//   예: "공부 시간을 매일 평균 1시간 20분 높게 예상해."
//
// 정해 둔 규칙
//   1. 그날의 "예상 순공" 은 체크인에 적은 값이 있으면 그 값, 없으면 그날 공부 계획의 목표 시간 합입니다.
//      둘 다 없으면(0 이하 포함) 그날은 예상이 없는 날이라 계산에서 뺍니다.
//      (체크인에서 매일 묻지 않게 되면서, 대부분의 날은 계획의 목표 시간 합을 예상으로 씁니다)
//   2. 하루의 차이 = 예상 − 실제. 공부를 안 한 날도 예상이 있으면 넣습니다. (실제 0분)
//   3. 평균 차이(분)는 모두 더해서 나눈 뒤 마지막에 한 번만 반올림합니다.
//   4. 방향: 평균 차이가 0 보다 크면 "높게 예상"(OVER), 작으면 "낮게 예상"(UNDER), 0 이면 "비슷"(SAME).
//   5. 계산에 쓴 날짜를 근거로 같이 돌려줍니다. 오래된 날부터입니다.
//   6. 계산에 쓴 날이 5일 미만이면 "가설" 입니다. 며칠 안 되는 기록으로 단정하지 않습니다. (기획서 3장)
//   7. 예상이 있는 날이 하루도 없으면 갭이 없습니다. (null)
//
// 예시
//   5일 동안 매일 180분을 예상하고 100분을 공부함         →  평균 80분, 높게 예상, 5일, 가설 아님
//   3일 동안 매일 60분을 예상하고 90분을 공부함           →  평균 -30분, 낮게 예상, 3일, 가설
//   체크인 예상 120분, 그날 계획 240분, 실제 100분        →  예상은 120분(체크인이 먼저) → 차이 20분
//   체크인 예상 없음, 그날 계획 240분, 실제 100분         →  예상은 240분 → 차이 140분
//
// "처음 계획 대비 지금" 도 같은 계산입니다. 최근 7일의 날마다 "처음 약속한 하루 목표" 를 예상으로 넣으면 됩니다. (ofInitialPlan)
//   처음 약속 하루 180분, 최근 7일 평균 100분  →  평균 80분, 높게 예상
//
// ⚠️ FE 에는 이 계산이 아직 없습니다. 이슈 #40 본문의 규칙으로 만들었습니다. FE 가 같은 숫자를 보여주려면 이 규칙을 따라야 합니다.
//
// averageDiffMinutes: 평균 차이(분). 예상이 실제보다 크면 양수 / direction: 차이의 방향
// dates: 계산에 쓴 날짜들 (근거) / isHypothesis: 5일 미만이라 가설이면 true
public record ExpectationGap(
    int averageDiffMinutes, Direction direction, List<LocalDate> dates, boolean isHypothesis) {

  // 계산에 쓴 날이 이만큼은 있어야 "가설" 딱지를 뗍니다.
  public static final int MIN_DAYS = 5;

  // "처음 계획 대비 지금" 을 볼 때 쓰는 날 수입니다. (최근 7일)
  public static final int RECENT_DAYS = 7;

  // 차이의 방향입니다.
  public enum Direction {
    // 높게 예상: 실제보다 많이 할 거라고 봄
    OVER,
    // 낮게 예상: 실제보다 적게 할 거라고 봄
    UNDER,
    // 비슷: 평균 차이가 0분
    SAME
  }

  // 하루치 재료입니다.
  // date: 날짜
  // checkInExpectedMinutes: 그날 체크인에 적은 예상 순공시간(분). 안 적었으면 null
  // plannedMinutes: 그날 공부 계획의 목표 시간 합(분). 계획이 없으면 0. 노는 계획은 넣지 않습니다.
  // studyMinutes: 그날 실제 순공시간(분). DaySummary 의 값을 넣습니다.
  public record Day(
      LocalDate date, Integer checkInExpectedMinutes, int plannedMinutes, int studyMinutes) {}

  // 하루의 날짜와 순공시간입니다. "처음 계획 대비 지금" 에 씁니다.
  public record DayStudy(LocalDate date, int studyMinutes) {}

  // 그날의 "예상 순공"(분)을 돌려줍니다. 예상이 없는 날이면 null 입니다. (규칙 1)
  //
  //   (120, 240) → 120   /   (null, 240) → 240   /   (0, 240) → 240   /   (null, 0) → null
  public static Integer expectedMinutesOf(Integer checkInExpectedMinutes, int plannedMinutes) {
    if (checkInExpectedMinutes != null && checkInExpectedMinutes > 0) {
      return checkInExpectedMinutes;
    }
    return plannedMinutes > 0 ? plannedMinutes : null;
  }

  // 날들의 예상 순공과 실제 순공의 차이를 계산합니다. 예상이 있는 날이 없으면 null 입니다.
  //
  // days: 날짜마다 하나씩. 순서가 뒤섞여 있어도 됩니다.
  public static ExpectationGap of(List<Day> days) {
    List<Day> sorted = new ArrayList<>(days);
    sorted.sort(Comparator.comparing(Day::date));

    long diffSum = 0;
    List<LocalDate> dates = new ArrayList<>();
    for (Day day : sorted) {
      Integer expected = expectedMinutesOf(day.checkInExpectedMinutes(), day.plannedMinutes());
      if (expected == null) {
        continue;
      }
      diffSum += expected - day.studyMinutes();
      dates.add(day.date());
    }
    if (dates.isEmpty()) {
      return null;
    }

    // 마지막에 한 번만 반올림합니다. (규칙 3)
    int averageDiffMinutes = (int) Math.round((double) diffSum / dates.size());
    return new ExpectationGap(
        averageDiffMinutes, directionOf(averageDiffMinutes), dates, dates.size() < MIN_DAYS);
  }

  // "처음 계획 대비 지금" 의 차이를 계산합니다. 처음 약속한 하루 목표와 최근 날들의 실제 순공을 비교합니다.
  // 처음 계획이 없거나(0 이하) 날이 하나도 없으면 null 입니다.
  //
  // initialDailyMinutes: 온보딩에서 처음 약속한 하루 목표(분)
  // recentDays: 최근 날들의 순공시간. 보통 최근 7일(RECENT_DAYS)을 넘깁니다. 공부를 안 한 날도 0분으로 넣습니다.
  public static ExpectationGap ofInitialPlan(int initialDailyMinutes, List<DayStudy> recentDays) {
    if (initialDailyMinutes <= 0) {
      return null;
    }
    List<Day> days = new ArrayList<>();
    for (DayStudy day : recentDays) {
      // 계획 자리는 비워 두고(0), 처음 약속을 그날의 예상으로 넣습니다.
      days.add(new Day(day.date(), initialDailyMinutes, 0, day.studyMinutes()));
    }
    return of(days);
  }

  private static Direction directionOf(int averageDiffMinutes) {
    if (averageDiffMinutes > 0) {
      return Direction.OVER;
    }
    if (averageDiffMinutes < 0) {
      return Direction.UNDER;
    }
    return Direction.SAME;
  }
}
