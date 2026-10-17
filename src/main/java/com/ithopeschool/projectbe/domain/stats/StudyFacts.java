package com.ithopeschool.projectbe.domain.stats;

import com.ithopeschool.projectbe.domain.session.StudyTime;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 계획과 공부 기록만으로 어떤 기간의 "객관적인 숫자(팩트)" 를 계산합니다. (이슈 #36)
// 사용자에게 따로 묻지 않고, 타이머와 계획에서 나온 값만 씁니다.
// 숫자는 코드가 계산하고, AI 는 그 숫자를 말로 풀기만 합니다.
//
// 정해 둔 규칙
//   1. 날짜는 한국 시간 기준이고, 시작 날짜와 끝 날짜를 모두 포함합니다.
//      하루는 새벽 4시에 시작합니다. 00:00 ~ 03:59 에 시작한 기록은 전날 것입니다. (DayRule)
//   2. 계획은 계획의 날짜가, 기록은 "시작한 날" 이 기간 안에 있으면 셉니다.
//   3. 순공시간은 StudyTime.studyMillis 로 구한 값을 받습니다. (끝 − 시작 − 일시정지한 시간)
//   4. 계획 대비 실제 = 기간의 순공 합 ÷ 기간의 계획 목표 시간 합 × 100. 소수점은 버립니다.
//      계획 없이 켠 타이머의 순공도 "실제" 에 들어갑니다. 100 을 넘을 수 있습니다. (계획보다 더 한 것도 사실이라 자르지 않습니다)
//      계획 목표 시간의 합이 0 이면 나눌 수 없어서 null 입니다.
//   5. "목표가 있는 타이머" = 목표 시간이 0 보다 큰 공부 기록입니다.
//   6. 완주 = 그 기록의 순공시간이 약속한 시간(목표) 이상입니다.
//      완주율 = 완주한 수 ÷ 목표가 있는 타이머 수 × 100. 소수점은 버립니다. 목표가 있는 타이머가 없으면 null 입니다.
//   7. 시작 지연 = 그 계획으로 처음 켠 타이머의 시작 시각 − 계획의 시작 시각. (계획에 시작 시각이 있을 때만)
//      계획의 시작 시각이 00:00 ~ 03:59 이면 달력으로는 다음 날 새벽입니다. (10/5 계획의 01:00 = 10/6 01:00)
//      계획보다 일찍 시작했으면 음수가 아니라 0분으로 칩니다.
//      일찍 시작한 날이 늦게 시작한 날을 지워서, 평균이 "늦지 않았다" 처럼 보이면 안 되기 때문입니다.
//      한 번도 시작하지 않은 계획은 "늦은 것" 이 아니라 "안 한 것" 이라 표본에서 뺍니다.
//      평균(분)은 모두 더해서 나눈 뒤 마지막에 한 번만 반올림합니다. 표본이 없으면 null 입니다.
//   8. 멈추는 지점 = 완주하지 못한 "목표가 있는 타이머" 가 목표의 몇 % 에서 끝났는지(순공 ÷ 목표)의 평균.
//      소수점은 마지막에 한 번만 버립니다. 표본이 없으면 null 입니다.
//   9. 요일별·시간대별 완주율은 기록을 "시작한 시각" 의 요일·시간대로 나눠서 규칙 6 을 그대로 씁니다.
//      요일은 규칙 1 의 "그 기록이 속한 날" 의 요일이고(화요일 새벽 1시 = 월요일), 시간대는 실제 시계 시각입니다(새벽 1시 = 밤).
//
// 예시 (기간은 2026-10-05(월) ~ 2026-10-11(일))
//   계획: 월 60분, 화 90분 (합 150분) / 기록: 월 순공 40분, 화 순공 56분 (합 96분)
//     →  계획 대비 실제 64% (계획 2개로 계산)
//   기록 3개: 목표 50분에 순공 50분(완주) / 목표 50분에 순공 31분(멈춤) / 목표 25분에 순공 30분(완주)
//     →  완주 2번, 완주율 66% (3번 중), 멈추는 지점 62% (1번으로 계산)
//   계획 A 는 19:00 인데 19:23 에 시작, 계획 B 는 21:00 인데 20:50 에 시작(일찍 → 0분)
//     →  평균 시작 지연 12분   ((23 + 0) ÷ 2 = 11.5 → 반올림 12)
//
// FE 의 getFacts (src/lib/facts.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 숫자가 어긋나지 않습니다.
//
// plannedMinutes: 계획한 시간의 합(분)
// studyMinutes: 실제 순공시간의 합(분)
// planRate: 계획 대비 실제(%). 표본 수 = 공부 계획의 개수
// completedCount: 목표가 있는 타이머 중 목표 시간을 채운 횟수 ("20번 중 11번" 의 11)
// completionRate: 완주율(%). 표본 수 = 목표가 있는 타이머의 개수
// startDelayMinutes: 평균 시작 지연(분). 표본 수 = 시작 시각이 있고 실제로 시작한 계획의 개수
// stopPoint: 멈추는 지점(%). 표본 수 = 중간에 그만둔 타이머의 개수
// completionByWeekday: 요일별 완주율. 0번째 = 일요일, 1번째 = 월요일 … 6번째 = 토요일
// completionByTimeOfDay: 시간대별 완주율
public record StudyFacts(
    int plannedMinutes,
    int studyMinutes,
    FactValue planRate,
    int completedCount,
    FactValue completionRate,
    FactValue startDelayMinutes,
    FactValue stopPoint,
    List<FactValue> completionByWeekday,
    Map<TimeOfDay, FactValue> completionByTimeOfDay) {

  // 시간대를 나누는 시각(시)입니다. 타이머를 "시작한 시각" 으로 나눕니다.
  //   아침 05:00 ~ 11:59 / 낮 12:00 ~ 17:59 / 저녁 18:00 ~ 21:59 / 밤 22:00 ~ 04:59
  public static final int MORNING_START_HOUR = 5;
  public static final int AFTERNOON_START_HOUR = 12;
  public static final int EVENING_START_HOUR = 18;
  public static final int NIGHT_START_HOUR = 22;

  // 하루가 시작되는 시각(새벽 4시). DayRule 과 같은 값입니다.
  private static final int DAY_START_HOUR = 4;

  private static final long MILLIS_PER_MINUTE = 60_000;

  // 배포 서버는 보통 UTC 로 돌아가서, 서버 컴퓨터의 시간대에 기대지 않고 한국 시간을 직접 지정합니다.
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  // 시간대입니다.
  public enum TimeOfDay {
    // 아침
    MORNING,
    // 낮
    AFTERNOON,
    // 저녁
    EVENING,
    // 밤
    NIGHT
  }

  // 숫자 하나와, 그 숫자를 몇 개의 기록으로 계산했는지(표본 수)입니다.
  // value: 계산한 값. 계산할 수 없으면 0 이 아니라 null 입니다. ("0%" 와 "모름" 은 다릅니다)
  // sampleCount: 이 값을 계산하는 데 쓴 기록(또는 계획)의 개수
  public record FactValue(Integer value, int sampleCount) {

    // 계산할 수 없을 때의 값입니다.
    public static final FactValue UNKNOWN = new FactValue(null, 0);
  }

  // 공부 계획 하나에서 계산에 쓰는 값만 모은 것입니다.
  // id: 계획 번호 / date: 계획한 날짜 / targetMinutes: 목표 공부 시간(분)
  // startTime: 시작하기로 한 시각. 정하지 않았으면 null
  public record FactPlan(Long id, LocalDate date, int targetMinutes, LocalTime startTime) {}

  // 공부 기록 하나에서 계산에 쓰는 값만 모은 것입니다.
  // planId: 어느 계획으로 켠 타이머인지. 계획 없이 켰으면 null
  // startedAt: 시작한 시각 / studyMillis: 순공시간(밀리초). StudyTime.studyMillis 의 결과를 넣습니다.
  // targetMinutes: 타이머를 켤 때 정한 목표(분). 목표 없이 했으면 0
  // (공부 기록 테이블이 아직 바뀌는 중이라, 엔티티가 아니라 이 계산에 필요한 값만 받습니다)
  public record FactSession(Long planId, Instant startedAt, long studyMillis, int targetMinutes) {}

  // 완주율을 세는 통입니다. total = 목표가 있는 타이머 수, completed = 그중 목표를 채운 수
  private static final class CompletionCount {
    private int total;
    private int completed;
  }

  // 시(0 ~ 23)가 어느 시간대인지 알려줍니다.
  //
  //   7 → 아침 / 12 → 낮 / 19 → 저녁 / 23 → 밤 / 2 → 밤
  public static TimeOfDay timeOfDay(int hour) {
    if (hour >= MORNING_START_HOUR && hour < AFTERNOON_START_HOUR) {
      return TimeOfDay.MORNING;
    }
    if (hour >= AFTERNOON_START_HOUR && hour < EVENING_START_HOUR) {
      return TimeOfDay.AFTERNOON;
    }
    if (hour >= EVENING_START_HOUR && hour < NIGHT_START_HOUR) {
      return TimeOfDay.EVENING;
    }
    return TimeOfDay.NIGHT;
  }

  // 계획의 날짜와 시작 시각을 합쳐 "실제로 시작하기로 한 순간" 을 만듭니다. 시작 시각을 안 정했으면 null 입니다.
  // 하루는 새벽 4시에 시작하므로, 00:00 ~ 03:59 는 달력으로 다음 날 새벽입니다. (10/5 계획의 01:00 = 10/6 01:00)
  public static Instant plannedStartAt(FactPlan plan) {
    if (plan.startTime() == null) {
      return null;
    }
    LocalDate calendarDay =
        plan.startTime().getHour() < DAY_START_HOUR ? plan.date().plusDays(1) : plan.date();
    return calendarDay.atTime(plan.startTime()).atZone(SEOUL).toInstant();
  }

  // 기간(startDate ~ endDate, 두 날짜 모두 포함) 동안의 팩트 숫자를 계산합니다.
  //
  // plans, sessions: 다른 날짜가 섞여 있어도 됩니다. 이 함수가 기간에 해당하는 것만 골라서 계산합니다.
  //   노는 계획과 노는 기록은 공부가 아니라서, 부르는 쪽에서 빼고 넘깁니다. (FE 는 함수 안에서 걸러냅니다)
  //   시작 지연을 맞게 세려면 기간 바로 다음 날 새벽의 기록까지 넘겨야 합니다. (일요일 23:50 계획을 월요일 00:10 에 시작한 경우)
  public static StudyFacts of(
      List<FactPlan> plans, List<FactSession> sessions, LocalDate startDate, LocalDate endDate) {
    // 기간 안의 계획과 기록만 고릅니다. (규칙 1, 2)
    List<FactPlan> periodPlans = new ArrayList<>();
    for (FactPlan plan : plans) {
      if (isInPeriod(plan.date(), startDate, endDate)) {
        periodPlans.add(plan);
      }
    }
    List<FactSession> periodSessions = new ArrayList<>();
    for (FactSession session : sessions) {
      if (isInPeriod(DayRule.dateOf(session.startedAt()), startDate, endDate)) {
        periodSessions.add(session);
      }
    }

    // ─── 계획 대비 실제 (규칙 4) ───
    int plannedMinutes = 0;
    for (FactPlan plan : periodPlans) {
      plannedMinutes += plan.targetMinutes();
    }
    long studyMillis = 0;
    for (FactSession session : periodSessions) {
      studyMillis += session.studyMillis();
    }

    // FE 와 같은 순서(먼저 나누고 100 을 곱한 뒤 소수점 버림)로 계산합니다.
    FactValue planRate =
        plannedMinutes > 0
            ? new FactValue(
                (int) Math.floor((double) studyMillis / (plannedMinutes * MILLIS_PER_MINUTE) * 100),
                periodPlans.size())
            : FactValue.UNKNOWN;

    // ─── 완주율 · 멈추는 지점 · 요일별 · 시간대별 (규칙 5, 6, 8, 9) ───
    CompletionCount overall = new CompletionCount();
    List<CompletionCount> byWeekday = new ArrayList<>();
    for (int weekday = 0; weekday < 7; weekday++) {
      byWeekday.add(new CompletionCount());
    }
    Map<TimeOfDay, CompletionCount> byTimeOfDay = new EnumMap<>(TimeOfDay.class);
    for (TimeOfDay timeOfDay : TimeOfDay.values()) {
      byTimeOfDay.put(timeOfDay, new CompletionCount());
    }
    // 완주하지 못한 타이머가 목표의 몇 % 에서 멈췄는지를 모두 더한 값과 그 개수
    double stopPercentSum = 0;
    int stoppedCount = 0;

    for (FactSession session : periodSessions) {
      // 목표가 있는 타이머만 봅니다. (규칙 5)
      if (session.targetMinutes() <= 0) {
        continue;
      }
      long targetMillis = session.targetMinutes() * MILLIS_PER_MINUTE;
      boolean isCompleted = session.studyMillis() >= targetMillis;

      // 요일은 "그 기록이 속한 날"(새벽 4시 기준)의 요일, 시간대는 실제 시계 시각으로 봅니다. (규칙 9)
      // getDayOfWeek 는 월 1 ~ 일 7 이라서, 7 로 나눈 나머지로 일 0 ~ 토 6 으로 바꿉니다.
      int weekday = DayRule.dateOf(session.startedAt()).getDayOfWeek().getValue() % 7;
      int startHour = session.startedAt().atZone(SEOUL).getHour();
      List<CompletionCount> buckets =
          List.of(overall, byWeekday.get(weekday), byTimeOfDay.get(timeOfDay(startHour)));
      for (CompletionCount bucket : buckets) {
        bucket.total++;
        if (isCompleted) {
          bucket.completed++;
        }
      }

      if (!isCompleted) {
        stopPercentSum += (double) session.studyMillis() / targetMillis * 100;
        stoppedCount++;
      }
    }

    FactValue stopPoint =
        stoppedCount > 0
            ? new FactValue((int) Math.floor(stopPercentSum / stoppedCount), stoppedCount)
            : FactValue.UNKNOWN;

    // ─── 평균 시작 지연 (규칙 7) ───
    // 계획마다 "그 계획으로 처음 켠 타이머의 시작 시각" 을 찾아 둡니다. 기간 밖의 기록도 봅니다.
    // (일요일 23:50 계획을 월요일 00:10 에 시작한 경우도 20분 늦은 것으로 세기 위해서입니다)
    Map<Long, Instant> firstStartByPlanId = new HashMap<>();
    for (FactSession session : sessions) {
      if (session.planId() == null) {
        continue;
      }
      Instant known = firstStartByPlanId.get(session.planId());
      if (known == null || session.startedAt().isBefore(known)) {
        firstStartByPlanId.put(session.planId(), session.startedAt());
      }
    }

    long delayMillisSum = 0;
    int delayCount = 0;
    for (FactPlan plan : periodPlans) {
      Instant plannedStartAt = plannedStartAt(plan);
      Instant firstStartAt = firstStartByPlanId.get(plan.id());
      // 시작 시각을 안 정했거나, 한 번도 시작하지 않은 계획은 뺍니다.
      if (plannedStartAt == null || firstStartAt == null) {
        continue;
      }
      // 일찍 시작했으면 0 으로 칩니다. (Math.max 가 음수를 0 으로 바꿉니다)
      delayMillisSum += Math.max(0, Duration.between(plannedStartAt, firstStartAt).toMillis());
      delayCount++;
    }

    FactValue startDelayMinutes =
        delayCount > 0
            ? new FactValue(
                (int) Math.round((double) delayMillisSum / delayCount / MILLIS_PER_MINUTE),
                delayCount)
            : FactValue.UNKNOWN;

    // ─── 결과 묶기 ───
    List<FactValue> completionByWeekday = new ArrayList<>();
    for (CompletionCount count : byWeekday) {
      completionByWeekday.add(toCompletionRate(count));
    }
    Map<TimeOfDay, FactValue> completionByTimeOfDay = new EnumMap<>(TimeOfDay.class);
    for (TimeOfDay timeOfDay : TimeOfDay.values()) {
      completionByTimeOfDay.put(timeOfDay, toCompletionRate(byTimeOfDay.get(timeOfDay)));
    }

    return new StudyFacts(
        plannedMinutes,
        StudyTime.toMinutes(studyMillis),
        planRate,
        overall.completed,
        toCompletionRate(overall),
        startDelayMinutes,
        stopPoint,
        completionByWeekday,
        completionByTimeOfDay);
  }

  // 날짜가 기간 안(시작과 끝 포함)인지 봅니다.
  private static boolean isInPeriod(LocalDate date, LocalDate startDate, LocalDate endDate) {
    return !date.isBefore(startDate) && !date.isAfter(endDate);
  }

  // 통에 담긴 수로 완주율(%)을 만듭니다. 하나도 없으면 null 입니다. 소수점은 버립니다.
  private static FactValue toCompletionRate(CompletionCount count) {
    if (count.total == 0) {
      return FactValue.UNKNOWN;
    }
    return new FactValue(
        (int) Math.floor((double) count.completed / count.total * 100), count.total);
  }
}
