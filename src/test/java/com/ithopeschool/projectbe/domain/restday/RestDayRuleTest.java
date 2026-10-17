package com.ithopeschool.projectbe.domain.restday;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 휴식 처리 기한 · 한도 · 회복 점검을 RestDayRule 주석의 예시 그대로 확인합니다. (이슈 #225 · #232, FE#407 과 같은 숫자)
class RestDayRuleTest {

  private static final LocalDate OCT_12 = LocalDate.of(2026, 10, 12);

  @Test
  @DisplayName("지난 날과 오늘만 휴식으로 처리할 수 있다 (내일은 안 됨)")
  void allowedDates() {
    assertThat(RestDayRule.isAllowedDate(OCT_12, OCT_12)).isTrue();
    assertThat(RestDayRule.isAllowedDate(OCT_12.minusDays(20), OCT_12)).isTrue();
    assertThat(RestDayRule.isAllowedDate(OCT_12.plusDays(1), OCT_12)).isFalse();
  }

  @Test
  @DisplayName("한 달에 4번까지: 3번 썼으면 1번 남음, 4번이면 0")
  void monthlyLimit() {
    assertThat(RestDayRule.MONTHLY_LIMIT).isEqualTo(4);
    assertThat(RestDayRule.daysLeft(0)).isEqualTo(4);
    assertThat(RestDayRule.daysLeft(3)).isEqualTo(1);
    assertThat(RestDayRule.daysLeft(4)).isZero();
    assertThat(RestDayRule.daysLeft(7)).isZero();
  }

  private static LocalDate october(int day) {
    return LocalDate.of(2026, 10, day);
  }

  @Test
  @DisplayName("연속으로 쉰 날: 10/15·16·17 → 3, 10/15·17 → 1, 그날 안 쉼 → 0")
  void streak() {
    assertThat(RestDayRule.streak(List.of(october(15), october(16), october(17)), october(17)))
        .isEqualTo(3);
    assertThat(RestDayRule.streak(List.of(october(15), october(17)), october(17))).isEqualTo(1);
    assertThat(RestDayRule.streak(List.of(october(15), october(16)), october(17))).isZero();
    // 순서가 섞여 있어도 같습니다.
    assertThat(RestDayRule.streak(List.of(october(17), october(15), october(16)), october(17)))
        .isEqualTo(3);
  }

  @Test
  @DisplayName("3일 연속 쉬면 회복 점검으로 (2일은 아님)")
  void recoveryCheckByStreak() {
    assertThat(
            RestDayRule.needsRecoveryCheck(
                List.of(october(15), october(16), october(17)), october(17)))
        .isTrue();
    assertThat(RestDayRule.needsRecoveryCheck(List.of(october(16), october(17)), october(17)))
        .isFalse();
    assertThat(RestDayRule.needsRecoveryCheck(List.of(october(15), october(17)), october(17)))
        .isFalse();
  }

  @Test
  @DisplayName("그 달 한도(4번)를 다 쓰면 회복 점검으로. 지난달에 쉰 날은 세지 않는다")
  void recoveryCheckByMonthlyLimit() {
    List<LocalDate> fourInOctober = List.of(october(2), october(9), october(12), october(17));
    List<LocalDate> threeInOctoberOneInSeptember =
        List.of(LocalDate.of(2026, 9, 30), october(9), october(12), october(17));

    assertThat(RestDayRule.needsRecoveryCheck(fourInOctober, october(17))).isTrue();
    assertThat(RestDayRule.needsRecoveryCheck(threeInOctoberOneInSeptember, october(17))).isFalse();
  }
}
