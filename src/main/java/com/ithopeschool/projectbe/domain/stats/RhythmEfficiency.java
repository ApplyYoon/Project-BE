package com.ithopeschool.projectbe.domain.stats;

import com.ithopeschool.projectbe.domain.session.StudyTime;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.TimeOfDay;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

// 요일별·시간대별로 공부가 잘 되는지(효율)를 계산합니다. (이슈 #37 분석 ② 리듬·패턴 중 요일·시간대 효율)
// "주말에 무너지는 편이야", "밤에는 일시정지가 많아" 같은 문장의 재료입니다.
//
// 정해 둔 규칙
//   1. 날짜는 하루의 기준(새벽 4시)을 따릅니다. 화요일 새벽 1시에 시작한 기록은 월요일 것입니다. (DayRule)
//   2. "쓴 날" 만 셉니다. 계획도 공부도 없는 날은 "0분 공부한 날" 이 아니라 "앱을 안 쓴 날" 이라 뺍니다.
//      계획은 있는데 공부를 안 한 날은 0분으로 넣습니다. (docs/API.md 6장, 효과 판정과 같은 규칙)
//      요일끼리 비교하는 숫자라서, 쉰 날이 섞이면 그 요일이 억울하게 낮아지기 때문입니다.
//   3. 요일별 결과: 그 요일의 평균 순공(분)과 평균 달성률(%).
//      평균 달성률은 계획이 있던 날(달성률이 있는 날)만으로 냅니다. 계획 없이 공부만 한 날은 순공 평균에만 들어갑니다.
//      평균은 모두 더해서 나눈 뒤 마지막에 한 번만 반올림합니다. 셀 날이 없으면 0 이 아니라 null 입니다.
//      요일 목록은 월요일부터 일요일까지 7칸입니다. (실패 원인 #39 의 요일 분포와 같은 순서)
//   4. 평일·주말 비교: 월~금을 "평일", 토·일을 "주말" 로 묶어 규칙 3 과 같이 계산합니다.
//      비교하는 두 쪽 중 하나라도 5일 미만이면 가설입니다. (이슈 #37 완료 조건)
//   5. 시간대별 결과: 공부 기록을 "시작한 시각" 의 시간대로 나눕니다. 나누는 시각은 주간 리포트(StudyFacts)와 같습니다.
//        아침 05:00 ~ 11:59 / 낮 12:00 ~ 17:59 / 저녁 18:00 ~ 21:59 / 밤 22:00 ~ 04:59
//      - 기록 하나의 평균 순공(분) = 순공 합 ÷ 기록 수. 마지막에 한 번만 반올림합니다.
//      - 일시정지 비율(%) = (전체 길이 − 순공) 합 ÷ 전체 길이 합 × 100. 마지막에 한 번만 반올림합니다.
//        순공은 StudyTime 으로 구해서, 믿을 수 없는 일시정지 값(음수, 기록보다 김)은 0 으로 칩니다.
//        전체 길이의 합이 0 이면 나눌 수 없어서 null 입니다.
//   6. 근거 날짜: 요일·평일/주말은 계산에 쓴 날, 시간대는 그 시간대에 기록이 있던 날입니다. 오래된 날부터, 같은 날은 한 번.
//   7. 근거 날짜가 5일 미만이면 가설입니다. (hypothesis)
//   8. 기간(from ~ to, 둘 다 포함) 밖의 날과 기록은 쓰지 않습니다.
//
// 예시
//   월요일 3번: 60분(달성 100%), 30분(50%), 0분(계획만 있음, 0%)  →  평균 순공 30분, 평균 달성률 50%, 3일, 가설
//   밤 기록 2개: 60분 동안 일시정지 15분, 40분 동안 일시정지 5분
//     →  기록 하나 평균 순공 40분 ((45 + 35) ÷ 2), 일시정지 비율 20% (20 ÷ 100)
//
// byWeekday: 요일별 결과 7칸 (0번째 = 월요일 … 6번째 = 일요일)
// weekdayVsWeekend: 평일·주말 비교 / byTimeOfDay: 시간대별 결과 (아침 · 낮 · 저녁 · 밤 순서)
public record RhythmEfficiency(
    List<DayGroup> byWeekday, Comparison weekdayVsWeekend, List<TimeGroup> byTimeOfDay) {

  // 근거 날짜가 이만큼은 있어야 "가설" 딱지를 뗍니다.
  public static final int MIN_DAYS = 5;

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  // 하루의 재료입니다.
  // date: 날짜 / studyMinutes: 그날 순공(분). DaySummary 의 값
  // achievementRate: 그날 달성률. DaySummary 의 값. 계획이 없던 날은 null
  public record Day(LocalDate date, int studyMinutes, Integer achievementRate) {}

  // 공부 기록 하나의 재료입니다. 노는 기록은 부르는 쪽에서 빼고 넘깁니다.
  // startedAt: 시작한 시각 / endedAt: 끝낸 시각 / pausedMillis: 일시정지해 있던 시간의 합(밀리초)
  public record Session(Instant startedAt, Instant endedAt, long pausedMillis) {}

  // 날짜 묶음(요일 하나, 또는 평일·주말) 하나의 결과입니다.
  // averageStudyMinutes: 평균 순공(분). 셀 날이 없으면 null
  // averageAchievementRate: 평균 달성률(%). 계획이 있던 날이 없으면 null
  // dates: 계산에 쓴 날 (근거, 오래된 날부터) / hypothesis: 5일 미만이면 true
  public record DayGroup(
      Integer averageStudyMinutes,
      Integer averageAchievementRate,
      List<LocalDate> dates,
      boolean hypothesis) {}

  // 평일·주말 비교입니다. hypothesis: 둘 중 하나라도 5일 미만이면 true (규칙 4)
  public record Comparison(DayGroup weekday, DayGroup weekend, boolean hypothesis) {}

  // 시간대 하나의 결과입니다.
  // timeOfDay: 시간대 / sessionCount: 그 시간대에 시작한 기록 수
  // averageSessionMinutes: 기록 하나의 평균 순공(분). 기록이 없으면 null
  // pauseRate: 일시정지 비율(%). 계산할 수 없으면 null
  // dates: 그 시간대에 기록이 있던 날 (근거, 오래된 날부터) / hypothesis: 5일 미만이면 true
  public record TimeGroup(
      TimeOfDay timeOfDay,
      int sessionCount,
      Integer averageSessionMinutes,
      Integer pauseRate,
      List<LocalDate> dates,
      boolean hypothesis) {}

  // 기간의 요일·시간대 효율을 계산합니다.
  //
  // days: 날짜마다의 순공과 달성률. 목록에 없는 날은 "앱을 안 쓴 날" 로 봅니다. 순서가 뒤섞여 있어도 됩니다.
  // sessions: 공부 기록들. 기간 밖의 기록이 섞여 있어도 됩니다.
  public static RhythmEfficiency of(
      List<Day> days, List<Session> sessions, LocalDate from, LocalDate to) {
    // 기간 안의 "쓴 날" 만 남깁니다. (규칙 2, 8)
    List<Day> usedDays = new ArrayList<>();
    for (Day day : days) {
      boolean inPeriod = !day.date().isBefore(from) && !day.date().isAfter(to);
      boolean used = day.achievementRate() != null || day.studyMinutes() > 0;
      if (inPeriod && used) {
        usedDays.add(day);
      }
    }
    usedDays.sort(Comparator.comparing(Day::date));

    // 요일별 (규칙 3). DayOfWeek 는 월요일이 1, 일요일이 7 입니다.
    List<DayGroup> byWeekday = new ArrayList<>();
    for (int dayNumber = 1; dayNumber <= 7; dayNumber++) {
      DayOfWeek weekday = DayOfWeek.of(dayNumber);
      List<Day> group = new ArrayList<>();
      for (Day day : usedDays) {
        if (day.date().getDayOfWeek() == weekday) {
          group.add(day);
        }
      }
      byWeekday.add(dayGroupOf(group));
    }

    // 평일·주말 (규칙 4)
    List<Day> weekdays = new ArrayList<>();
    List<Day> weekends = new ArrayList<>();
    for (Day day : usedDays) {
      DayOfWeek weekday = day.date().getDayOfWeek();
      if (weekday == DayOfWeek.SATURDAY || weekday == DayOfWeek.SUNDAY) {
        weekends.add(day);
      } else {
        weekdays.add(day);
      }
    }
    DayGroup weekdayGroup = dayGroupOf(weekdays);
    DayGroup weekendGroup = dayGroupOf(weekends);
    Comparison weekdayVsWeekend =
        new Comparison(
            weekdayGroup, weekendGroup, weekdayGroup.hypothesis() || weekendGroup.hypothesis());

    // 시간대별 (규칙 5)
    List<TimeGroup> byTimeOfDay = new ArrayList<>();
    for (TimeOfDay timeOfDay : TimeOfDay.values()) {
      byTimeOfDay.add(timeGroupOf(timeOfDay, sessions, from, to));
    }

    return new RhythmEfficiency(List.copyOf(byWeekday), weekdayVsWeekend, List.copyOf(byTimeOfDay));
  }

  // 날짜 묶음 하나의 평균을 냅니다. days 는 날짜 순서로 들어옵니다.
  // 이벤트 다음 날 효과(AfterEventEffect)도 같은 규칙으로 평균을 내려고 이 함수를 씁니다. (그래서 private 이 아님)
  static DayGroup dayGroupOf(List<Day> days) {
    long studySum = 0;
    long rateSum = 0;
    int rateCount = 0;
    List<LocalDate> dates = new ArrayList<>();
    for (Day day : days) {
      studySum += day.studyMinutes();
      // 계획이 있던 날만 달성률 평균에 넣습니다.
      if (day.achievementRate() != null) {
        rateSum += day.achievementRate();
        rateCount++;
      }
      dates.add(day.date());
    }
    // 마지막에 한 번만 반올림합니다. 셀 날이 없으면 null 입니다.
    Integer averageStudyMinutes =
        days.isEmpty() ? null : (int) Math.round((double) studySum / days.size());
    Integer averageAchievementRate =
        rateCount == 0 ? null : (int) Math.round((double) rateSum / rateCount);
    return new DayGroup(
        averageStudyMinutes, averageAchievementRate, List.copyOf(dates), dates.size() < MIN_DAYS);
  }

  // 시간대 하나의 기록들을 모아 계산합니다.
  private static TimeGroup timeGroupOf(
      TimeOfDay timeOfDay, List<Session> sessions, LocalDate from, LocalDate to) {
    int sessionCount = 0;
    long studyMillisSum = 0;
    long lengthMillisSum = 0;
    // TreeSet 이라 같은 날은 한 번만, 오래된 날부터 들어갑니다.
    TreeSet<LocalDate> dates = new TreeSet<>();
    for (Session session : sessions) {
      LocalDate date = DayRule.dateOf(session.startedAt());
      if (date.isBefore(from) || date.isAfter(to)) {
        continue;
      }
      int hour = session.startedAt().atZone(SEOUL).getHour();
      if (StudyFacts.timeOfDay(hour) != timeOfDay) {
        continue;
      }
      sessionCount++;
      dates.add(date);
      long studyMillis =
          StudyTime.studyMillis(session.startedAt(), session.endedAt(), session.pausedMillis());
      studyMillisSum += studyMillis;
      // 전체 길이. 끝이 시작보다 빠른 잘못된 기록은 0 으로 칩니다.
      lengthMillisSum +=
          Math.max(0, session.endedAt().toEpochMilli() - session.startedAt().toEpochMilli());
    }

    // 기록 하나의 평균 순공. 밀리초로 모두 더한 뒤 마지막에 한 번만 분으로 바꿉니다.
    Integer averageSessionMinutes =
        sessionCount == 0
            ? null
            : StudyTime.toMinutes(Math.round((double) studyMillisSum / sessionCount));
    // 일시정지 비율 = (전체 길이 − 순공) ÷ 전체 길이
    Integer pauseRate =
        lengthMillisSum == 0
            ? null
            : (int) Math.round((double) (lengthMillisSum - studyMillisSum) / lengthMillisSum * 100);
    return new TimeGroup(
        timeOfDay,
        sessionCount,
        averageSessionMinutes,
        pauseRate,
        List.copyOf(dates),
        dates.size() < MIN_DAYS);
  }
}
