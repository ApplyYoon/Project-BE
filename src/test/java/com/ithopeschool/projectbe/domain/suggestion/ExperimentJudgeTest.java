package com.ithopeschool.projectbe.domain.suggestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.suggestion.ExperimentJudge.DayMetric;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// 개선안 실험의 효과 판정 규칙(이슈 #54)을 확인합니다.
// feExample 로 시작하는 5개는 FE 함수(judgeExperiment) 주석의 예시를 그대로 옮긴 것입니다.
class ExperimentJudgeTest {

  // 매일 같은 숫자를 기록한 기간을 만듭니다.
  // 예: days(14, 120, 70) → 14일 동안 매일 순공 120분, 달성률 70%
  private static List<DayMetric> days(int count, int studyMinutes, int achievementRate) {
    List<DayMetric> metrics = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      metrics.add(new DayMetric(studyMinutes, achievementRate));
    }
    return metrics;
  }

  @Test
  void feExample1ShortBaselineIsUnclear() {
    // FE 예) 기준선 4일 + 실험 14일 → 애매함 (기준선이 5일 미만)
    // 숫자만 보면 크게 올랐지만, 기록이 모자라서 단정하지 않습니다.
    assertThat(ExperimentJudge.judge(days(4, 60, 50), days(14, 180, 90)))
        .isEqualTo(ExperimentEffect.UNCLEAR);
  }

  @Test
  void feExample2ShortExperimentIsUnclear() {
    // FE 예) 기준선 14일 + 실험 3일 → 애매함 (실험이 5일 미만)
    assertThat(ExperimentJudge.judge(days(14, 60, 50), days(3, 180, 90)))
        .isEqualTo(ExperimentEffect.UNCLEAR);
  }

  @Test
  void feExample3StudyUpAchievementFlatIsEffective() {
    // FE 예) 기준선 평균 순공 120분, 실험 평균 150분(+25%), 달성률 비슷 → 효과 있음
    assertThat(ExperimentJudge.judge(days(14, 120, 70), days(14, 150, 72)))
        .isEqualTo(ExperimentEffect.EFFECTIVE);
  }

  @Test
  void feExample4UpAndDownMixedIsUnclear() {
    // FE 예) 순공은 +30% 인데 달성률은 -15%p → 애매함 (엇갈림. 1주 더 지켜봄)
    assertThat(ExperimentJudge.judge(days(14, 100, 80), days(14, 130, 65)))
        .isEqualTo(ExperimentEffect.UNCLEAR);
  }

  @Test
  void feExample5SmallChangeIsNoEffect() {
    // FE 예) 기준선 평균 순공 120분, 실험 평균 125분(+4%), 달성률 비슷 → 효과 없음
    assertThat(ExperimentJudge.judge(days(14, 120, 70), days(14, 125, 70)))
        .isEqualTo(ExperimentEffect.NO_EFFECT);
  }

  @Test
  void exactlyFiveDaysIsEnoughToJudge() {
    // 경계값: 두 기간 모두 딱 5일이면 판정합니다. 한쪽이 4일이면 애매함입니다.
    assertThat(ExperimentJudge.judge(days(5, 100, 70), days(5, 150, 70)))
        .isEqualTo(ExperimentEffect.EFFECTIVE);
    assertThat(ExperimentJudge.judge(days(5, 100, 70), days(4, 150, 70)))
        .isEqualTo(ExperimentEffect.UNCLEAR);
    assertThat(ExperimentJudge.judge(days(4, 100, 70), days(5, 150, 70)))
        .isEqualTo(ExperimentEffect.UNCLEAR);
  }

  @Test
  void noRecordsIsUnclear() {
    // 기록이 하나도 없으면 "효과 없음" 이 아니라 "아직 모름" 입니다.
    assertThat(ExperimentJudge.judge(List.of(), List.of())).isEqualTo(ExperimentEffect.UNCLEAR);
    assertThat(ExperimentJudge.judge(days(14, 100, 70), List.of()))
        .isEqualTo(ExperimentEffect.UNCLEAR);
  }

  @Test
  void studyBoundaryTenPercentIsUp() {
    // 경계값: 정확히 +10% 는 "올랐다", +9.9% 는 "비슷하다" 입니다.
    // 1000분 → 1100분(+10%) 은 효과 있음, 1000분 → 1099분(+9.9%) 은 효과 없음
    assertThat(ExperimentJudge.judge(days(7, 1000, 70), days(7, 1100, 70)))
        .isEqualTo(ExperimentEffect.EFFECTIVE);
    assertThat(ExperimentJudge.judge(days(7, 1000, 70), days(7, 1099, 70)))
        .isEqualTo(ExperimentEffect.NO_EFFECT);
  }

  @Test
  void studyBoundaryHoldsForManyBaselines() {
    // 소수 계산 오차가 있어도 "정확히 +10%" 는 언제나 "올랐다" 로 판정되는지, 여러 기준선으로 확인합니다.
    // (10분 단위 기준선이면 +10% 가 정수로 떨어집니다. 70분 → 77분, 130분 → 143분 …)
    for (int baselineMinutes = 10; baselineMinutes <= 600; baselineMinutes += 10) {
      int tenPercentUp = baselineMinutes + baselineMinutes / 10;
      int tenPercentDown = baselineMinutes - baselineMinutes / 10;

      assertThat(ExperimentJudge.judge(days(7, baselineMinutes, 70), days(7, tenPercentUp, 70)))
          .as("기준선 %d분 → %d분", baselineMinutes, tenPercentUp)
          .isEqualTo(ExperimentEffect.EFFECTIVE);
      // 정확히 -10% 는 "내려갔다" 입니다. 달성률이 올랐다면 엇갈려서 애매함이 됩니다.
      assertThat(ExperimentJudge.judge(days(7, baselineMinutes, 70), days(7, tenPercentDown, 90)))
          .as("기준선 %d분 → %d분", baselineMinutes, tenPercentDown)
          .isEqualTo(ExperimentEffect.UNCLEAR);
    }
  }

  @Test
  void achievementBoundaryTenPointsIsUp() {
    // 경계값: 달성률은 %p 로 봅니다. 60% → 70%(+10%p) 는 효과 있음, 60% → 69%(+9%p) 는 효과 없음
    assertThat(ExperimentJudge.judge(days(7, 100, 60), days(7, 100, 70)))
        .isEqualTo(ExperimentEffect.EFFECTIVE);
    assertThat(ExperimentJudge.judge(days(7, 100, 60), days(7, 100, 69)))
        .isEqualTo(ExperimentEffect.NO_EFFECT);
  }

  @Test
  void achievementIsComparedByPointsNotByRatio() {
    // 달성률은 비율(%)이 아니라 차이(%p)로 봅니다.
    // 20% → 25% 는 비율로는 +25% 지만 차이는 5%p 라서 "비슷하다" 입니다.
    assertThat(ExperimentJudge.judge(days(7, 100, 20), days(7, 100, 25)))
        .isEqualTo(ExperimentEffect.NO_EFFECT);
  }

  @Test
  void bothUpIsEffective() {
    // 둘 다 올랐으면 효과 있음
    assertThat(ExperimentJudge.judge(days(14, 100, 60), days(14, 130, 80)))
        .isEqualTo(ExperimentEffect.EFFECTIVE);
  }

  @Test
  void onlyDownIsNoEffect() {
    // 내려간 것만 있으면 효과 없음입니다. (애매함이 아닙니다)
    // 순공만 내려감
    assertThat(ExperimentJudge.judge(days(14, 100, 70), days(14, 80, 70)))
        .isEqualTo(ExperimentEffect.NO_EFFECT);
    // 달성률만 내려감
    assertThat(ExperimentJudge.judge(days(14, 100, 70), days(14, 100, 50)))
        .isEqualTo(ExperimentEffect.NO_EFFECT);
    // 둘 다 내려감
    assertThat(ExperimentJudge.judge(days(14, 100, 70), days(14, 80, 50)))
        .isEqualTo(ExperimentEffect.NO_EFFECT);
  }

  @Test
  void achievementUpStudyDownIsUnclear() {
    // 엇갈림은 방향이 반대여도 애매함입니다. (달성률은 올랐는데 순공은 내려감)
    assertThat(ExperimentJudge.judge(days(14, 100, 60), days(14, 80, 80)))
        .isEqualTo(ExperimentEffect.UNCLEAR);
  }

  @Test
  void zeroBaselineStudyCountsAnyIncreaseAsUp() {
    // 기준선 순공이 0분이면 % 를 계산할 수 없습니다. 조금이라도 늘었으면 "올랐다" 로 봅니다.
    assertThat(ExperimentJudge.judge(days(7, 0, 0), days(7, 5, 0)))
        .isEqualTo(ExperimentEffect.EFFECTIVE);
    // 실험 기간도 0분이면 "비슷하다" → 효과 없음
    assertThat(ExperimentJudge.judge(days(7, 0, 0), days(7, 0, 0)))
        .isEqualTo(ExperimentEffect.NO_EFFECT);
  }

  @Test
  void comparesAveragesNotTotals() {
    // 합계가 아니라 하루 평균으로 비교합니다.
    // 기준선 14일(합계 1680분)과 실험 7일(합계 1050분)은 합계로는 줄었지만, 하루 평균은 120분 → 150분으로 올랐습니다.
    assertThat(ExperimentJudge.judge(days(14, 120, 70), days(7, 150, 70)))
        .isEqualTo(ExperimentEffect.EFFECTIVE);
  }

  @Test
  void averageCanBeFractional() {
    // 평균이 소수로 나와도 그대로 비교합니다. (정수로 버리지 않습니다)
    // 기준선 평균 100분. 실험은 5일 중 4일 110분 + 1일 109분 → 평균 109.8분(+9.8%) → 비슷하다
    List<DayMetric> experiment = new ArrayList<>(days(4, 110, 70));
    experiment.add(new DayMetric(109, 70));
    assertThat(ExperimentJudge.judge(days(5, 100, 70), experiment))
        .isEqualTo(ExperimentEffect.NO_EFFECT);

    // 5일 중 4일 110분 + 1일 111분 → 평균 110.2분(+10.2%) → 올랐다
    List<DayMetric> higher = new ArrayList<>(days(4, 110, 70));
    higher.add(new DayMetric(111, 70));
    assertThat(ExperimentJudge.judge(days(5, 100, 70), higher))
        .isEqualTo(ExperimentEffect.EFFECTIVE);
  }

  @Test
  void orderOfDaysDoesNotMatter() {
    // 날짜 순서와 상관없이 평균만 봅니다.
    List<DayMetric> ascending =
        List.of(
            new DayMetric(100, 60),
            new DayMetric(120, 70),
            new DayMetric(140, 80),
            new DayMetric(160, 90),
            new DayMetric(180, 100));
    List<DayMetric> descending = new ArrayList<>(ascending).reversed();

    assertThat(ExperimentJudge.judge(days(5, 100, 70), ascending))
        .isEqualTo(ExperimentJudge.judge(days(5, 100, 70), descending))
        .isEqualTo(ExperimentEffect.EFFECTIVE);
  }
}
