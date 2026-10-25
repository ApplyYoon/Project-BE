package com.ithopeschool.projectbe.domain.manual;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.insight.Insight;
import com.ithopeschool.projectbe.domain.insight.InsightResponse;
import com.ithopeschool.projectbe.domain.suggestion.ExperimentEffect;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

// 효과가 확인된 실험을 "나 사용설명서" 항목으로 바꾸는 규칙(이슈 #46)을 확인합니다.
// feExample 로 시작하는 테스트는 FE 함수(buildManualItem) 주석의 예시를 그대로 옮긴 것입니다.
class ManualItemTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

  private static ManualItem item(int days, Double baseline, Double experiment) {
    return ManualItem.ofExperiment("공부를 시작하면", "폰은 다른 방에 둔다", days, baseline, experiment, TODAY);
  }

  // ─── FE 예시 ───

  @Test
  void feExampleItemFromAnEffectiveExperiment() {
    // FE 예) 제목: '만약 공부를 시작하면 → 폰은 다른 방에 둔다' / 근거: '14일 실험: 하루 순공 +35분'
    assertThat(item(14, 120.0, 155.0))
        .isEqualTo(
            new ManualItem(
                ManualCategory.EFFECTIVE_METHOD,
                "만약 공부를 시작하면 → 폰은 다른 방에 둔다",
                TODAY,
                "14일 실험: 하루 순공 +35분"));
  }

  @Test
  void feExampleEvidenceWithoutNumbers() {
    // FE 예) 기록이 모자라 숫자를 못 비교하면 '14일 실험으로 효과 확인'
    assertThat(item(14, null, 155.0).evidence()).isEqualTo("14일 실험으로 효과 확인");
    assertThat(item(14, 120.0, null).evidence()).isEqualTo("14일 실험으로 효과 확인");
    assertThat(item(14, null, null).evidence()).isEqualTo("14일 실험으로 효과 확인");
  }

  // ─── 근거 문장 ───

  @Test
  void differenceIsRoundedOnce() {
    // 평균은 소수입니다. 차이를 구한 뒤 한 번만 반올림합니다. (155.4 − 120.0 = 35.4 → +35, 35.5 → +36)
    assertThat(item(14, 120.0, 155.4).evidence()).isEqualTo("14일 실험: 하루 순공 +35분");
    assertThat(item(14, 120.0, 155.5).evidence()).isEqualTo("14일 실험: 하루 순공 +36분");
  }

  @Test
  void decreaseAndNoChangeAreWrittenAsTheyAre() {
    // 달성률이 올라서 "효과 있음" 이어도 순공은 줄었을 수 있습니다. 숫자는 있는 그대로 적습니다.
    assertThat(item(14, 120.0, 108.0).evidence()).isEqualTo("14일 실험: 하루 순공 -12분");
    // 차이가 없으면(반올림해서 0) 부호 없이 0분입니다.
    assertThat(item(14, 120.0, 120.4).evidence()).isEqualTo("14일 실험: 하루 순공 0분");
    assertThat(item(14, 120.0, 119.6).evidence()).isEqualTo("14일 실험: 하루 순공 0분");
  }

  @Test
  void evidenceShowsTheExperimentLength() {
    // 1주 연장한 실험(21일)과 7일 실험은 기간이 그대로 적힙니다.
    assertThat(item(21, 60.0, 90.0).evidence()).isEqualTo("21일 실험: 하루 순공 +30분");
    assertThat(item(7, null, null).evidence()).isEqualTo("7일 실험으로 효과 확인");
  }

  @Test
  void itemIsAlwaysAnEffectiveMethodConfirmedToday() {
    ManualItem item = item(14, 60.0, 90.0);

    assertThat(item.category()).isEqualTo(ManualCategory.EFFECTIVE_METHOD);
    assertThat(item.confirmedDate()).isEqualTo(TODAY);
  }

  // ─── 올려도 되는 실험 ───

  @Test
  void onlyEffectiveExperimentsCanBeRegistered() {
    assertThat(ManualItem.canRegister(ExperimentEffect.EFFECTIVE)).isTrue();
    // 애매하거나 효과가 없던 실험은 사용설명서에 올리지 않습니다.
    assertThat(ManualItem.canRegister(ExperimentEffect.UNCLEAR)).isFalse();
    assertThat(ManualItem.canRegister(ExperimentEffect.NO_EFFECT)).isFalse();
  }

  // ─── v3 자기 실험 (붙인 카드의 실행률) ───

  private static ManualItem cardItem(Double baselineRate, Double experimentRate) {
    return ManualItem.ofCardExperiment("시작할 때 폰을 가방에 넣기", 14, baselineRate, experimentRate, TODAY);
  }

  @Test
  void cardExperimentItemUsesTheRuleAsTitleAndShowsBothRates() {
    // docs/API.md "자기 실험" 의 예시 숫자: 기준선 0.5, 실험 0.7
    assertThat(cardItem(0.5, 0.7))
        .isEqualTo(
            new ManualItem(
                ManualCategory.EFFECTIVE_METHOD,
                "시작할 때 폰을 가방에 넣기",
                TODAY,
                "14일 실험: 카드 실행률 50% → 70% (+20%p)"));
  }

  @Test
  void cardExperimentDifferenceIsTheDifferenceOfTheTwoShownPercents() {
    // 0.504 → 50%, 0.696 → 70%. 화면에 보이는 두 숫자의 차이(+20)를 적어야 숫자가 서로 맞습니다. (실제 차이는 19.2)
    assertThat(cardItem(0.504, 0.696).evidence()).isEqualTo("14일 실험: 카드 실행률 50% → 70% (+20%p)");
    // 줄었거나 같아도 있는 그대로 적습니다.
    assertThat(cardItem(0.7, 0.5).evidence()).isEqualTo("14일 실험: 카드 실행률 70% → 50% (-20%p)");
    assertThat(cardItem(0.6, 0.601).evidence()).isEqualTo("14일 실험: 카드 실행률 60% → 60% (0%p)");
  }

  @Test
  void cardExperimentWithoutRatesOnlySaysTheLength() {
    assertThat(cardItem(null, 0.7).evidence()).isEqualTo("14일 실험으로 효과 확인");
    assertThat(cardItem(0.5, null).evidence()).isEqualTo("14일 실험으로 효과 확인");
  }

  // ─── 인사이트 ───

  private static Insight insight(boolean hypothesis, InsightResponse reaction, LocalDate... dates) {
    Insight insight =
        new Insight(
            1L, "오전에 공부한 날 순공이 40% 길었어", "morning-vs-night", hypothesis, TODAY, List.of(dates));
    insight.react(reaction);
    return insight;
  }

  @Test
  void onlyCertainInsightsCanBeRegistered() {
    LocalDate day = LocalDate.of(2026, 10, 1);

    // 가설이 아니면 올릴 수 있습니다. 반응을 안 눌렀어도, 몰랐는데 맞아·이미 알던 거야 여도 마찬가지입니다.
    assertThat(ManualItem.canRegisterInsight(insight(false, null, day))).isTrue();
    assertThat(ManualItem.canRegisterInsight(insight(false, InsightResponse.SURPRISED, day)))
        .isTrue();
    assertThat(ManualItem.canRegisterInsight(insight(false, InsightResponse.ALREADY_KNEW, day)))
        .isTrue();
    // 5일 미만이라 가설로 보여 준 것은 확실하지 않아서 올리지 않습니다.
    assertThat(ManualItem.canRegisterInsight(insight(true, InsightResponse.SURPRISED, day)))
        .isFalse();
    // 학생이 틀렸다고 한 것은 올리지 않습니다.
    assertThat(ManualItem.canRegisterInsight(insight(false, InsightResponse.WRONG, day))).isFalse();
  }

  @Test
  void insightItemShowsTheSentenceTheShownDateAndTheEvidenceDays() {
    Insight insight =
        insight(
            false,
            null,
            LocalDate.of(2026, 10, 16),
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 9));

    assertThat(ManualItem.ofInsight(insight, ManualCategory.RHYTHM))
        .isEqualTo(
            new ManualItem(
                ManualCategory.RHYTHM, "오전에 공부한 날 순공이 40% 길었어", TODAY, "근거 3일 (10/01 ~ 10/16)"));
  }
}
