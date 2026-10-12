package com.ithopeschool.projectbe.domain.suggestion;

import java.util.List;

// 개선안 실험에 효과가 있었는지 판정합니다. (이슈 #54, 기획서 v2 4장 「효과 판정」)
// 실험 기간과 그 직전 2주(기준선)의 평균 순공시간·평균 달성률을 비교합니다.
//
//   효과 있음(EFFECTIVE)  →  나 사용설명서에 올림
//   효과 없음(NO_EFFECT)  →  전략을 바꿈
//   애매함(UNCLEAR)       →  1주 연장
//
// 순서대로 따지고, 먼저 맞는 줄에서 끝냅니다.
//
//   1) 두 기간 중 하나라도 기록이 있는 날이 5일 미만이면  →  애매함 (아직 단정할 수 없음)
//        기준선 4일 + 실험 14일   →  애매함   (기준선이 5일 미만)
//        기준선 14일 + 실험 3일   →  애매함   (실험이 5일 미만)
//
//   2) 평균 순공시간과 평균 달성률을 각각 "올랐다 / 비슷하다 / 내려갔다" 로 나눕니다.
//        순공시간: 기준선보다 10% 이상 늘면 올랐다, 10% 이상 줄면 내려갔다
//        달성률:   기준선보다 10%p 이상 오르면 올랐다, 10%p 이상 내리면 내려갔다
//
//   3) 두 결과를 합쳐서 판정합니다.
//        하나라도 올랐고 내려간 것은 없음   →  효과 있음
//          기준선 평균 순공 120분, 실험 평균 150분(+25%), 달성률 비슷   →  효과 있음
//        올랐다와 내려갔다가 섞임          →  애매함   (엇갈림. 1주 더 지켜봄)
//          순공은 +30% 인데 달성률은 -15%p                             →  애매함
//        모두 비슷하거나, 내려간 것만 있음  →  효과 없음  (전략을 바꿈)
//          기준선 평균 순공 120분, 실험 평균 125분(+4%), 달성률 비슷     →  효과 없음
//
// 경계값: 정확히 +10% 는 "올랐다", +9.9% 는 "비슷하다" 입니다. (이상 / 미만)
//
// 기획서 3장 원칙: 상관은 인과가 아니라서, 기록이 충분히 쌓이기 전에는 단정하지 않습니다.
//
// ⚠️ 기준 숫자 3개(10% · 10%p · 5일)는 FE 에서 임시로 정한 값을 그대로 옮긴 것입니다. 팀 확인이 필요합니다.
// FE 의 judgeExperiment (src/lib/experimentJudge.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 판정이 어긋나지 않습니다.
public final class ExperimentJudge {

  // 평균 순공시간이 기준선보다 이 비율(%) 이상 늘면 "올랐다", 이만큼 줄면 "내려갔다" 로 봅니다.
  public static final int STUDY_CHANGE_PERCENT = 10;

  // 평균 달성률이 기준선보다 이 퍼센트포인트(%p) 이상 오르면 "올랐다", 이만큼 내리면 "내려갔다" 로 봅니다.
  // (%p 는 퍼센트끼리의 차이입니다. 60% 에서 70% 가 되면 10%p 오른 것입니다)
  public static final int ACHIEVEMENT_CHANGE_POINTS = 10;

  // 두 기간 모두 기록이 있는 날이 이 일수 이상이어야 판정합니다. 하나라도 모자라면 애매함입니다.
  public static final int MIN_RECORDED_DAYS = 5;

  // 하루 기록에서 비교에 쓰는 두 숫자입니다.
  // studyMinutes: 그날 순공시간(분) / achievementRate: 그날 계획 달성률(0~100, AchievementRate.percentOf)
  // 계획 없이 공부만 해서 달성률을 계산할 수 없는 날은 FE 와 같이 0 으로 넣습니다.
  public record DayMetric(int studyMinutes, int achievementRate) {}

  // 기준선과 비교한 변화의 방향입니다.
  private enum Change {
    UP,
    FLAT,
    DOWN
  }

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private ExperimentJudge() {}

  // 개선안 실험의 효과를 판정합니다.
  //
  // baseline: 실험 직전 2주(기준선) 중 "기록이 있는 날들" 의 하루 지표
  // experiment: 실험 기간 중 "기록이 있는 날들" 의 하루 지표
  // 기록이 있는 날 = 공부 기록이 있거나 그날 계획이 있는 날. 아무 기록도 없는 날은 평균을 깎지 않게 빼고 넘깁니다.
  public static ExperimentEffect judge(List<DayMetric> baseline, List<DayMetric> experiment) {
    // 1) 기록이 모자라면 단정하지 않습니다.
    if (baseline.size() < MIN_RECORDED_DAYS || experiment.size() < MIN_RECORDED_DAYS) {
      return ExperimentEffect.UNCLEAR;
    }

    // 2) 평균을 구해서 비교합니다.
    Change study = compareStudy(averageStudyMinutes(baseline), averageStudyMinutes(experiment));
    Change achievement =
        compareAchievement(averageAchievementRate(baseline), averageAchievementRate(experiment));

    // 3) 두 결과를 합칩니다.
    boolean hasUp = study == Change.UP || achievement == Change.UP;
    boolean hasDown = study == Change.DOWN || achievement == Change.DOWN;

    if (hasUp && hasDown) {
      return ExperimentEffect.UNCLEAR;
    }
    if (hasUp) {
      return ExperimentEffect.EFFECTIVE;
    }
    return ExperimentEffect.NO_EFFECT;
  }

  // 평균 순공시간이 기준선보다 올랐는지·내려갔는지를 % 로 비교합니다.
  private static Change compareStudy(double baselineAverage, double experimentAverage) {
    // 기준선이 0분이면 % 를 계산할 수 없습니다. 0분에서 조금이라도 늘었으면 "올랐다" 로 봅니다.
    if (baselineAverage == 0) {
      return experimentAverage > 0 ? Change.UP : Change.FLAT;
    }

    // FE 와 같은 순서(먼저 나누고 100 을 곱함)로 계산합니다.
    // 순서를 바꾸면 소수 계산 오차 때문에 "정확히 10%" 같은 경계에서 FE 와 판정이 갈릴 수 있습니다.
    double changePercent = (experimentAverage - baselineAverage) / baselineAverage * 100;
    if (changePercent >= STUDY_CHANGE_PERCENT) {
      return Change.UP;
    }
    if (changePercent <= -STUDY_CHANGE_PERCENT) {
      return Change.DOWN;
    }
    return Change.FLAT;
  }

  // 평균 달성률이 기준선보다 올랐는지·내려갔는지를 퍼센트포인트(%p)로 비교합니다.
  private static Change compareAchievement(double baselineAverage, double experimentAverage) {
    double changePoints = experimentAverage - baselineAverage;
    if (changePoints >= ACHIEVEMENT_CHANGE_POINTS) {
      return Change.UP;
    }
    if (changePoints <= -ACHIEVEMENT_CHANGE_POINTS) {
      return Change.DOWN;
    }
    return Change.FLAT;
  }

  // 하루 순공시간의 평균입니다. (빈 목록은 이 함수를 부르기 전에 걸러냅니다)
  private static double averageStudyMinutes(List<DayMetric> days) {
    long sum = 0;
    for (DayMetric day : days) {
      sum += day.studyMinutes();
    }
    // 정수끼리 나누면 소수점이 버려져서, 한쪽을 소수(double)로 바꿔서 나눕니다.
    return (double) sum / days.size();
  }

  // 하루 달성률의 평균입니다.
  private static double averageAchievementRate(List<DayMetric> days) {
    long sum = 0;
    for (DayMetric day : days) {
      sum += day.achievementRate();
    }
    return (double) sum / days.size();
  }
}
