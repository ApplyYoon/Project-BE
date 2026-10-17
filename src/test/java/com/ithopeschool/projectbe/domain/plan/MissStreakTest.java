package com.ithopeschool.projectbe.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

// "연속 미달 일수" 규칙(이슈 #31)을 확인합니다. 달성률은 어제, 그저께, 3일 전 … 순서로 넣습니다.
// feExample 로 시작하는 2개는 FE 함수(countConsecutiveMissDays) 주석의 예시를 그대로 옮긴 것입니다.
class MissStreakTest {

  @Test
  void feExample1CountsUntilAFullDay() {
    // FE 예) 어제 50%, 그저께 80%, 3일 전 100% → 2
    assertThat(MissStreak.countConsecutiveMissDays(Arrays.asList(50, 80, 100))).isEqualTo(2);
  }

  @Test
  void feExample2NoPlanYesterdayIsZero() {
    // FE 예) 어제 계획 없음 → 0 (그 전에 못 채운 날이 있어도 세지 않습니다)
    assertThat(MissStreak.countConsecutiveMissDays(Arrays.asList(null, 50, 50))).isZero();
  }

  @Test
  void noDaysIsZero() {
    // 볼 날이 하나도 없으면 0
    assertThat(MissStreak.countConsecutiveMissDays(List.of())).isZero();
  }

  @Test
  void fullDayYesterdayIsZero() {
    // 어제 다 채웠으면 0
    assertThat(MissStreak.countConsecutiveMissDays(Arrays.asList(100, 50, 50))).isZero();
  }

  @Test
  void ninetyNineIsAMissButHundredIsNot() {
    // 경계값: 99% 는 못 채운 날, 100% 는 채운 날
    assertThat(MissStreak.countConsecutiveMissDays(Arrays.asList(99, 99, 100))).isEqualTo(2);
  }

  @Test
  void zeroPercentIsAMiss() {
    // 계획은 있는데 공부를 안 한 날(0%)은 못 채운 날입니다. 계획이 없던 날(null)과 다릅니다.
    assertThat(MissStreak.countConsecutiveMissDays(Arrays.asList(0, 0, 0))).isEqualTo(3);
  }

  @Test
  void dayWithoutPlanInTheMiddleStopsTheCount() {
    // 가운데에 계획이 없던 날(null)이 있으면 거기서 멈춥니다.
    assertThat(MissStreak.countConsecutiveMissDays(Arrays.asList(50, null, 30, 30))).isEqualTo(1);
  }

  @Test
  void threeMissDaysInARow() {
    // "3일 연속 미달" 이 되는 경우 (이슈 #31: 이때 채찍이 아니라 회복 메시지를 보냅니다)
    assertThat(MissStreak.countConsecutiveMissDays(Arrays.asList(40, 70, 10, 100))).isEqualTo(3);
  }

  @Test
  void usesTheResultOfAchievementRateAsIs() {
    // AchievementRate.percentOf 의 결과를 그대로 넣어 씁니다.
    // 어제: 30분 / 60분 = 50%, 그저께: 0분 / 60분 = 0%, 3일 전: 계획 없음(null)
    List<Integer> rates =
        Arrays.asList(
            AchievementRate.percentOf(30, 60),
            AchievementRate.percentOf(0, 60),
            AchievementRate.percentOf(45, 0));
    assertThat(MissStreak.countConsecutiveMissDays(rates)).isEqualTo(2);
  }

  @Test
  void neverCountsMoreThanThirtyDays() {
    // 40일 내내 못 채웠어도 30일까지만 셉니다.
    List<Integer> fortyMissDays = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      fortyMissDays.add(50);
    }
    assertThat(MissStreak.countConsecutiveMissDays(fortyMissDays))
        .isEqualTo(MissStreak.MAX_LOOKBACK_DAYS);
  }
}
