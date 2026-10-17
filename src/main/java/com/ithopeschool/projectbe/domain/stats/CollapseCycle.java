package com.ithopeschool.projectbe.domain.stats;

import com.ithopeschool.projectbe.domain.checkin.CheckInQuestions;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// "며칠째에 무너지고, 며칠 만에 회복하는지" 를 계산합니다. (이슈 #37 분석 ② 리듬·패턴 중 무너짐·회복 주기)
// "너는 3일째에 무너지는 편이야" 같은 문장의 재료입니다.
//
// 정해 둔 규칙
//   1. "채운 날" 과 "못 채운 날" 은 실패 원인(#39)·체크인 질문과 같은 기준입니다. (CheckInQuestions.isMissedDay)
//      채운 날 = 달성률 100%, 못 채운 날 = 100% 미만. 계획이 없던 날(달성률 null)은 어느 쪽도 아니라서 건너뜁니다.
//   2. 무너짐 = 채운 날이 이어지다가 처음으로 못 채운 날. "며칠째" 는 그 못 채운 날이 연속의 몇 번째 날인지입니다.
//        채움 → 채움 → 못 채움  →  3일째 무너짐 (근거 날짜 = 못 채운 날)
//      날을 셀 때 계획이 없던 날은 건너뜁니다. (금 채움 → 토 계획 없음 → 일 채움 → 월 못 채움 = 3일째)
//      못 채운 날이 이어지는 동안은 새로 무너진 것이 아니라서 다시 세지 않습니다.
//      기간의 첫 계획일부터 못 채웠으면, 그 앞에 며칠을 채웠는지 몰라서 무너짐으로 세지 않습니다.
//   3. 주로 무너지는 날 = 가장 많이 나온 "며칠째". 같으면 더 빠른 날입니다. (2일째와 3일째가 2번씩 → 2일째)
//   4. 회복 = 못 채운 날이 이어지다가 다시 채운 날. 걸린 날 수는 "처음 못 채운 날" 부터 "다시 채운 날" 까지의 달력 날짜 차이입니다.
//        10/3 못 채움 → 10/4 못 채움 → 10/6 채움  →  3일 만에 회복
//      실패 원인(#39)의 회복 속도와 같은 "달력 날짜 차이" 입니다. 다만 #39 는 못 채운 날마다 세고, 여기는 이어진 묶음마다 셉니다.
//      평균은 소수 첫째 자리까지, 마지막에 한 번만 반올림합니다. 회복한 적이 없으면 null 입니다.
//   5. 기간의 마지막 계획일까지 회복하지 못했으면 "아직 무너진 중" 이고, 그 묶음의 첫날을 slumpSince 에 넣습니다.
//   6. 근거가 5개 미만이면 가설입니다. (무너짐은 무너진 날 수, 회복은 회복한 횟수) 며칠 안 되는 기록으로 단정하지 않습니다.
//   7. 기간(from ~ to, 둘 다 포함) 밖의 날은 쓰지 않습니다.
//
// 예시 (10월, 매일 계획이 있음)
//   1·2 채움, 3 못 채움, 4·5 채움, 6 못 채움, 7·8 채움, 9 못 채움, 10 채움
//     →  무너짐 3번 모두 3일째 (근거 3일, 가설) / 회복 3번 모두 1일 만에
//   1 못 채움, 2 못 채움, 3 채움  →  무너짐 없음 (첫날부터 못 채움) / 회복 1번, 2일 만에
//
// collapse: 무너짐 쪽 결과 / recovery: 회복 쪽 결과
public record CollapseCycle(Collapse collapse, Recovery recovery) {

  // 근거가 이만큼은 있어야 "가설" 딱지를 뗍니다.
  public static final int MIN_SAMPLES = 5;

  // 하루의 달성률입니다. date: 날짜 / achievementRate: DaySummary 의 달성률. 계획이 없던 날은 null
  public record Day(LocalDate date, Integer achievementRate) {}

  // 무너짐 쪽 결과입니다.
  // commonDay: 주로 무너지는 "며칠째" (규칙 3). 무너진 적이 없으면 null
  // commonDayCount: 그 "며칠째" 에 무너진 횟수
  // events: 무너진 날마다 하나씩 (오래된 날부터) / hypothesis: 근거가 5개 미만이면 true
  public record Collapse(
      Integer commonDay, int commonDayCount, List<CollapseEvent> events, boolean hypothesis) {}

  // 무너진 날 하나. date: 못 채운 날 (근거 날짜) / dayNumber: 연속의 몇 번째 날에 무너졌는지
  public record CollapseEvent(LocalDate date, int dayNumber) {}

  // 회복 쪽 결과입니다.
  // averageDays: 평균 회복 날 수 (소수 첫째 자리). 회복한 적이 없으면 null
  // events: 회복마다 하나씩 (오래된 것부터)
  // slumpSince: 아직 무너진 중이면 그 묶음의 첫날, 아니면 null / hypothesis: 근거가 5개 미만이면 true
  public record Recovery(
      Double averageDays, List<RecoveryEvent> events, LocalDate slumpSince, boolean hypothesis) {}

  // 회복 하나. missedFrom: 처음 못 채운 날 / recoveredOn: 다시 채운 날 / days: 걸린 날 수
  public record RecoveryEvent(LocalDate missedFrom, LocalDate recoveredOn, int days) {}

  // 기간의 무너짐·회복 주기를 계산합니다.
  //
  // days: 날짜마다의 달성률. 목록에 없는 날은 계획이 없던 날로 봅니다. 순서가 뒤섞여 있어도 됩니다.
  public static CollapseCycle of(List<Day> days, LocalDate from, LocalDate to) {
    List<Day> sorted = new ArrayList<>();
    for (Day day : days) {
      boolean inPeriod = !day.date().isBefore(from) && !day.date().isAfter(to);
      // 기간 밖의 날과 계획이 없던 날은 건너뜁니다. (규칙 1, 7)
      if (inPeriod && day.achievementRate() != null) {
        sorted.add(day);
      }
    }
    sorted.sort(Comparator.comparing(Day::date));

    List<CollapseEvent> collapses = new ArrayList<>();
    List<RecoveryEvent> recoveries = new ArrayList<>();
    // 지금까지 이어서 채운 날 수
    int achievedRun = 0;
    // 못 채운 날이 이어지는 중이면 그 첫날, 아니면 null
    LocalDate slumpSince = null;

    for (Day day : sorted) {
      if (CheckInQuestions.isMissedDay(day.achievementRate())) {
        // 채운 날이 이어지다가 처음 못 채운 날이면 무너짐입니다. (규칙 2)
        if (slumpSince == null && achievedRun > 0) {
          collapses.add(new CollapseEvent(day.date(), achievedRun + 1));
        }
        if (slumpSince == null) {
          slumpSince = day.date();
        }
        achievedRun = 0;
      } else {
        // 못 채운 날이 이어지다가 다시 채운 날이면 회복입니다. (규칙 4)
        if (slumpSince != null) {
          int recoveryDays = (int) ChronoUnit.DAYS.between(slumpSince, day.date());
          recoveries.add(new RecoveryEvent(slumpSince, day.date(), recoveryDays));
          slumpSince = null;
        }
        achievedRun++;
      }
    }

    return new CollapseCycle(collapseOf(collapses), recoveryOf(recoveries, slumpSince));
  }

  // 주로 무너지는 날을 고릅니다. (규칙 3)
  private static Collapse collapseOf(List<CollapseEvent> events) {
    // "며칠째" 마다 몇 번 나왔는지. TreeMap 이라 작은 날부터 돌아서, 횟수가 같으면 먼저 본 더 빠른 날이 남습니다.
    Map<Integer, Integer> countByDay = new TreeMap<>();
    for (CollapseEvent event : events) {
      countByDay.merge(event.dayNumber(), 1, Integer::sum);
    }
    Integer commonDay = null;
    int commonDayCount = 0;
    for (Map.Entry<Integer, Integer> entry : countByDay.entrySet()) {
      if (entry.getValue() > commonDayCount) {
        commonDay = entry.getKey();
        commonDayCount = entry.getValue();
      }
    }
    return new Collapse(
        commonDay, commonDayCount, List.copyOf(events), events.size() < MIN_SAMPLES);
  }

  // 평균 회복 날 수를 계산합니다. (규칙 4, 5)
  private static Recovery recoveryOf(List<RecoveryEvent> events, LocalDate slumpSince) {
    Double averageDays = null;
    if (!events.isEmpty()) {
      int sum = 0;
      for (RecoveryEvent event : events) {
        sum += event.days();
      }
      // 소수 첫째 자리까지, 마지막에 한 번만 반올림합니다.
      averageDays = Math.round((double) sum / events.size() * 10) / 10.0;
    }
    return new Recovery(averageDays, List.copyOf(events), slumpSince, events.size() < MIN_SAMPLES);
  }
}
