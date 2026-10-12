package com.ithopeschool.projectbe.domain.care;

import com.ithopeschool.projectbe.domain.plan.MissStreak;
import com.ithopeschool.projectbe.domain.safety.LowMoodStreak;
import com.ithopeschool.projectbe.domain.suggestion.ExperimentProgress;
import com.ithopeschool.projectbe.domain.suggestion.ExperimentSummary;
import com.ithopeschool.projectbe.domain.suggestion.ExperimentSummary.KeptCheck;
import com.ithopeschool.projectbe.domain.summary.DaySummary;
import com.ithopeschool.projectbe.domain.summary.DaySummary.DayPlan;
import com.ithopeschool.projectbe.domain.summary.DaySummary.DaySession;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// 오늘의 대응(CareDecision)에 넣을 재료를 기록에서 모으고, 대응까지 정합니다. (이슈 #55)
// CareDecision.decide 는 "재료 5개가 있으면 무엇을 할지" 만 정합니다. 그 재료를 어디서 구하는지가 여기 있습니다.
//
//   이행 판정      기간이 끝난 개선안의 이행 기록     (ExperimentSummary → SuggestionRate)
//   효과 판정      그 개선안의 실험 기간 vs 직전 2주  (ExperimentSummary → ExperimentJudge)
//   못 채운 이유   오늘·어제 체크인에 고른 이유
//   기분           오늘·어제 체크인의 기분
//   연속 미달 일수  계획과 공부 기록                  (MissStreak)
//
// 정해 둔 규칙
//   1. 이행·효과 판정은 "실험 기간이 끝난" 개선안만 씁니다. (기획서 4장: 실험 기간 중에는 바꾸지 않는다)
//      끝난 개선안이 여럿이면 가장 최근에 끝난 것 하나입니다. 끝난 날이 같으면 목록에서 먼저 나온 것입니다.
//        10월 1일 시작, 14일  →  10월 14일까지가 실험 기간이라, 10월 15일부터 "끝난" 개선안
//   2. 끝난 개선안이 없거나, 그 개선안의 이행 기록이 하나도 없으면 대응을 정하지 않습니다. (null)
//      판정할 재료가 없는데 당근이나 채찍을 고르면 안 되기 때문입니다.
//   3. 체크인은 오늘 것을 보고, 오늘 아직 안 했으면 어제 것을 봅니다. 그보다 옛날 것은 보지 않습니다.
//      못 채운 이유와 기분은 "가장 최근 체크인 하나" 에서만 꺼냅니다.
//   4. 기분이 2 이하면 "기분이 낮다" 로 봅니다. (1 ~ 5 중 1, 2. 안전 브레이크와 같은 기준)
//   5. 연속 미달 일수는 어제부터 거슬러 올라가며 셉니다. 계획이 없던 날이나 다 채운 날을 만나면 멈춥니다.
//
// FE 의 pickFinishedSuggestion · getMainMissCause · isMoodLow · getTodayCareDecision
// (src/lib/careInputs.ts),
// countConsecutiveMissDays (src/lib/goalAdjustInput.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 대응이 어긋나지 않습니다.
public final class CareInputs {

  // 도전 중인 개선안 하나에서 필요한 값만 모은 것입니다. 시작일과 기간이 정해진 것만 넘깁니다.
  // id: 개선안 번호 / startDate: 도전을 시작한 날 / days: 실험 기간 일수
  // (개선안 테이블은 아직 없어서, 엔티티가 아니라 이 계산에 필요한 값만 받습니다)
  public record ActiveSuggestion(Long id, LocalDate startDate, int days) {}

  // 개선안을 그날 지켰는지 적은 기록 하나입니다.
  // suggestionId: 어느 개선안인지 / date: 지켰는지 묻는 날 / kept: 지켰으면 true
  public record SuggestionCheck(Long suggestionId, LocalDate date, boolean kept) {}

  // 체크인 하나에서 필요한 값만 모은 것입니다. 날짜마다 하나씩 넘깁니다.
  // date: 체크인한 날 / mood: 기분 1(많이 처짐) ~ 5(아주 좋음) / missReason: 어제 계획을 못 채운 이유. 안 골랐으면 null
  public record DayCheckIn(LocalDate date, int mood, MissReason missReason) {}

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private CareInputs() {}

  // 오늘의 대응(행동과 이유)을 정합니다. 판정할 재료가 없으면 null 입니다. (규칙 2)
  //
  // activeSuggestions: 도전 중인 개선안들 (시작일·기간이 있는 것만)
  // checks: 이행 기록들. 다른 개선안의 기록이 섞여 있어도 됩니다.
  // checkIns: 체크인들. 오늘과 어제 것만 있으면 됩니다.
  // plans, sessions: 계획과 공부 기록. 끝난 개선안의 기준선 첫날부터 어제까지가 들어 있어야 합니다.
  //   노는 계획과 노는 기록은 부르는 쪽에서 빼고 넘깁니다.
  // today: 오늘 날짜 (하루의 기준인 새벽 4시로 정한 날짜, DayRule)
  public static CareDecision todayDecision(
      List<ActiveSuggestion> activeSuggestions,
      List<SuggestionCheck> checks,
      List<DayCheckIn> checkIns,
      List<DayPlan> plans,
      List<DaySession> sessions,
      LocalDate today) {
    ActiveSuggestion finished = pickFinished(activeSuggestions, today);
    if (finished == null) {
      return null;
    }

    // 그 개선안의 이행 기록만 골라서, 실험 결과(이행 판정·효과 판정)를 계산합니다.
    List<KeptCheck> keptChecks = new ArrayList<>();
    for (SuggestionCheck check : checks) {
      if (check.suggestionId().equals(finished.id())) {
        keptChecks.add(new KeptCheck(check.date(), check.kept()));
      }
    }
    ExperimentSummary summary =
        ExperimentSummary.of(finished.startDate(), finished.days(), plans, sessions, keptChecks);
    if (summary.fulfillment() == null) {
      return null;
    }

    return CareDecision.decide(
        summary.fulfillment().grade(),
        summary.effect(),
        mainMissReason(checkIns, today),
        consecutiveMissDays(plans, sessions, today),
        isMoodLow(checkIns, today));
  }

  // 실험 기간이 끝난 개선안 중 가장 최근에 끝난 것 하나를 고릅니다. 없으면 null 입니다. (규칙 1)
  public static ActiveSuggestion pickFinished(
      List<ActiveSuggestion> activeSuggestions, LocalDate today) {
    ActiveSuggestion latest = null;
    LocalDate latestEnd = null;
    for (ActiveSuggestion suggestion : activeSuggestions) {
      if (!ExperimentProgress.isFinished(suggestion.startDate(), suggestion.days(), today)) {
        continue;
      }
      // 끝난 날(시작일 + 기간)이 더 늦을 때만 바꾸므로, 같으면 먼저 나온 개선안이 남습니다.
      LocalDate end = ExperimentProgress.resultDateOf(suggestion.startDate(), suggestion.days());
      if (latest == null || end.isAfter(latestEnd)) {
        latest = suggestion;
        latestEnd = end;
      }
    }
    return latest;
  }

  // 오늘·어제 체크인 중 가장 최근 것을 돌려줍니다. 둘 다 없으면 null 입니다. (규칙 3)
  public static DayCheckIn latestCheckIn(List<DayCheckIn> checkIns, LocalDate today) {
    DayCheckIn yesterdays = null;
    for (DayCheckIn checkIn : checkIns) {
      if (checkIn.date().equals(today)) {
        return checkIn;
      }
      if (yesterdays == null && checkIn.date().equals(today.minusDays(1))) {
        yesterdays = checkIn;
      }
    }
    return yesterdays;
  }

  // 가장 최근 체크인(오늘·어제)에 고른 "못 채운 이유" 를 돌려줍니다. 체크인이 없거나 안 골랐으면 null 입니다.
  public static MissReason mainMissReason(List<DayCheckIn> checkIns, LocalDate today) {
    DayCheckIn latest = latestCheckIn(checkIns, today);
    return latest == null ? null : latest.missReason();
  }

  // 가장 최근 체크인(오늘·어제)의 기분이 낮으면 true 입니다. 체크인이 없으면 false 입니다. (규칙 4)
  public static boolean isMoodLow(List<DayCheckIn> checkIns, LocalDate today) {
    DayCheckIn latest = latestCheckIn(checkIns, today);
    return latest != null && latest.mood() <= LowMoodStreak.LOW_MOOD_MAX;
  }

  // 어제부터 거슬러 올라가며, 계획을 못 채운 날이 연속으로 며칠인지 셉니다. (규칙 5)
  //
  //   어제 50%, 그저께 80%, 3일 전 100%  →  2
  //   어제 계획 없음                     →  0
  public static int consecutiveMissDays(
      List<DayPlan> plans, List<DaySession> sessions, LocalDate today) {
    // "어제, 그저께, 3일 전 …" 순서로 30일의 달성률을 만듭니다. 계획이 없던 날은 null 입니다.
    List<Integer> ratesFromYesterday = new ArrayList<>();
    for (int daysAgo = 1; daysAgo <= MissStreak.MAX_LOOKBACK_DAYS; daysAgo++) {
      LocalDate date = today.minusDays(daysAgo);
      // 계획의 목표가 모두 0분인 날도 달성률이 null 이라서, 계획이 없던 날처럼 거기서 멈춥니다.
      ratesFromYesterday.add(DaySummary.of(date, plans, sessions).achievementRate());
    }
    return MissStreak.countConsecutiveMissDays(ratesFromYesterday);
  }
}
