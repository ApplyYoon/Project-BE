package com.ithopeschool.projectbe.domain.care;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.ithopeschool.projectbe.domain.card.CardPriority;
import com.ithopeschool.projectbe.domain.cardresult.CardResult;
import com.ithopeschool.projectbe.domain.cardresult.JudgedCardDay;
import com.ithopeschool.projectbe.domain.care.Willpower.CarePreference;
import com.ithopeschool.projectbe.domain.care.Willpower.TestScores;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 의지력 지수를 FE src/lib/willpower.ts 주석의 예시 그대로 확인합니다. (이슈 #231)
class WillpowerTest {

  private static final LocalDate OCT_17 = LocalDate.of(2026, 10, 17);
  // 소수 계산의 아주 작은 오차(0.000…1)는 같은 값으로 봅니다.
  private static final double TINY = 1e-9;

  private static JudgedCardDay day(
      LocalDate date, CardResult result, double runRate, Boolean startedOnTime) {
    return new JudgedCardDay(
        1L, date, false, CardPriority.FLEXIBLE, result, runRate, null, startedOnTime, null);
  }

  // 날짜 n 개에 하루 한 장씩 실행(정시, 실행률 rate) 카드
  private static List<JudgedCardDay> doneDays(int count, double rate) {
    List<JudgedCardDay> days = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      days.add(day(OCT_17.minusDays(i), CardResult.DONE, rate, true));
    }
    return days;
  }

  // 세 척도가 모두 같은 점수인 검사
  private static TestScores scores(double each) {
    return new TestScores(each, each, each);
  }

  @Test
  @DisplayName("데이터: 실행(정시, 1) · 실행(정시, 1) · 지연(늦음, 1) · 실패(0) → 72.5")
  void dataWillpower() {
    List<JudgedCardDay> days =
        List.of(
            day(OCT_17, CardResult.DONE, 1, true),
            day(OCT_17, CardResult.DONE, 1, true),
            day(OCT_17, CardResult.LATE, 1, false),
            day(OCT_17, CardResult.FAILED, 0, null));

    assertThat(Willpower.getDataWillpower(days)).isCloseTo(72.5, within(TINY));
  }

  @Test
  @DisplayName("데이터: 카드가 없으면 null, 휴식 날은 빼고, 시작한 카드가 없으면 정시 비율 0")
  void dataWillpowerEdges() {
    assertThat(Willpower.getDataWillpower(List.of())).isNull();
    assertThat(Willpower.getDataWillpower(List.of(day(OCT_17, CardResult.REST, 1, true)))).isNull();
    // 미처리 1장: 실행률 0 · 정시 0(시작 안 함) · 미실행 100% → 0
    assertThat(Willpower.getDataWillpower(List.of(day(OCT_17, CardResult.UNHANDLED, 0, null))))
        .isZero();
    // 증빙 없는 자기 보고 1장: 50 × 0.5 + 30 × 0(사후 입력은 시작 시각을 안 봄) + 20 × 1 = 45
    assertThat(Willpower.getDataWillpower(List.of(day(OCT_17, CardResult.SELF_REPORT, 0.5, null))))
        .isCloseTo(45, within(TINY));
  }

  @Test
  @DisplayName("검사: 세 척도 평균 1 → 100, 4 → 50, 7 → 0")
  void testWillpower() {
    assertThat(Willpower.getTestWillpower(scores(1))).isCloseTo(100, within(TINY));
    assertThat(Willpower.getTestWillpower(scores(4))).isCloseTo(50, within(TINY));
    assertThat(Willpower.getTestWillpower(scores(7))).isCloseTo(0, within(TINY));
    // 세 척도가 달라도 평균으로 봅니다: (2 + 4 + 6) ÷ 3 = 4 → 50
    assertThat(Willpower.getTestWillpower(new TestScores(2, 4, 6))).isCloseTo(50, within(TINY));
  }

  @Test
  @DisplayName("유효 카드일: 카드가 있던 날 수 (같은 날 여러 장은 하루, 휴식일은 뺌)")
  void validCardDays() {
    List<JudgedCardDay> days =
        List.of(
            day(OCT_17, CardResult.DONE, 1, true),
            day(OCT_17, CardResult.FAILED, 0, null),
            day(OCT_17.minusDays(1), CardResult.DONE, 1, true),
            day(OCT_17.minusDays(2), CardResult.REST, 0, null));

    assertThat(Willpower.countValidCardDays(days)).isEqualTo(2);
  }

  @Test
  @DisplayName("섞기: 검사 50 · 데이터 80 · 유효 7일 → 65, 유효 20일(이상) → 80, 둘 다 없음 → 50")
  void mix() {
    // 실행(정시) 카드 실행률 0.6: 50 × 0.6 + 30 × 1 + 20 × 1 = 80
    assertThat(Willpower.getWillpower(doneDays(7, 0.6), scores(4))).isCloseTo(65, within(TINY));
    assertThat(Willpower.getWillpower(doneDays(20, 0.6), scores(4))).isCloseTo(80, within(TINY));
    assertThat(Willpower.getWillpower(List.of(), null)).isEqualTo(50);
  }

  @Test
  @DisplayName("섞기: 검사를 안 했으면 데이터만, 데이터가 없으면 검사만")
  void mixOneSide() {
    assertThat(Willpower.getWillpower(doneDays(3, 0.6), null)).isCloseTo(80, within(TINY));
    assertThat(Willpower.getWillpower(List.of(), scores(1))).isCloseTo(100, within(TINY));
    // 첫날(유효 카드일 0일 = 휴식뿐)은 데이터가 없어서 검사 100%
    assertThat(Willpower.getWillpower(List.of(day(OCT_17, CardResult.REST, 0, null)), scores(4)))
        .isCloseTo(50, within(TINY));
  }

  @Test
  @DisplayName("기준 단계: (75, 보통, 20일째) → 1, (55, 높음) → 3, (30, 낮음) → 2, (30, 보통, 3일째) → 2")
  void baseStage() {
    assertThat(Willpower.getBaseStage(75, CarePreference.MID, 20)).isEqualTo(1);
    assertThat(Willpower.getBaseStage(55, CarePreference.HIGH, 20)).isEqualTo(3);
    assertThat(Willpower.getBaseStage(30, CarePreference.LOW, 20)).isEqualTo(2);
    assertThat(Willpower.getBaseStage(30, CarePreference.MID, 3)).isEqualTo(2);
  }

  @Test
  @DisplayName("기준 단계 경계: 70 → 1, 69.9 → 2, 40 → 2, 39.9 → 3, 1 ~ 3 밖으로 나가지 않고, 선호를 모르면 그대로")
  void baseStageEdges() {
    assertThat(Willpower.getBaseStage(70, null, 20)).isEqualTo(1);
    assertThat(Willpower.getBaseStage(69.9, null, 20)).isEqualTo(2);
    assertThat(Willpower.getBaseStage(40, null, 20)).isEqualTo(2);
    assertThat(Willpower.getBaseStage(39.9, null, 20)).isEqualTo(3);
    assertThat(Willpower.getBaseStage(90, CarePreference.LOW, 20)).isEqualTo(1);
    assertThat(Willpower.getBaseStage(10, CarePreference.HIGH, 20)).isEqualTo(3);
    // 7일째(dayIndex 7)부터는 상한이 없습니다.
    assertThat(Willpower.getBaseStage(10, null, 6)).isEqualTo(2);
    assertThat(Willpower.getBaseStage(10, null, 7)).isEqualTo(3);
  }
}
