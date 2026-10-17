package com.ithopeschool.projectbe.domain.stats.dto;

import com.ithopeschool.projectbe.domain.stats.AfterEventEffect;
import com.ithopeschool.projectbe.domain.stats.CollapseCycle;
import com.ithopeschool.projectbe.domain.stats.RhythmEfficiency;
import com.ithopeschool.projectbe.domain.stats.RhythmEfficiency.DayGroup;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// GET /api/v1/stats/rhythm 의 응답입니다. (이슈 #37 분석 ② 리듬·패턴)
// from, to: 계산한 기간 'YYYY-MM-DD' (둘 다 포함, to 는 어제)
// collapse, recovery: 며칠째 무너지고 며칠 만에 회복하는지 (CollapseCycle)
// byWeekday, weekdayVsWeekend, byTimeOfDay: 요일·시간대 효율 (RhythmEfficiency)
// afterEvents: 시험·약속 다음 날 vs 평소 (AfterEventEffect)
//
// 날짜 묶음(DayGroup)의 모양은 어디서나 같습니다.
//   { "averageStudyMinutes", "averageAchievementRate", "dates", "hypothesis" }
public record RhythmStatsResponse(
    LocalDate from,
    LocalDate to,
    CollapseCycle.Collapse collapse,
    CollapseCycle.Recovery recovery,
    List<WeekdayItem> byWeekday,
    RhythmEfficiency.Comparison weekdayVsWeekend,
    List<TimeOfDayItem> byTimeOfDay,
    AfterEvents afterEvents) {

  // 요일 이름. byWeekday 의 순서(월요일부터)와 같습니다.
  private static final List<String> WEEKDAYS =
      List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun");

  // 요일 하나. weekday: "mon" ~ "sun" (순서를 헷갈리지 않게 이름을 붙였습니다)
  public record WeekdayItem(
      String weekday,
      Integer averageStudyMinutes,
      Integer averageAchievementRate,
      List<LocalDate> dates,
      boolean hypothesis) {}

  // 시간대 하나. timeOfDay: "morning"(05~12시) · "afternoon"(12~18시) · "evening"(18~22시) ·
  // "night"(22~05시)
  public record TimeOfDayItem(
      String timeOfDay,
      int sessionCount,
      Integer averageSessionMinutes,
      Integer pauseRate,
      List<LocalDate> dates,
      boolean hypothesis) {}

  // 이벤트 다음 날 효과. usual: 평소 날 / byEvent: 시험 · 약속·행사 순서
  public record AfterEvents(DayGroup usual, List<EventItem> byEvent) {}

  // 이벤트 종류 하나. event: "exam"(시험) · "event"(약속·행사, 체크인의 missReason 과 같은 글자)
  public record EventItem(
      String event,
      DayGroup nextDays,
      Integer studyMinutesDiff,
      Integer achievementRateDiff,
      boolean hypothesis) {}

  public static RhythmStatsResponse of(
      LocalDate from,
      LocalDate to,
      CollapseCycle cycle,
      RhythmEfficiency efficiency,
      AfterEventEffect afterEvent) {
    List<WeekdayItem> byWeekday = new ArrayList<>();
    for (int i = 0; i < efficiency.byWeekday().size(); i++) {
      DayGroup group = efficiency.byWeekday().get(i);
      byWeekday.add(
          new WeekdayItem(
              WEEKDAYS.get(i),
              group.averageStudyMinutes(),
              group.averageAchievementRate(),
              group.dates(),
              group.hypothesis()));
    }

    List<TimeOfDayItem> byTimeOfDay = new ArrayList<>();
    for (RhythmEfficiency.TimeGroup group : efficiency.byTimeOfDay()) {
      byTimeOfDay.add(
          new TimeOfDayItem(
              // MORNING → "morning". Locale.ROOT: 컴퓨터의 언어 설정에 따라 소문자가 달라지지 않게 합니다. (ExamType 과 같음)
              group.timeOfDay().name().toLowerCase(Locale.ROOT),
              group.sessionCount(),
              group.averageSessionMinutes(),
              group.pauseRate(),
              group.dates(),
              group.hypothesis()));
    }

    List<EventItem> byEvent = new ArrayList<>();
    for (AfterEventEffect.EventEffect effect : afterEvent.byEvent()) {
      String event = effect.type() == AfterEventEffect.EventType.EXAM ? "exam" : "event";
      byEvent.add(
          new EventItem(
              event,
              effect.nextDays(),
              effect.studyMinutesDiff(),
              effect.achievementRateDiff(),
              effect.hypothesis()));
    }

    return new RhythmStatsResponse(
        from,
        to,
        cycle.collapse(),
        cycle.recovery(),
        byWeekday,
        efficiency.weekdayVsWeekend(),
        byTimeOfDay,
        new AfterEvents(afterEvent.usual(), byEvent));
  }
}
