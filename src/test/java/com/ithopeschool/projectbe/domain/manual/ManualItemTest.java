package com.ithopeschool.projectbe.domain.manual;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.suggestion.ExperimentEffect;
import java.time.LocalDate;
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
}
