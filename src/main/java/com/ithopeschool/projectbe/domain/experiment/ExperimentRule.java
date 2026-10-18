package com.ithopeschool.projectbe.domain.experiment;

import com.ithopeschool.projectbe.domain.cardresult.CardResult;
import com.ithopeschool.projectbe.domain.cardresult.JudgedCardDay;
import com.ithopeschool.projectbe.domain.suggestion.SuggestionRate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// 자기 실험의 날짜 계산과 판정입니다. (최종 기획서 「4.6 분석」 자기 실험, 이슈 #52 · #53 · #54)
// FE 의 judgeExperiment · getExperimentEndDate · getExperimentDay (src/lib/experiments.ts) 와 같은 규칙 ·
// 같은 숫자입니다.
// 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
//
// ■ 효과 판정 (#54): 붙인 카드만, 기준선(시작 전 14일) vs 실험 기간(14일)의 평균 실행률을 비교합니다.
//   - 둘 중 하나라도 카드 하루치가 5개보다 적으면 UNCLEAR (기록이 모자라 알 수 없음)
//   - (실험 − 기준선) 이 그만둘 기준(%p)보다 크면 EFFECTIVE, 아니면 NO_EFFECT
//   - 휴식한 날(REST)은 빼고 셉니다.
//
//     기준선 0.50, 실험 0.70, 기준 10%p  → EFFECTIVE (+20%p)
//     기준선 0.50, 실험 0.58, 기준 10%p  → NO_EFFECT (+8%p)
//     실험 기간 카드 3개뿐               → UNCLEAR
//
// ■ 이행률 (#53): "지켰어?" 를 묻지 않고, 붙인 카드의 기록으로 셉니다.
//   실험 기간 중 판정이 끝난 붙인 카드 하루치마다, 실행했으면(미실행이 아니면) 지킨 것입니다. 휴식한 날은 뺍니다.
//   70% 이상 잘 시행 · 40% 이상 부분 시행 · 그 밑은 못 시행 (SuggestionRate 와 같은 기준). 셀 것이 없으면 null
public final class ExperimentRule {

  // 실험 기간 (일). 기준선도 같은 길이입니다.
  public static final int EXPERIMENT_DAYS = 14;
  // 기본 그만둘 기준 — 기준선보다 실행률이 이만큼(%p) 넘게 올라야 효과 있음
  public static final int DEFAULT_MIN_GAIN_PP = 10;
  // 판정에 필요한 카드 하루치 수 (기준선 · 실험 각각). 모자라면 UNCLEAR
  public static final int MIN_SAMPLES = 5;

  // 판정 결과. baselineRate · experimentRate: 평균 실행률(0 ~ 1). 카드가 모자라 셀 수 없으면 null
  public record Judgement(ExperimentStatus status, Double baselineRate, Double experimentRate) {}

  private ExperimentRule() {}

  // 실험이 끝나는 날 (마지막 날)
  //   10/1 시작 → 10/14
  public static LocalDate endDate(LocalDate startDate) {
    return startDate.plusDays(EXPERIMENT_DAYS - 1);
  }

  // 며칠째인지 (시작한 날 = 1, 끝난 뒤에는 15)
  //   10/1 시작, 오늘 10/5 → 5     오늘 10/20 → 15
  public static int dayOf(LocalDate startDate, LocalDate today) {
    long day = today.toEpochDay() - startDate.toEpochDay() + 1;
    return (int) Math.max(1, Math.min(EXPERIMENT_DAYS + 1, day));
  }

  // 판정할 날이 됐는지 (실험 마지막 날 다음 날부터)
  public static boolean isJudgeDay(LocalDate startDate, LocalDate today) {
    return today.isAfter(endDate(startDate));
  }

  // 효과 판정 (#54). allDays 에는 붙인 카드가 아닌 것이 섞여 있어도 됩니다. (여기서 거릅니다)
  public static Judgement judge(
      Set<Long> cardIds, LocalDate startDate, int minGainPp, List<JudgedCardDay> allDays) {
    LocalDate baselineFrom = startDate.minusDays(EXPERIMENT_DAYS);
    LocalDate baselineTo = startDate.minusDays(1);
    Double baselineRate = averageRate(between(cardIds, allDays, baselineFrom, baselineTo));
    Double experimentRate = averageRate(between(cardIds, allDays, startDate, endDate(startDate)));
    if (baselineRate == null || experimentRate == null) {
      return new Judgement(ExperimentStatus.UNCLEAR, baselineRate, experimentRate);
    }
    double gainPp = (experimentRate - baselineRate) * 100;
    ExperimentStatus status =
        gainPp > minGainPp ? ExperimentStatus.EFFECTIVE : ExperimentStatus.NO_EFFECT;
    return new Judgement(status, baselineRate, experimentRate);
  }

  // 이행률 (#53). 실험 기간 중 판정이 끝난 붙인 카드 하루치로 셉니다. 셀 것이 없으면 null
  public static SuggestionRate fulfillment(
      Set<Long> cardIds, LocalDate startDate, List<JudgedCardDay> allDays) {
    List<Boolean> kept = new ArrayList<>();
    for (JudgedCardDay day : between(cardIds, allDays, startDate, endDate(startDate))) {
      kept.add(!day.result().isMiss());
    }
    return SuggestionRate.of(kept, kept.size());
  }

  // 붙인 카드 · 기간 안 · 휴식이 아닌 카드 하루치
  private static List<JudgedCardDay> between(
      Set<Long> cardIds, List<JudgedCardDay> allDays, LocalDate from, LocalDate to) {
    List<JudgedCardDay> picked = new ArrayList<>();
    for (JudgedCardDay day : allDays) {
      boolean inPeriod = !day.date().isBefore(from) && !day.date().isAfter(to);
      if (cardIds.contains(day.cardId()) && inPeriod && day.result() != CardResult.REST) {
        picked.add(day);
      }
    }
    return picked;
  }

  // 평균 실행률. 카드 하루치가 5개보다 적으면 null
  private static Double averageRate(List<JudgedCardDay> days) {
    if (days.size() < MIN_SAMPLES) {
      return null;
    }
    double sum = 0;
    for (JudgedCardDay day : days) {
      sum += day.runRate();
    }
    return sum / days.size();
  }
}
