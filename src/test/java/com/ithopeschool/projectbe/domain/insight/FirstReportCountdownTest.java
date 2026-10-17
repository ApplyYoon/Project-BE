package com.ithopeschool.projectbe.domain.insight;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 첫 리포트(14일 차)까지 남은 날 계산을 확인합니다. FE 홈 화면과 같은 숫자가 나와야 합니다.
class FirstReportCountdownTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

  @Test
  @DisplayName("기록이 없으면 지난 0일 · 남은 14일 · 아직")
  void noRecord() {
    assertThat(FirstReportCountdown.of(null, TODAY))
        .isEqualTo(new FirstReportCountdown.Status(null, 0, 14, false));
  }

  @Test
  @DisplayName("첫 기록이 오늘이면 첫날(1일)이라 남은 13일")
  void firstDay() {
    assertThat(FirstReportCountdown.of(TODAY, TODAY))
        .isEqualTo(new FirstReportCountdown.Status(TODAY, 1, 13, false));
  }

  @Test
  @DisplayName("13일째는 남은 1일, 14일째부터 ready 이고 그 뒤로는 14일에서 멈춘다")
  void reachesFourteen() {
    LocalDate oct5 = LocalDate.of(2026, 10, 5); // 10/05 ~ 10/17 = 13일
    LocalDate oct4 = LocalDate.of(2026, 10, 4); // 10/04 ~ 10/17 = 14일
    LocalDate sep1 = LocalDate.of(2026, 9, 1);

    assertThat(FirstReportCountdown.of(oct5, TODAY))
        .isEqualTo(new FirstReportCountdown.Status(oct5, 13, 1, false));
    assertThat(FirstReportCountdown.of(oct4, TODAY))
        .isEqualTo(new FirstReportCountdown.Status(oct4, 14, 0, true));
    assertThat(FirstReportCountdown.of(sep1, TODAY))
        .isEqualTo(new FirstReportCountdown.Status(sep1, 14, 0, true));
  }

  @Test
  @DisplayName("미리 세운 계획만 있어서 첫 기록이 내일 이후면 0일에서 멈춘다 (음수가 되지 않는다)")
  void futureFirstRecord() {
    LocalDate later = TODAY.plusDays(3);

    assertThat(FirstReportCountdown.of(later, TODAY))
        .isEqualTo(new FirstReportCountdown.Status(later, 0, 14, false));
  }
}
