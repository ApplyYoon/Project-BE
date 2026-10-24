package com.ithopeschool.projectbe.domain.variant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 문제 글 지문(fingerprint) 테스트입니다. (docs/VARIANT-SPEC.md 4장 끝)
class VariantFingerprintTest {

  @Test
  @DisplayName("띄어쓰기 · LaTeX 서식 명령 · 대소문자만 다르면 같은 지문이다")
  void ignoresFormatting() {
    assertThat(VariantFingerprint.of("$\\left( x+1 \\right)^2$ 의 값"))
        .isEqualTo(VariantFingerprint.of("$(x+1)^2$의   값"));
    assertThat(VariantFingerprint.of("$A\\,B$")).isEqualTo(VariantFingerprint.of("$ab$"));
  }

  @Test
  @DisplayName("숫자가 다르면 다른 지문이고, 지문은 64글자 16진수다")
  void differentNumbers() {
    assertThat(VariantFingerprint.of("$x^2-5x+6=0$"))
        .isNotEqualTo(VariantFingerprint.of("$x^2-5x+4=0$"));
    assertThat(VariantFingerprint.of("문제")).hasSize(64).matches("[0-9a-f]+");
    assertThat(VariantFingerprint.of(null)).isEqualTo(VariantFingerprint.of(""));
  }
}
