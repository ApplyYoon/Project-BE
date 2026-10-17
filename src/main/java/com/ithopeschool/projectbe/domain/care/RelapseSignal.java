package com.ithopeschool.projectbe.domain.care;

import com.ithopeschool.projectbe.domain.card.CardPriority;
import com.ithopeschool.projectbe.domain.cardresult.CardResult;
import com.ithopeschool.projectbe.domain.cardresult.JudgedCardDay;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 재발 신호 — 실수와 재발을 나눕니다. (최종 기획서 「5.2 재발 신호」, 이슈 #230)
//
// 카드 한 장 미실행 = **실수**: 기록만 하고 관리 단계는 움직이지 않습니다.
// 아래 중 하나라도 맞으면 **재발 신호**입니다.
//   ① 같은 루틴 카드 2회 연속 미실행 (미루기 또는 실패)
//   ② 최근 7일 실행률이 개인 기준선(직전 14일)의 70% 미만
//   ③ 절대 카드 미실행 1회
// 한 번의 실수를 크게 다루면 "이미 망쳤으니"(될 대로 되라 효과)로 번지기 때문에 나눕니다. (Marlatt, Lally 2010)
//
// 고정 일정(학원)은 카드가 아니라서 처음부터 들어오지 않고, 휴식 처리한 날(result REST)은 여기서 뺍니다.
// FE 의 getRelapseSignal (src/lib/relapseSignal.ts) 과 같은 규칙 · 같은 숫자입니다. 한쪽을 고치면 다른 쪽도 같이 고칩니다.
// ⚠️ 지금 관리 단계에 쓰는 MissStreak(하루 100% 미만 3일 연속)는 아직 그대로 둡니다. 이 판정으로 바꾸는 일은 #62 에서 합니다.
//
// hasSignal: 재발 신호가 있는지 / reasons: 맞은 조건들 / routineCardIds: ① 에 걸린 루틴 카드 번호
// recentRate · baselineRate: ② 계산에 쓴 최근 7일 · 기준선 실행률. 계산할 수 없으면 null
// mainCause: 최근 7일 미실행 원인 중 가장 많은 것 (관리 단계 판정에 씀). 없으면 null
public record RelapseSignal(
    boolean hasSignal,
    List<RelapseReason> reasons,
    List<Long> routineCardIds,
    Double recentRate,
    Double baselineRate,
    MissReason mainCause) {

  // ① 몇 번 연속이면 신호인지
  public static final int ROUTINE_MISS_STREAK = 2;
  // ② 최근 기간 (일)
  public static final int RECENT_DAYS = 7;
  // ② 기준선 기간 (일) — 최근 기간 바로 앞의 14일
  public static final int BASELINE_DAYS = 14;
  // ② 기준선의 몇 배 밑이면 신호인지 (70%)
  public static final double RATE_DROP_RATIO = 0.7;

  // 재발 신호를 판정합니다. 매일 1번(통화 때) 부릅니다.
  //
  // - today: 오늘 날짜
  // - since: 지난 판정 다음 날. ① · ③ 은 이날 이후의 미실행이 있을 때만 신호입니다
  //   (같은 일로 매일 다시 신호가 나지 않게). ② 는 기간 전체를 봅니다.
  // - ② 는 기록이 기준선 기간(14일)보다 짧으면 판정하지 않습니다.
  //
  // ■ 예시 (today = since = 2026-10-17)
  //   루틴 카드 A: 10/15 실패, 10/17 미루기                       → ① { reasons: [ROUTINE_TWICE],
  // routineCardIds: [A] }
  //   루틴 카드 A: 10/15 실패, 10/17 실행                          → 신호 없음 (연속 아님)
  //   루틴 카드 A: 10/13 실패, 10/15 실패 (since 10/17)            → 신호 없음 (어제까지 판정한 일)
  //   절대 카드 B: 10/17 실패                                      → ③ ABSOLUTE_MISS
  //   10/11~10/17 평균 실행률 0.5, 9/27~10/10 평균 0.8            → ② RATE_DROP (0.5 < 0.8 × 0.7 = 0.56)
  //   10/11~10/17 평균 실행률 0.6, 9/27~10/10 평균 0.8            → 신호 없음 (0.6 ≥ 0.56)
  //   휴식(REST) 처리한 날은 어느 계산에도 안 들어갑니다
  public static RelapseSignal judge(List<JudgedCardDay> allDays, LocalDate today, LocalDate since) {
    // 휴식 날과 오늘 뒤의 날은 뺍니다.
    List<JudgedCardDay> days = new ArrayList<>();
    for (JudgedCardDay day : allDays) {
      if (day.result() != CardResult.REST && !day.date().isAfter(today)) {
        days.add(day);
      }
    }
    List<RelapseReason> reasons = new ArrayList<>();

    // ① 루틴 카드마다 가장 최근 2번이 모두 미실행이고, 그중 최근 것이 since 이후
    // 루틴 카드 번호를 처음 나온 순서대로 모읍니다. (LinkedHashSet: 겹치지 않게 + 넣은 순서 그대로)
    Set<Long> routineIds = new LinkedHashSet<>();
    for (JudgedCardDay day : days) {
      if (day.isRoutine()) {
        routineIds.add(day.cardId());
      }
    }
    List<Long> routineCardIds = new ArrayList<>();
    for (Long cardId : routineIds) {
      List<JudgedCardDay> ofCard = new ArrayList<>();
      for (JudgedCardDay day : days) {
        if (day.cardId().equals(cardId)) {
          ofCard.add(day);
        }
      }
      // 최근 날짜가 앞으로 오게 줄 세우고 앞의 2개만 봅니다.
      ofCard.sort(Comparator.comparing(JudgedCardDay::date).reversed());
      List<JudgedCardDay> recent = ofCard.subList(0, Math.min(ROUTINE_MISS_STREAK, ofCard.size()));

      boolean allMissed = recent.size() == ROUTINE_MISS_STREAK;
      for (JudgedCardDay day : recent) {
        if (!day.result().isMiss()) {
          allMissed = false;
        }
      }
      if (allMissed && !recent.get(0).date().isBefore(since)) {
        routineCardIds.add(cardId);
      }
    }
    if (!routineCardIds.isEmpty()) {
      reasons.add(RelapseReason.ROUTINE_TWICE);
    }

    // ② 최근 7일 vs 직전 14일
    LocalDate recentFrom = today.minusDays(RECENT_DAYS - 1);
    LocalDate baselineTo = recentFrom.minusDays(1);
    LocalDate baselineFrom = baselineTo.minusDays(BASELINE_DAYS - 1);
    LocalDate firstDate = null;
    for (JudgedCardDay day : days) {
      if (firstDate == null || day.date().isBefore(firstDate)) {
        firstDate = day.date();
      }
    }
    // 가장 오래된 기록이 기준선 첫날이거나 그 전이어야 기준선이 있는 것입니다.
    boolean hasBaseline = firstDate != null && !firstDate.isAfter(baselineFrom);
    Double recentRate = hasBaseline ? averageRunRate(days, recentFrom, today) : null;
    Double baselineRate = hasBaseline ? averageRunRate(days, baselineFrom, baselineTo) : null;
    if (recentRate != null
        && baselineRate != null
        && baselineRate > 0
        && recentRate < baselineRate * RATE_DROP_RATIO) {
      reasons.add(RelapseReason.RATE_DROP);
    }

    // ③ 절대 카드 미실행 (since 이후)
    boolean absoluteMiss = false;
    for (JudgedCardDay day : days) {
      if (day.priority() == CardPriority.ABSOLUTE
          && day.result().isMiss()
          && !day.date().isBefore(since)) {
        absoluteMiss = true;
      }
    }
    if (absoluteMiss) {
      reasons.add(RelapseReason.ABSOLUTE_MISS);
    }

    // 최근 7일 미실행의 주원인
    List<JudgedCardDay> recentMisses = new ArrayList<>();
    for (JudgedCardDay day : days) {
      if (!day.date().isBefore(recentFrom) && day.result().isMiss()) {
        recentMisses.add(day);
      }
    }
    return new RelapseSignal(
        !reasons.isEmpty(),
        reasons,
        routineCardIds,
        recentRate,
        baselineRate,
        findMainCause(recentMisses));
  }

  // 기간(from ~ to, 둘 다 포함) 카드들의 평균 실행률. 카드가 없으면 null
  private static Double averageRunRate(List<JudgedCardDay> days, LocalDate from, LocalDate to) {
    double sum = 0;
    int count = 0;
    for (JudgedCardDay day : days) {
      if (!day.date().isBefore(from) && !day.date().isAfter(to)) {
        sum += day.runRate();
        count++;
      }
    }
    return count == 0 ? null : sum / count;
  }

  // 원인 하나가 몇 번 나왔는지와 가장 최근에 나온 날
  private record CauseCount(int count, LocalDate lastDate) {}

  // 가장 많이 나온 원인. 수가 같으면 더 최근에 나온 것, 그것도 같으면 먼저 나온 것. 원인이 하나도 없으면 null
  private static MissReason findMainCause(List<JudgedCardDay> days) {
    // LinkedHashMap: 원인을 처음 나온 순서대로 기억합니다. (FE 의 Map 과 같은 순서)
    Map<MissReason, CauseCount> counts = new LinkedHashMap<>();
    for (JudgedCardDay day : days) {
      if (day.missReason() == null) {
        continue;
      }
      CauseCount prev = counts.get(day.missReason());
      if (prev == null) {
        counts.put(day.missReason(), new CauseCount(1, day.date()));
      } else {
        LocalDate lastDate = day.date().isAfter(prev.lastDate()) ? day.date() : prev.lastDate();
        counts.put(day.missReason(), new CauseCount(prev.count() + 1, lastDate));
      }
    }

    MissReason best = null;
    CauseCount bestCount = null;
    for (Map.Entry<MissReason, CauseCount> entry : counts.entrySet()) {
      CauseCount value = entry.getValue();
      boolean isBetter =
          bestCount == null
              || value.count() > bestCount.count()
              || (value.count() == bestCount.count()
                  && value.lastDate().isAfter(bestCount.lastDate()));
      if (isBetter) {
        best = entry.getKey();
        bestCount = value;
      }
    }
    return best;
  }
}
