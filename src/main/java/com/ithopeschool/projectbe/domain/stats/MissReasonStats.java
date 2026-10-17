package com.ithopeschool.projectbe.domain.stats;

import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.checkin.CheckInQuestions;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

// 계획을 못 채운 날의 원인을 셉니다. (이슈 #39 분석 ④ 실패 원인)
// 주요 원인 1~2개를 찾아야 그 원인에 맞는 개선안(#51)을 고를 수 있습니다.
//
// 정해 둔 규칙
//   1. "못 채운 날(미달)" 은 체크인 질문·통화 대본과 같은 기준입니다. 달성률이 100% 미만인 날이고,
//      계획이 없던 날(달성률 null)은 미달이 아닙니다. (CheckInQuestions.isMissedDay)
//   2. 원인은 부르는 쪽이 "그 일이 있던 날짜" 로 맞춰서 넘깁니다. (ReasonFact)
//        - 체크인의 "어제 못 채운 이유" → 체크인 날짜의 하루 전
//        - 타이머를 목표 전에 멈추며 고른 이유 → 그 기록을 시작한 날
//   3. 미달한 날의 원인만 셉니다. 다 채운 날이나 계획이 없던 날에 고른 이유는 "미달 원인" 이 아니라서 뺍니다.
//   4. 원인의 빈도는 "그 원인이 나온 미달한 날 수" 입니다. 같은 날 같은 원인이 두 번 나와도 하루로 칩니다.
//      (타이머에서도 '폰·SNS', 체크인에서도 '폰·SNS' 라고 했으면 하루. 한 번의 일을 두 번 세지 않게)
//      같은 날 서로 다른 원인이 나왔으면 둘 다 하루씩 셉니다.
//   5. 원인 목록은 날 수가 많은 것부터, 같으면 MissReason 에 적힌 순서대로입니다.
//   6. 주요 원인은 목록의 앞에서부터 최대 2개입니다. '기타' 는 어떤 개선안을 골라야 할지 알 수 없어서 뺍니다.
//   7. 요일 분포: 원인마다 그 원인이 나온 날을 요일별로 셉니다. 월요일부터 일요일까지 7칸입니다.
//   8. 회복 속도: 미달한 날부터 "다시 다 채운 날(달성률 100%)" 까지 걸린 날 수입니다.
//        - 날 수는 달력의 날짜 차이입니다. 10월 3일 미달 → 10월 5일 달성이면 2일입니다.
//        - 사이에 계획이 없던 날이 있어도 그냥 지나갑니다. (미달도 달성도 아니라서)
//        - 기간의 마지막 날까지 다시 다 채우지 못했으면 "아직 회복 안 됨" 으로 세고, 평균에서는 뺍니다.
//        - 원인의 평균 회복 속도는 그 원인이 나온 날들의 회복 날 수 평균입니다. 소수 첫째 자리까지, 마지막에 한 번만 반올림합니다.
//        - 회복한 날이 하나도 없으면 평균은 null 입니다.
//   9. 근거 날짜: 원인마다 그 원인이 나온 날짜들을 오래된 날부터 붙입니다.
//  10. 기간(from ~ to, 둘 다 포함) 밖의 날짜는 셀 때도, 회복을 찾을 때도 쓰지 않습니다.
//
// 예시 (10월 1일 ~ 10월 14일)
//   1일 미달 '폰·SNS', 2일 달성                        →  폰·SNS 1일, 회복 1일
//   3일 미달 '피곤', 4일 계획 없음, 5일 달성            →  피곤 1일, 회복 2일
//   13일 미달 '폰·SNS', 14일도 미달                     →  아직 회복 안 됨 1일 (평균에서 뺌)
//   6일 다 채웠는데 '폰·SNS' 를 고름                    →  미달이 아니라서 세지 않음
//
// ⚠️ 일기 키워드(#60)는 아직 일기가 없어서 넣지 않았습니다. 일기가 생기면 원인(ReasonFact)으로 더하면 됩니다.
// ⚠️ "시간대 분포" 는 넣지 않았습니다. 체크인의 원인은 "하루" 에 붙어 있어서 몇 시의 일인지 알 수 없기 때문입니다.
//
// from, to: 계산한 기간 (둘 다 포함) / missedDayCount: 기간 안에서 미달한 날 수 (원인을 안 고른 날 포함)
// reasons: 원인마다의 결과 (규칙 5 의 순서) / topReasons: 주요 원인 최대 2개 (규칙 6)
public record MissReasonStats(
    LocalDate from,
    LocalDate to,
    int missedDayCount,
    List<ReasonStat> reasons,
    List<MissReason> topReasons) {

  // 짧게 보는 기간 (최근 14일)
  public static final int SHORT_DAYS = 14;

  // 길게 보는 기간 (최근 30일)
  public static final int LONG_DAYS = 30;

  // 주요 원인은 최대 몇 개까지 고르는지
  public static final int MAX_TOP_REASONS = 2;

  // 하루의 달성률입니다. date: 날짜 / achievementRate: DaySummary 의 달성률. 계획이 없던 날은 null
  public record Day(LocalDate date, Integer achievementRate) {}

  // 원인 하나. date: 그 일이 있던 날 (규칙 2) / reason: 원인
  public record ReasonFact(LocalDate date, MissReason reason) {}

  // 원인 하나의 결과입니다.
  // reason: 원인 / dayCount: 그 원인이 나온 미달한 날 수
  // weekdayCounts: 요일별 날 수. 0번째 = 월요일 … 6번째 = 일요일
  // averageRecoveryDays: 평균 회복 속도(일). 회복한 날이 없으면 null
  // notRecoveredCount: 기간 끝까지 다시 다 채우지 못한 날 수
  // dates: 근거 날짜들 (오래된 날부터)
  public record ReasonStat(
      MissReason reason,
      int dayCount,
      List<Integer> weekdayCounts,
      Double averageRecoveryDays,
      int notRecoveredCount,
      List<LocalDate> dates) {}

  // to 까지 periodDays 일을 볼 때의 첫날입니다.   (10월 14일, 14일) → 10월 1일
  public static LocalDate fromDateOf(LocalDate to, int periodDays) {
    return to.minusDays(periodDays - 1);
  }

  // 기간의 실패 원인을 셉니다.
  //
  // days: 날짜마다의 달성률. 목록에 없는 날은 계획이 없던 날로 봅니다. 순서가 뒤섞여 있어도 됩니다.
  // facts: 원인들. 날짜는 "그 일이 있던 날" 로 맞춰서 넘깁니다. (규칙 2)
  public static MissReasonStats of(
      List<Day> days, List<ReasonFact> facts, LocalDate from, LocalDate to) {
    // 날짜로 그날의 달성률을 바로 찾을 수 있게 모아 둡니다.
    Map<LocalDate, Integer> rateByDate = new HashMap<>();
    for (Day day : days) {
      rateByDate.put(day.date(), day.achievementRate());
    }

    // 기간 안에서 미달한 날을 셉니다. (규칙 1)
    int missedDayCount = 0;
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      if (CheckInQuestions.isMissedDay(rateByDate.get(date))) {
        missedDayCount++;
      }
    }

    // 원인마다 "그 원인이 나온 미달한 날들". TreeSet 이라 같은 날은 한 번만, 오래된 날부터 들어갑니다. (규칙 3, 4, 9)
    Map<MissReason, TreeSet<LocalDate>> datesByReason = new EnumMap<>(MissReason.class);
    for (ReasonFact fact : facts) {
      boolean inPeriod = !fact.date().isBefore(from) && !fact.date().isAfter(to);
      if (fact.reason() == null
          || !inPeriod
          || !CheckInQuestions.isMissedDay(rateByDate.get(fact.date()))) {
        continue;
      }
      datesByReason.computeIfAbsent(fact.reason(), reason -> new TreeSet<>()).add(fact.date());
    }

    List<ReasonStat> reasons = new ArrayList<>();
    for (Map.Entry<MissReason, TreeSet<LocalDate>> entry : datesByReason.entrySet()) {
      reasons.add(statOf(entry.getKey(), entry.getValue(), rateByDate, to));
    }
    // 날 수가 많은 것부터. 같으면 EnumMap 이 이미 MissReason 순서로 넣어 두어서 그 순서가 유지됩니다. (규칙 5)
    reasons.sort((a, b) -> Integer.compare(b.dayCount(), a.dayCount()));

    // 주요 원인 (규칙 6)
    List<MissReason> topReasons = new ArrayList<>();
    for (ReasonStat stat : reasons) {
      if (topReasons.size() == MAX_TOP_REASONS) {
        break;
      }
      if (stat.reason() != MissReason.ETC) {
        topReasons.add(stat.reason());
      }
    }

    return new MissReasonStats(
        from, to, missedDayCount, List.copyOf(reasons), List.copyOf(topReasons));
  }

  // 원인 하나의 요일 분포와 회복 속도를 계산합니다.
  private static ReasonStat statOf(
      MissReason reason,
      TreeSet<LocalDate> dates,
      Map<LocalDate, Integer> rateByDate,
      LocalDate to) {
    // 요일 분포 (규칙 7). getValue() 는 월요일이 1, 일요일이 7 이라서 1 을 뺍니다.
    List<Integer> weekdayCounts = new ArrayList<>(List.of(0, 0, 0, 0, 0, 0, 0));
    // 회복 속도 (규칙 8)
    int recoveryDaySum = 0;
    int recoveredCount = 0;
    int notRecoveredCount = 0;
    for (LocalDate date : dates) {
      int weekday = date.getDayOfWeek().getValue() - 1;
      weekdayCounts.set(weekday, weekdayCounts.get(weekday) + 1);

      Integer recoveryDays = recoveryDaysOf(date, rateByDate, to);
      if (recoveryDays == null) {
        notRecoveredCount++;
      } else {
        recoveryDaySum += recoveryDays;
        recoveredCount++;
      }
    }

    // 소수 첫째 자리까지, 마지막에 한 번만 반올림합니다.
    Double averageRecoveryDays =
        recoveredCount == 0
            ? null
            : Math.round((double) recoveryDaySum / recoveredCount * 10) / 10.0;
    return new ReasonStat(
        reason,
        dates.size(),
        List.copyOf(weekdayCounts),
        averageRecoveryDays,
        notRecoveredCount,
        List.copyOf(dates));
  }

  // missedDate 다음 날부터 to 까지 보면서, 처음으로 다 채운 날까지 걸린 날 수를 돌려줍니다.
  // to 까지 다시 다 채운 날이 없으면 null 입니다. (규칙 8)
  //
  //   3일 미달, 4일 계획 없음, 5일 달성  →  2
  private static Integer recoveryDaysOf(
      LocalDate missedDate, Map<LocalDate, Integer> rateByDate, LocalDate to) {
    int passedDays = 0;
    for (LocalDate date = missedDate.plusDays(1); !date.isAfter(to); date = date.plusDays(1)) {
      passedDays++;
      Integer rate = rateByDate.get(date);
      // 계획이 있었고(null 이 아님) 미달이 아니면 다 채운 날입니다.
      if (rate != null && !CheckInQuestions.isMissedDay(rate)) {
        return passedDays;
      }
    }
    return null;
  }
}
