package com.ithopeschool.projectbe.domain.restday;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 휴식 처리 기한 · 한도를 RestDayRule 주석의 예시 그대로 확인합니다. (이슈 #225, FE#407 과 같은 숫자)
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
}
