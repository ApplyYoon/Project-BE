package com.ithopeschool.projectbe.domain.suggestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

// 실험을 1주 더 하는 규칙(이슈 #54 "연장은 한 번만 한다")을 확인합니다.
class ExperimentExtensionTest {

  @Test
  void unclearExperimentCanBeExtendedOnce() {
    // 애매함이고 아직 연장한 적이 없으면 1주 더 할 수 있습니다.
    assertThat(ExperimentExtension.canExtend(ExperimentEffect.UNCLEAR, 0)).isTrue();
  }

  @Test
  void alreadyExtendedExperimentCannotBeExtendedAgain() {
    // 한 번 연장했으면, 또 애매함이 나와도 더 늘릴 수 없습니다.
    assertThat(ExperimentExtension.canExtend(ExperimentEffect.UNCLEAR, 1)).isFalse();
    assertThat(ExperimentExtension.canExtend(ExperimentEffect.UNCLEAR, 2)).isFalse();
  }

  @Test
  void clearResultIsNeverExtended() {
    // 효과가 있거나 없다고 판정이 났으면 연장하지 않습니다. (사용설명서에 올리거나, 전략을 바꿉니다)
    assertThat(ExperimentExtension.canExtend(ExperimentEffect.EFFECTIVE, 0)).isFalse();
    assertThat(ExperimentExtension.canExtend(ExperimentEffect.NO_EFFECT, 0)).isFalse();
  }

  @Test
  void extensionAddsOneWeek() {
    assertThat(ExperimentExtension.extendedDays(14)).isEqualTo(21);
    assertThat(ExperimentExtension.extendedDays(7)).isEqualTo(14);
  }

  @Test
  void extendedExperimentKeepsItsStartDate() {
    // 10월 1일 시작, 14일 → 연장하면 21일. 시작일은 그대로라서 10월 21일까지가 실험이고 결과는 10월 22일에 나옵니다.
    LocalDate start = LocalDate.of(2026, 10, 1);
    int extendedDays = ExperimentExtension.extendedDays(14);

    assertThat(ExperimentProgress.resultDateOf(start, extendedDays))
        .isEqualTo(LocalDate.of(2026, 10, 22));
    assertThat(ExperimentProgress.isFinished(start, extendedDays, LocalDate.of(2026, 10, 21)))
        .isFalse();
    assertThat(ExperimentProgress.isFinished(start, extendedDays, LocalDate.of(2026, 10, 22)))
        .isTrue();
  }
}
