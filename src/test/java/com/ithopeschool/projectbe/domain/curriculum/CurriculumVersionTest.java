package com.ithopeschool.projectbe.domain.curriculum;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 교육과정 버전 규칙(교육부 고시 제2022-33호 부칙)을 확인합니다.
class CurriculumVersionTest {

  private static final LocalDate OCT_2026 = LocalDate.of(2026, 10, 18);

  @Test
  @DisplayName("2026학년도는 고1 · 고2 가 2022 개정, 고3 은 2015 개정")
  void year2026() {
    assertThat(CurriculumVersion.of(1, OCT_2026)).contains("2022");
    assertThat(CurriculumVersion.of(2, OCT_2026)).contains("2022");
    assertThat(CurriculumVersion.of(3, OCT_2026)).contains("2015");
  }

  @Test
  @DisplayName("학년도는 3월에 바뀐다: 2027년 2월의 고3 은 아직 2015 개정, 2027년 3월부터 2022 개정")
  void schoolYearStartsInMarch() {
    assertThat(CurriculumVersion.of(3, LocalDate.of(2027, 2, 28))).contains("2015");
    assertThat(CurriculumVersion.of(3, LocalDate.of(2027, 3, 2))).contains("2022");
    assertThat(CurriculumVersion.of(2, LocalDate.of(2026, 2, 10))).contains("2015");
  }

  @Test
  @DisplayName("학년을 모르거나 1~3 이 아니면 정하지 않는다")
  void unknownGrade() {
    assertThat(CurriculumVersion.of(null, OCT_2026)).isEmpty();
    assertThat(CurriculumVersion.of(0, OCT_2026)).isEmpty();
    assertThat(CurriculumVersion.of(4, OCT_2026)).isEmpty();
  }

  @Test
  @DisplayName("대본에 쓰는 이름")
  void label() {
    assertThat(CurriculumVersion.label("2022")).isEqualTo("2022 개정 교육과정");
  }
}
