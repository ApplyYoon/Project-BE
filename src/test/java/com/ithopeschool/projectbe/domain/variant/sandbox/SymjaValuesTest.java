package com.ithopeschool.projectbe.domain.variant.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.matheclipse.core.eval.ExprEvaluator;
import org.matheclipse.core.interfaces.IExpr;

// 정답 값의 종류 · 같음 판정 · LaTeX 단위 테스트입니다. (docs/VARIANT-SPEC.md 6-1 표)
// 실제 서비스에서는 샌드박스 안에서만 계산하지만, 여기서는 테스트가 직접 쓴 식이라 바로 계산합니다.
class SymjaValuesTest {

  private ExprEvaluator evaluator;
  private SymjaValues values;

  @BeforeEach
  void setUp() {
    evaluator = new ExprEvaluator();
    values = new SymjaValues(evaluator.getEvalEngine());
  }

  private IExpr v(String text) {
    return evaluator.eval(text);
  }

  private boolean same(String a, String b) {
    return values.same(v(a), v(b));
  }

  @Test
  @DisplayName("수: 2*Sqrt(3) 과 Sqrt(12) 는 같고, 1/2 과 0.5 도 같고, Sqrt(13) 과는 다르다")
  void numbers() {
    assertThat(same("2*Sqrt(3)", "Sqrt(12)")).isTrue();
    assertThat(same("1/2", "0.5")).isTrue();
    assertThat(same("2*Sqrt(3)", "Sqrt(13)")).isFalse();
    assertThat(same("(1+2*I)*(3-I)", "5+5*I")).isTrue();
    assertThat(same("3", "-3")).isFalse();
  }

  @Test
  @DisplayName("집합: 순서와 겹치는 원소는 무시하고, 원소가 다르면 다르다")
  void sets() {
    assertThat(same("{2, -1}", "{-1, 2}")).isTrue();
    assertThat(same("{2, 2, -1}", "{-1, 2}")).isTrue();
    assertThat(same("{1, Sqrt(4)}", "{2, 1}")).isTrue();
    assertThat(same("{1, 2}", "{1, 3}")).isFalse();
    assertThat(same("{1, 2}", "{1, 2, 3}")).isFalse();
  }

  @Test
  @DisplayName("행렬: 크기와 성분이 같으면 같고, 하나라도 다르면 다르다")
  void matrices() {
    assertThat(same("{{1,2},{3,4}}", "{{1,2},{3,4}}")).isTrue();
    assertThat(same("{{1,2},{3,4}}", "{{1,2},{3,5}}")).isFalse();
    assertThat(same("{{1,2},{3,4}}", "{{1,3},{2,4}}")).isFalse();
    assertThat(same("{{1,2},{3,4}}.{{0,1},{1,0}}", "{{2,1},{4,3}}")).isTrue();
  }

  @Test
  @DisplayName("부등식 조건: 열린 · 닫힌 구간을 구분하고, 같은 해집합을 다르게 쓴 것은 같다")
  void conditions() {
    assertThat(same("1 < x < 3", "x > 1 && x < 3")).isTrue();
    assertThat(same("1 < x < 3", "1 <= x <= 3")).isFalse();
    assertThat(same("1 < x <= 3", "1 < x < 3")).isFalse();
    assertThat(same("x < 1 || x > 3", "x > 3 || x < 1")).isTrue();
    assertThat(same("x < 1 || x > 3", "x <= 1 || x >= 3")).isFalse();
    assertThat(same("x <= -Sqrt(2) || x >= Sqrt(2)", "x <= -Sqrt(2) || x >= Sqrt(2)")).isTrue();
    assertThat(same("x < 2", "x < 3")).isFalse();
  }

  @Test
  @DisplayName("종류가 다르면 다르다: 수 3 과 집합 {3}, 수와 조건")
  void differentKinds() {
    assertThat(same("3", "{3}")).isFalse();
    assertThat(same("3", "x < 3")).isFalse();
    assertThat(values.kindOf(v("3"))).isEqualTo(SymjaValues.Kind.NUMBER);
    assertThat(values.kindOf(v("{3}"))).isEqualTo(SymjaValues.Kind.SET);
    assertThat(values.kindOf(v("{{1}}"))).isEqualTo(SymjaValues.Kind.MATRIX);
    assertThat(values.kindOf(v("1 < x < 3"))).isEqualTo(SymjaValues.Kind.CONDITION);
  }

  @Test
  @DisplayName("문자가 남은 식은 정답으로 쓸 수 없는 값(OTHER)이고, 무엇과도 같지 않다")
  void otherValues() {
    assertThat(values.kindOf(v("a + 1"))).isEqualTo(SymjaValues.Kind.OTHER);
    assertThat(same("a + 1", "a + 1")).isFalse();
  }

  @Test
  @DisplayName("정수 값: 12/4 는 3, 3/2 · 2.5 는 정수가 아니다")
  void integerValue() {
    assertThat(values.integerValue(v("12/4"))).isEqualTo(3);
    assertThat(values.integerValue(v("999"))).isEqualTo(999);
    assertThat(values.integerValue(v("3/2"))).isNull();
    assertThat(values.integerValue(v("2.5"))).isNull();
  }

  @Test
  @DisplayName("LaTeX: 분수 · 근호 · 행렬 · '또는' 을 화면용으로 만든다")
  void latex() {
    assertThat(values.latex(v("3/2"))).isEqualTo("\\frac{3}{2}");
    assertThat(values.latex(v("2*Sqrt(3)"))).contains("\\sqrt{3}");
    assertThat(values.latex(v("{{1,2},{3,4}}"))).contains("\\begin{array}");
    assertThat(values.latex(v("x < 1 || x > 3"))).contains("또는");
  }
}
