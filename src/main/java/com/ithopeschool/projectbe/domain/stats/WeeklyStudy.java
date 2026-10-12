package com.ithopeschool.projectbe.domain.stats;

import com.ithopeschool.projectbe.domain.summary.DaySummary;
import com.ithopeschool.projectbe.domain.summary.DaySummary.DaySession;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// "최근 7일 공부 시간" 막대그래프에 필요한 값을 계산합니다. (이슈 #36 의 그래프 화면)
//
// 정해 둔 규칙
//   1. 오늘을 포함한 최근 7일입니다. 맨 뒤(7번째)가 오늘이고, 앞으로 갈수록 하루씩 옛날입니다.
//      오늘은 하루의 기준(새벽 4시)으로 정합니다. 새벽 2시면 아직 어제입니다. (DayRule)
//   2. 하루 공부 시간은 DaySummary.of 가 계산합니다. 그래서 다른 화면의 숫자와 같은 규칙입니다.
//      (그날 "시작한" 기록만 세고, 밀리초로 더한 뒤 마지막에 한 번만 분으로 반올림)
//   3. 막대 높이(percent)는 가장 많이 공부한 날을 100 으로 잡고 나머지를 비율로 계산합니다. 반올림하지 않습니다.
//      모든 날이 0분이면 나눌 수 없으니 전부 0 입니다.
//   4. 7일 합계는 "막대에 보이는 하루 시간" 을 그대로 더한 값입니다. (화면에서 직접 더해 봐도 맞도록)
//
// 예시 — 오늘이 2026-10-11(일), 10/05(월) 60분 · 10/08(목) 120분 · 10/11(일) 30분 공부
//   days: 10/05(월, 60분, 50%) · 10/06(0분, 0%) · 10/07(0분, 0%) · 10/08(목, 120분, 100%)
//         · 10/09(0분) · 10/10(0분) · 10/11(일, 30분, 25%, 오늘)
//   totalMinutes: 210
//
// FE 의 getWeeklyStudy (src/lib/weeklyStudy.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 막대가 어긋나지 않습니다.
//
// days: 7일치. 오래된 날이 앞, 오늘이 맨 뒤입니다.
// totalMinutes: 7일 공부 시간 합계(분)
public record WeeklyStudy(List<Day> days, int totalMinutes) {

  // 보여줄 날짜 수
  public static final int DAY_COUNT = 7;

  // 막대 하나(하루)에 필요한 값입니다.
  // date: 날짜 / weekday: 요일 숫자. 일요일 0 ~ 토요일 6
  // minutes: 그날 공부한 시간(분) / percent: 막대 높이(0 ~ 100). 가장 많이 공부한 날이 100 입니다.
  // isToday: 오늘이면 true
  public record Day(LocalDate date, int weekday, int minutes, double percent, boolean isToday) {}

  // 공부 기록으로 최근 7일 막대그래프 값을 만듭니다. now 는 "지금이 언제인지" 입니다.
  public static WeeklyStudy of(List<DaySession> sessions, Instant now) {
    return of(sessions, DayRule.dateOf(now));
  }

  // 공부 기록으로 최근 7일 막대그래프 값을 만듭니다.
  //
  // sessions: 여러 날짜가 섞여 있어도 됩니다. 노는 기록은 공부가 아니라서, 부르는 쪽에서 빼고 넘깁니다.
  // today: 오늘 날짜 (하루의 기준인 새벽 4시로 정한 날짜, DayRule)
  public static WeeklyStudy of(List<DaySession> sessions, LocalDate today) {
    // 6일 전부터 오늘까지 순서대로, 날짜와 그날 공부한 시간을 구합니다.
    List<LocalDate> dates = new ArrayList<>();
    List<Integer> minutesList = new ArrayList<>();
    int maxMinutes = 0;
    int totalMinutes = 0;
    for (int daysAgo = DAY_COUNT - 1; daysAgo >= 0; daysAgo--) {
      LocalDate date = today.minusDays(daysAgo);
      // 계획은 필요 없어서 빈 목록을 넘깁니다.
      int minutes = DaySummary.of(date, List.of(), sessions).studyMinutes();
      dates.add(date);
      minutesList.add(minutes);
      maxMinutes = Math.max(maxMinutes, minutes);
      totalMinutes += minutes;
    }

    List<Day> days = new ArrayList<>();
    for (int index = 0; index < DAY_COUNT; index++) {
      LocalDate date = dates.get(index);
      int minutes = minutesList.get(index);
      // 가장 많이 공부한 날 = 100%. 모두 0분이면 0 으로 나눌 수 없으니 막대는 전부 0 입니다.
      double percent = maxMinutes > 0 ? (double) minutes / maxMinutes * 100 : 0;
      // getDayOfWeek 는 월 1 ~ 일 7 이라서, 7 로 나눈 나머지로 일 0 ~ 토 6 으로 바꿉니다.
      int weekday = date.getDayOfWeek().getValue() % 7;
      days.add(new Day(date, weekday, minutes, percent, index == DAY_COUNT - 1));
    }
    return new WeeklyStudy(days, totalMinutes);
  }
}
