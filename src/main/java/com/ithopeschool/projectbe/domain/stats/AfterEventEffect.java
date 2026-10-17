package com.ithopeschool.projectbe.domain.stats;

import com.ithopeschool.projectbe.domain.stats.RhythmEfficiency.Day;
import com.ithopeschool.projectbe.domain.stats.RhythmEfficiency.DayGroup;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 이벤트가 있던 날의 "다음 날" 이 평소보다 공부가 잘 되는지를 계산합니다. (이슈 #37 분석 ② 리듬·패턴 중 이벤트 다음 날 효과)
// "시험 끝난 다음 날은 평소보다 40분 덜 해" 같은 문장의 재료입니다.
//
// 이벤트 종류 (서버에 데이터가 있는 것만)
//   - 시험: 시험 점수에 등록한 시험 날짜 (exams.exam_date)
//   - 약속·행사: 못 채운 이유로 '약속·행사'(event) 를 고른 날. 날짜는 실패 원인(#39)과 같이 맞춰서 넘깁니다.
//       체크인의 "어제 못 채운 이유" → 체크인 날짜의 하루 전 / 타이머를 멈추며 고른 이유 → 그 기록을 시작한 날
// ⚠️ "학원 늦게 끝난 날" 은 기록하는 칸이 서버에 없어서 아직 넣지 않았습니다.
//
// 정해 둔 규칙
//   1. "쓴 날" 만 셉니다. 계획도 공부도 없는 날은 "앱을 안 쓴 날" 이라 뺍니다. (요일·시간대 효율과 같은 규칙)
//   2. 다음 날 = 이벤트 날짜의 바로 다음 달력 날입니다. 그날이 "쓴 날" 이 아니면 비교할 것이 없어서 뺍니다.
//      같은 종류의 이벤트가 같은 날 여러 번 있어도 다음 날은 하루로 칩니다.
//   3. 평소 날 = 쓴 날 중에서 이벤트가 있던 날도 아니고, 이벤트 다음 날도 아닌 날입니다. (종류와 상관없이)
//      이벤트 당일(시험 날 등)도 평소와 다른 날이라서 뺍니다.
//   4. 평균 순공·평균 달성률은 요일·시간대 효율과 같은 계산입니다. (RhythmEfficiency.dayGroupOf)
//   5. 차이 = 다음 날 평균 − 평소 평균. 음수면 다음 날이 평소보다 덜 한 것입니다.
//      화면에 보이는 숫자끼리 맞도록 반올림한 평균끼리 뺍니다. 한쪽이라도 평균이 없으면 null 입니다.
//   6. 비교하는 두 쪽(다음 날, 평소 날) 중 하나라도 5일 미만이면 가설입니다. (이슈 #37 완료 조건)
//   7. 다음 날과 평소 날은 기간(from ~ to, 둘 다 포함) 안에서만 셉니다.
//      기간 첫날의 전날에 있던 이벤트는 다음 날이 기간 안이라 셉니다.
//
// 예시 (매일 계획이 있음)
//   평소 날 60분(100%) 5일, 시험 다음 날 20분(33%) 2일
//     →  시험 다음 날 평균 20분·33%, 평소 60분·100%, 차이 −40분·−67%p, 가설 (다음 날이 2일)
//
// usual: 평소 날 / byEvent: 이벤트 종류마다의 결과 (시험 · 약속·행사 순서)
public record AfterEventEffect(DayGroup usual, List<EventEffect> byEvent) {

  // 이벤트 종류입니다.
  public enum EventType {
    // 시험
    EXAM,
    // 약속·행사
    APPOINTMENT
  }

  // 이벤트 하나. date: 이벤트가 있던 날 / type: 종류
  public record Event(LocalDate date, EventType type) {}

  // 이벤트 종류 하나의 결과입니다.
  // type: 종류 / nextDays: 이벤트 다음 날들의 평균 (근거 날짜 = 다음 날들)
  // studyMinutesDiff: 순공 차이(분) / achievementRateDiff: 달성률 차이(%p). 계산할 수 없으면 null
  // hypothesis: 다음 날이나 평소 날이 5일 미만이면 true
  public record EventEffect(
      EventType type,
      DayGroup nextDays,
      Integer studyMinutesDiff,
      Integer achievementRateDiff,
      boolean hypothesis) {}

  // 이벤트 다음 날 효과를 계산합니다.
  //
  // days: 날짜마다의 순공과 달성률. 목록에 없는 날은 "앱을 안 쓴 날" 로 봅니다. 순서가 뒤섞여 있어도 됩니다.
  // events: 이벤트들. 기간 밖의 이벤트가 섞여 있어도 됩니다.
  public static AfterEventEffect of(
      List<Day> days, List<Event> events, LocalDate from, LocalDate to) {
    // 기간 안의 "쓴 날" 만 남깁니다. (규칙 1, 7)
    List<Day> usedDays = new ArrayList<>();
    for (Day day : days) {
      boolean inPeriod = !day.date().isBefore(from) && !day.date().isAfter(to);
      boolean used = day.achievementRate() != null || day.studyMinutes() > 0;
      if (inPeriod && used) {
        usedDays.add(day);
      }
    }
    usedDays.sort(Comparator.comparing(Day::date));

    // 평소 날에서 뺄 날들: 이벤트가 있던 날 + 이벤트 다음 날 (규칙 3)
    Set<LocalDate> notUsual = new HashSet<>();
    for (Event event : events) {
      notUsual.add(event.date());
      notUsual.add(event.date().plusDays(1));
    }
    List<Day> usualDays = new ArrayList<>();
    for (Day day : usedDays) {
      if (!notUsual.contains(day.date())) {
        usualDays.add(day);
      }
    }
    DayGroup usual = RhythmEfficiency.dayGroupOf(usualDays);

    List<EventEffect> byEvent = new ArrayList<>();
    for (EventType type : EventType.values()) {
      byEvent.add(effectOf(type, usedDays, events, usual));
    }
    return new AfterEventEffect(usual, List.copyOf(byEvent));
  }

  // 이벤트 종류 하나의 다음 날들을 평소 날과 견줍니다.
  private static EventEffect effectOf(
      EventType type, List<Day> usedDays, List<Event> events, DayGroup usual) {
    // 이 종류의 이벤트 다음 날들. Set 이라 같은 날은 한 번만 들어갑니다. (규칙 2)
    Set<LocalDate> nextDates = new HashSet<>();
    for (Event event : events) {
      if (event.type() == type) {
        nextDates.add(event.date().plusDays(1));
      }
    }
    // usedDays 가 날짜 순서라서 nextDays 도 날짜 순서가 됩니다.
    List<Day> nextDays = new ArrayList<>();
    for (Day day : usedDays) {
      if (nextDates.contains(day.date())) {
        nextDays.add(day);
      }
    }
    DayGroup next = RhythmEfficiency.dayGroupOf(nextDays);

    // 차이 (규칙 5)
    Integer studyMinutesDiff = diffOf(next.averageStudyMinutes(), usual.averageStudyMinutes());
    Integer achievementRateDiff =
        diffOf(next.averageAchievementRate(), usual.averageAchievementRate());
    return new EventEffect(
        type, next, studyMinutesDiff, achievementRateDiff, next.hypothesis() || usual.hypothesis());
  }

  // 다음 날 − 평소. 한쪽이라도 없으면 null 입니다.
  private static Integer diffOf(Integer next, Integer usual) {
    if (next == null || usual == null) {
      return null;
    }
    return next - usual;
  }
}
