package com.ithopeschool.projectbe.domain.variant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 단원 · 유형 분류체계 로더 테스트입니다. (VAR-01)
class VariantTaxonomyTest {

  private static VariantTaxonomy.TypeShape type(String code) {
    return new VariantTaxonomy.TypeShape(code, "유형", List.of());
  }

  private static VariantTaxonomy.FileShape file(VariantTaxonomy.UnitShape... units) {
    return new VariantTaxonomy.FileShape("test", List.of(units));
  }

  @Test
  @DisplayName("지금 taxonomy.json 을 읽는다: 10개 단원, 32개 유형")
  void loadsCurrentFile() {
    VariantTaxonomy taxonomy = new VariantTaxonomy();

    assertThat(taxonomy.units()).hasSize(10);
    assertThat(taxonomy.units().stream().mapToInt(u -> u.types().size()).sum()).isEqualTo(32);
    assertThat(taxonomy.findType("CM1-U05-T03"))
        .hasValueSatisfying(
            t -> {
              assertThat(t.unitCode()).isEqualTo("CM1-U05");
              assertThat(t.misconceptions()).isNotEmpty();
            });
    assertThat(taxonomy.toPromptList()).contains("CM1-U10 행렬 / CM1-U10-T03 행렬의 곱셈");
  }

  @Test
  @DisplayName("없는 코드는 빈 값이고, 유형이 다른 단원 것이면 짝이 아니다")
  void unknownCodes() {
    VariantTaxonomy taxonomy = new VariantTaxonomy();

    assertThat(taxonomy.findType("CM1-U99-T01")).isEmpty();
    assertThat(taxonomy.findType(null)).isEmpty();
    assertThat(taxonomy.findUnit("CM1-U99")).isEmpty();
    assertThat(taxonomy.isValidPair("CM1-U05", "CM1-U05-T03")).isTrue();
    assertThat(taxonomy.isValidPair("CM1-U04", "CM1-U05-T03")).isFalse();
    assertThat(taxonomy.isValidPair(null, null)).isFalse();
  }

  @Test
  @DisplayName("코드가 겹치면 서버가 켜지지 않게 에러")
  void duplicateCodes() {
    assertThatThrownBy(
            () ->
                new VariantTaxonomy(
                    file(
                        new VariantTaxonomy.UnitShape(
                            "U1", "단원", List.of(type("U1-T1"), type("U1-T1"))))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("겹쳐요");
  }

  @Test
  @DisplayName("유형 코드가 자기 단원 코드로 시작하지 않으면 에러")
  void typeOutsideUnit() {
    assertThatThrownBy(
            () ->
                new VariantTaxonomy(
                    file(new VariantTaxonomy.UnitShape("U1", "단원", List.of(type("U2-T1"))))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("속하지 않아요");
  }

  @Test
  @DisplayName("단원이 없거나 유형이 없는 단원이 있으면 에러")
  void emptyFile() {
    assertThatThrownBy(() -> new VariantTaxonomy(file())).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () -> new VariantTaxonomy(file(new VariantTaxonomy.UnitShape("U1", "단원", List.of()))))
        .isInstanceOf(IllegalStateException.class);
  }
}
