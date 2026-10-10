package com.ithopeschool.projectbe.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

// "계획 대비 달성률" 식(이슈 #15, docs/API.md 5장)을 확인합니다.
// feExample 로 시작하는 테스트는 FE 함수(getDaySummary) 주석의 예시를 그대로 옮긴 것입니다.
// (예7~9 는 날짜 고르기·일시정지 횟수 예시라서, 숫자 두 개만 받는 이 함수와는 상관이 없습니다)
class AchievementRateTest {

  @Test
  void docExample1DropsTheDecimal() {
    // 문서 예시 1 = FE 예4) 순공 190분, 목표 240분 → 79.16… → 79
    assertThat(AchievementRate.percentOf(190, 240)).isEqualTo(79);
  }

  @Test
  void docExample2DoesNotRoundUpToHundred() {
    // 문서 예시 2 = FE 예6) 순공 239분, 목표 240분 → 99.58… → 99 (100 은 다 채웠을 때만)
    assertThat(AchievementRate.percentOf(239, 240)).isEqualTo(99);
  }

  @Test
  void docExample3IsCappedAtHundred() {
    // 문서 예시 3 = FE 예5) 순공 90분, 목표 60분 → 150 이지만 100 까지만
    assertThat(AchievementRate.percentOf(90, 60)).isEqualTo(100);
  }

  @Test
  void feExample2PlanButNoStudyIsZero() {
    // FE 예2) 계획 60분, 공부 안 함 → 0 (계산은 되고, 결과가 0%)
    assertThat(AchievementRate.percentOf(0, 60)).isEqualTo(0);
  }

  @Test
  void feExample10PausedTimeAlreadyRemoved() {
    // FE 예10) 계획 60분, 일시정지한 시간을 뺀 순공 30분 → 50
    assertThat(AchievementRate.percentOf(30, 60)).isEqualTo(50);
  }

  @Test
  void exactlyTheTargetIsHundred() {
    // 목표를 딱 채우면 100
    assertThat(AchievementRate.percentOf(240, 240)).isEqualTo(100);
  }

  @Test
  void hasNoRoundingErrorLikeFloatingPoint() {
    // FE 코드 주석의 경우: 29 ÷ 100 × 100 이 28.999… 로 나오면 안 됩니다. 29 여야 합니다.
    assertThat(AchievementRate.percentOf(29, 100)).isEqualTo(29);
  }

  @Test
  void noPlanMeansCannotCalculate() {
    // FE 예1) 계획도 기록도 없는 날. FE 는 0 이지만 BE 는 "계산 불가"(null) 로 구분합니다.
    assertThat(AchievementRate.percentOf(0, 0)).isNull();
    // FE 예3) 목표가 0분인데 30분 공부한 날. 0 으로 나눌 수 없어서 역시 "계산 불가" 입니다.
    assertThat(AchievementRate.percentOf(30, 0)).isNull();
  }

  @Test
  void negativeTargetAlsoMeansCannotCalculate() {
    // 목표 합계가 음수인 이상한 값도 나눌 수 없는 것으로 봅니다. (FE 도 0 이하를 같이 묶습니다)
    assertThat(AchievementRate.percentOf(30, -10)).isNull();
  }

  @Test
  void rejectsNegativeStudyMinutes() {
    // 순공시간이 음수면 계산하지 않고 바로 알립니다.
    assertThatThrownBy(() -> AchievementRate.percentOf(-1, 60))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
