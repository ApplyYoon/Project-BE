package com.ithopeschool.projectbe.domain.suggestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// "이행률 판정" 규칙(이슈 #53)을 확인합니다.
// feExample 로 시작하는 테스트는 FE 함수(getSuggestionRate) 주석의 예시 9개를 그대로 옮긴 것입니다.
class SuggestionRateTest {

  // "지킨 날 keptDays 개 + 못 지킨 날 missedDays 개" 기록을 만듭니다.
  private static List<Boolean> checks(int keptDays, int missedDays) {
    List<Boolean> checks = new ArrayList<>();
    for (int i = 0; i < keptDays; i++) {
      checks.add(true);
    }
    for (int i = 0; i < missedDays; i++) {
      checks.add(false);
    }
    return checks;
  }

  @Test
  void feExampleElevenOfFourteenIsGood() {
    // 14일 중 11일 지킴 → 79%, 잘 시행 (11 ÷ 14 = 78.6%)
    assertThat(SuggestionRate.of(checks(11, 3), 14))
        .isEqualTo(new SuggestionRate(79, SuggestionGrade.GOOD));
  }

  @Test
  void feExampleTenOfFourteenIsGood() {
    // 14일 중 10일 지킴 → 71%, 잘 시행
    assertThat(SuggestionRate.of(checks(10, 4), 14))
        .isEqualTo(new SuggestionRate(71, SuggestionGrade.GOOD));
  }

  @Test
  void feExampleSevenOfFourteenIsPartial() {
    // 14일 중 7일 지킴 → 50%, 부분 시행
    assertThat(SuggestionRate.of(checks(7, 7), 14))
        .isEqualTo(new SuggestionRate(50, SuggestionGrade.PARTIAL));
  }

  @Test
  void feExampleExactlySeventyIsGood() {
    // 10일 중 7일 지킴 (경계) → 70%, 잘 시행. 70 은 "잘 시행" 에 포함됩니다.
    assertThat(SuggestionRate.of(checks(7, 3), 10))
        .isEqualTo(new SuggestionRate(70, SuggestionGrade.GOOD));
  }

  @Test
  void feExampleExactlyFortyIsPartial() {
    // 10일 중 4일 지킴 (경계) → 40%, 부분 시행. 40 은 "부분 시행" 에 포함됩니다.
    assertThat(SuggestionRate.of(checks(4, 6), 10))
        .isEqualTo(new SuggestionRate(40, SuggestionGrade.PARTIAL));
  }

  @Test
  void feExampleThreeOfTenIsPoor() {
    // 10일 중 3일 지킴 → 30%, 못 시행
    assertThat(SuggestionRate.of(checks(3, 7), 10))
        .isEqualTo(new SuggestionRate(30, SuggestionGrade.POOR));
  }

  @Test
  void feExampleNoneKeptIsZeroNotNull() {
    // 14일 중 하루도 못 지킴 → 0%, 못 시행. 기록은 있으니 null 이 아니라 0% 입니다.
    assertThat(SuggestionRate.of(checks(0, 14), 14))
        .isEqualTo(new SuggestionRate(0, SuggestionGrade.POOR));
  }

  @Test
  void feExampleNoRecordsIsUnknown() {
    // 14일인데 기록이 하나도 없음 → null ("0%" 가 아니라 "아직 모름")
    assertThat(SuggestionRate.of(List.of(), 14)).isNull();
  }

  @Test
  void feExampleZeroDaysCannotBeCalculated() {
    // days 가 0 이하 → null (나눌 수 없음)
    assertThat(SuggestionRate.of(checks(3, 0), 0)).isNull();
    assertThat(SuggestionRate.of(checks(3, 0), -1)).isNull();
  }

  @Test
  void daysWithoutAnAnswerCountAsNotKept() {
    // 14일 중 답한 날이 5일뿐이고 전부 지켰어도, 안 적은 9일은 못 지킨 날로 셉니다. 5 ÷ 14 = 35.7% → 36%
    assertThat(SuggestionRate.of(checks(5, 0), 14))
        .isEqualTo(new SuggestionRate(36, SuggestionGrade.POOR));
  }

  @Test
  void rateNeverGoesOverHundred() {
    // 기록이 전체 일수보다 많아도 100% 를 넘지 않습니다.
    assertThat(SuggestionRate.of(checks(12, 0), 10))
        .isEqualTo(new SuggestionRate(100, SuggestionGrade.GOOD));
  }

  @Test
  void exactHalfRoundsUp() {
    // 딱 절반(.5)은 올립니다. 8일 중 1일 = 12.5% → 13%, 8일 중 5일 = 62.5% → 63%
    assertThat(SuggestionRate.of(checks(1, 7), 8))
        .isEqualTo(new SuggestionRate(13, SuggestionGrade.POOR));
    assertThat(SuggestionRate.of(checks(5, 3), 8))
        .isEqualTo(new SuggestionRate(63, SuggestionGrade.PARTIAL));
  }

  @Test
  void gradeUsesTheRoundedRate() {
    // 판정은 반올림한 값(화면에 보이는 값)으로 정합니다.
    // 200일 중 139일 = 69.5% → 70% 로 보이므로 "잘 시행"
    assertThat(SuggestionRate.of(checks(139, 61), 200))
        .isEqualTo(new SuggestionRate(70, SuggestionGrade.GOOD));
    // 200일 중 79일 = 39.5% → 40% 로 보이므로 "부분 시행"
    assertThat(SuggestionRate.of(checks(79, 121), 200))
        .isEqualTo(new SuggestionRate(40, SuggestionGrade.PARTIAL));
    // 200일 중 78일 = 39% → "못 시행"
    assertThat(SuggestionRate.of(checks(78, 122), 200))
        .isEqualTo(new SuggestionRate(39, SuggestionGrade.POOR));
  }
}
