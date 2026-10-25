package com.ithopeschool.projectbe.domain.variant.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// G3 코드 안전 검사 단위 테스트입니다. 검사는 식을 계산하지 않고 모양만 읽습니다.
class SymjaGuardTest {

  private static void passes(String code) {
    assertThat(SymjaGuard.checkCode(code).passed()).as(code).isTrue();
  }

  private static String rejects(String code) {
    SymjaGuard.Verdict verdict = SymjaGuard.checkCode(code);
    assertThat(verdict.passed()).as(code).isFalse();
    return verdict.reason();
  }

  @Test
  @DisplayName("고1 문제를 푸는 보통 코드는 통과한다")
  void normalCodePasses() {
    passes("k = 2*3; r = x /. Solve(x^2 - 5*x + k == 0, x); Max(r)");
    passes("p = PolynomialRemainder(x^3 + 2*x + 1, x - 1, x); p");
    passes("m = {{1,2},{3,4}}; Det(m)");
    passes("Length(Select(Range(-10, 10), (#^2 - 4*# + 3 < 0) &))");
    passes("r = x /. Solve(x^2 - 4*x + 3 == 0, x); Min(r) < x < Max(r)");
    passes("Binomial(5, 2) * Factorial(3)");
    passes("solve(x^2 - 4 == 0, x) // Length");
  }

  @Test
  @DisplayName("허용 목록 밖의 함수는 거부한다 (파일 · 프로그램 · 자바 · 출력)")
  void rejectsDangerousFunctions() {
    assertThat(rejects("Import(x)")).contains("Import");
    assertThat(rejects("JavaNew(x)")).contains("JavaNew");
    assertThat(rejects("Get(x)")).contains("Get");
    assertThat(rejects("Print(1); 2")).contains("Print");
    assertThat(rejects("ToExpression(x)")).contains("ToExpression");
    rejects("SystemDialogInput()");
  }

  @Test
  @DisplayName("글자(따옴표)와 패턴(x_)은 거부한다")
  void rejectsStringsAndPatterns() {
    assertThat(rejects("ToString(\"a\")")).isNotBlank();
    assertThat(rejects("k = \"abc\"; 1")).contains("StringNode");
    assertThat(rejects("f(x_) := x^2; f(2)")).contains("PatternNode");
  }

  @Test
  @DisplayName("계산 없이 값만 적은 코드(하드코딩)는 거부한다")
  void rejectsHardcodedAnswers() {
    assertThat(rejects("7")).contains("하드코딩");
    rejects("3/2");
    rejects("{1, 2}");
    rejects("1 < x < 3");
    rejects("2*Sqrt(3)");
  }

  @Test
  @DisplayName("문법이 틀리거나 비었거나 너무 길면 거부한다")
  void rejectsBrokenCode() {
    assertThat(rejects("Solve(x^2 == ")).contains("문법");
    assertThat(rejects("  ")).contains("비어");
    assertThat(rejects("k = 1; " + "k + ".repeat(600) + "1")).isNotBlank();
  }

  @Test
  @DisplayName("e · n · d 처럼 Symja 의 내장 이름과 겹치는 한 글자 변수는 내장 기호로 보고 거부한다")
  void builtinSingleLetters() {
    assertThat(rejects("n = 3; n + 1")).contains("N");
    assertThat(SymjaGuard.builtinName("solve")).contains("Solve");
    assertThat(SymjaGuard.builtinName("k")).isEmpty();
  }

  @Test
  @DisplayName("선지는 값만 된다: 수 · 목록 · x 의 조건은 통과하고, 계산 함수와 다른 변수는 거부한다")
  void choices() {
    assertThat(SymjaGuard.checkChoice("3/2").passed()).isTrue();
    assertThat(SymjaGuard.checkChoice("2*Sqrt(3)").passed()).isTrue();
    assertThat(SymjaGuard.checkChoice("-1 + 2*I").passed()).isTrue();
    assertThat(SymjaGuard.checkChoice("{-1, 2}").passed()).isTrue();
    assertThat(SymjaGuard.checkChoice("{{1,2},{3,4}}").passed()).isTrue();
    assertThat(SymjaGuard.checkChoice("x < 1 || x > 3").passed()).isTrue();
    assertThat(SymjaGuard.checkChoice("1 <= x < 3").passed()).isTrue();

    assertThat(SymjaGuard.checkChoice("Solve(x == 1, x)").passed()).isFalse();
    assertThat(SymjaGuard.checkChoice("k + 1").passed()).isFalse();
    assertThat(SymjaGuard.checkChoice("").passed()).isFalse();
    assertThat(SymjaGuard.checkChoice("a = 3").passed()).isFalse();
  }
}
