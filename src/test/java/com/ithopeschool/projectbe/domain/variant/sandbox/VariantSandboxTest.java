package com.ithopeschool.projectbe.domain.variant.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.variant.VariantSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 샌드박스를 진짜 새 프로세스로 띄워 보는 테스트입니다. (G4 · G5, docs/VARIANT-SPEC.md 6-3)
// 프로세스를 띄우느라 한 번에 1초쯤 걸립니다.
class VariantSandboxTest {

  // 보통 계산은 1초쯤이지만, 전체 빌드 중에는 컴퓨터가 바빠서 프로세스가 늦게 켜질 수 있어 넉넉히 30초를 줍니다.
  // 메모리는 기본값과 같은 256MB.
  private final VariantSandbox sandbox =
      new VariantSandbox(new VariantSettings(3, false, 30, 256, 10, 2, 3, 5, 10, 7, 0.6, 20));

  // 시간 초과 테스트만 짧은 제한(5초)으로 끊는지 봅니다.
  private final VariantSandbox shortTimeoutSandbox =
      new VariantSandbox(new VariantSettings(3, false, 5, 256, 10, 2, 3, 5, 10, 7, 0.6, 20));

  private static SandboxRequest shortAnswer(String code, int answer) {
    return new SandboxRequest(code, "SHORT", null, null, answer);
  }

  private static SandboxRequest mc5(String code, List<String> choices, int answerIndex) {
    return new SandboxRequest(code, "MC5", choices, answerIndex, null);
  }

  @Test
  @DisplayName("단답형: 계산한 정수가 결과에 담긴다")
  void shortAnswerPasses() {
    SandboxResult result =
        sandbox.run(shortAnswer("k = 2*3; r = x /. Solve(x^2 - 5*x + k == 0, x); Max(r)", 3));

    assertThat(result.ok()).as(result.error()).isTrue();
    assertThat(result.integerValue()).isEqualTo(3);
    assertThat(result.computedLatex()).isEqualTo("3");
  }

  @Test
  @DisplayName("5지선다: 계산값과 같은 선지 번호 하나만 찾고, 선지 LaTeX 를 만든다")
  void multipleChoicePasses() {
    SandboxResult result =
        sandbox.run(
            mc5(
                "r = x /. Solve(x^2 - 2*x - 2 == 0, x); Max(r) - Min(r)",
                List.of("Sqrt(3)", "2*Sqrt(3)", "2", "4", "3*Sqrt(2)"),
                1));

    assertThat(result.ok()).as(result.error()).isTrue();
    assertThat(result.matchedIndices()).containsExactly(1);
    assertThat(result.choicesDistinct()).isTrue();
    assertThat(result.choicesLatex()).hasSize(5);
    assertThat(result.choicesLatex().get(1)).contains("\\sqrt{3}");
  }

  @Test
  @DisplayName("5지선다: 계산값이 AI 가 말한 정답이 아닌 다른 선지와 같으면 그 번호가 나온다")
  void multipleChoiceOtherMatch() {
    SandboxResult result =
        sandbox.run(mc5("Det({{1,2},{3,4}})", List.of("2", "-2", "10", "-10", "0"), 0));

    assertThat(result.ok()).isTrue();
    assertThat(result.matchedIndices()).containsExactly(1);
  }

  @Test
  @DisplayName("선지끼리 값이 같으면(1/2 과 0.5) choicesDistinct 가 false 다")
  void duplicateChoices() {
    SandboxResult result =
        sandbox.run(mc5("Max({1/2, 1/3})", List.of("1/2", "0.5", "1/3", "1", "2"), 0));

    assertThat(result.ok()).isTrue();
    assertThat(result.choicesDistinct()).isFalse();
  }

  @Test
  @DisplayName("부등식 답: 경계값으로 만든 조건이 같은 조건 선지와 맞는다")
  void conditionAnswer() {
    SandboxResult result =
        sandbox.run(
            mc5(
                "r = x /. Solve(x^2 - 4*x + 3 == 0, x); Min(r) < x < Max(r)",
                List.of("1 < x < 3", "1 <= x <= 3", "x < 1 || x > 3", "-3 < x < -1", "x < 3"),
                0));

    assertThat(result.ok()).as(result.error()).isTrue();
    assertThat(result.matchedIndices()).containsExactly(0);
    assertThat(result.choicesDistinct()).isTrue();
  }

  @Test
  @DisplayName("코드가 x = 5 처럼 x 에 값을 넣어도 선지의 x 조건은 그대로다")
  void codeCannotChangeChoices() {
    SandboxResult result =
        sandbox.run(
            mc5(
                "x = 5; 1 < y < 3 /. y -> x; k = x - 4; k",
                List.of("1", "x < 1", "2", "3", "4"),
                0));

    assertThat(result.ok()).as(result.error()).isTrue();
    assertThat(result.matchedIndices()).containsExactly(0);
  }

  @Test
  @DisplayName("끝없이 도는 코드는 시간 제한에서 끊긴다")
  void infiniteLoopTimesOut() {
    SandboxResult result =
        shortTimeoutSandbox.run(shortAnswer("k = 0; While(True, k = k + 1); k", 1));

    assertThat(result.ok()).isFalse();
    assertThat(result.error()).contains("시간 제한");
  }

  @Test
  @DisplayName("메모리를 크게 쓰는 코드는 실패로 끝난다 (서버는 멀쩡)")
  void hugeMemoryFails() {
    SandboxResult result = sandbox.run(shortAnswer("t = Range(1, 10^9); Length(t)", 1));

    assertThat(result.ok()).isFalse();
  }

  @Test
  @DisplayName("문법이 틀린 코드는 계산 오류로 끝난다")
  void syntaxErrorFails() {
    SandboxResult result = sandbox.run(shortAnswer("Solve(x^2 == ", 1));

    assertThat(result.ok()).isFalse();
    assertThat(result.error()).isNotBlank();
  }

  @Test
  @DisplayName("정답 자리에 문자가 남은 식이 오면 실패한다")
  void symbolicAnswerFails() {
    SandboxResult result = sandbox.run(shortAnswer("Expand((a + 1)^2)", 1));

    assertThat(result.ok()).isFalse();
    assertThat(result.error()).contains("계산한 값");
  }

  @Test
  @DisplayName("실행이 끝나면 임시 폴더를 지운다")
  void cleansUpTempDirectory() throws IOException {
    Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
    long before = countSandboxDirs(tmp);

    sandbox.run(shortAnswer("1 + Binomial(3, 1)", 4));

    assertThat(countSandboxDirs(tmp)).isEqualTo(before);
  }

  @Test
  @DisplayName("프롬프트(variant_v2.md)에 적은 예시 코드 3개는 안전 검사와 샌드박스를 통과한다")
  void promptExamplesWork() {
    String example1 = "s = Solve({a + b == 7, a - b == 3}, {a, b}); k = (a*b) /. First(s); k";
    String example2 = "r = x /. Solve(x^2 - 4*x + 3 == 0, x); Min(r) < x < Max(r)";
    String example3 = "m1 = {{1, 2}, {3, 4}}; m2 = {{0, 1}, {1, 0}}; m1.m2";
    for (String code : List.of(example1, example2, example3)) {
      assertThat(SymjaGuard.checkCode(code).passed()).as(code).isTrue();
    }

    assertThat(sandbox.run(shortAnswer(example1, 10)).integerValue()).isEqualTo(10);
    assertThat(
            sandbox
                .run(mc5(example2, List.of("1 < x < 3", "x < 1 || x > 3", "1", "2", "3"), 0))
                .matchedIndices())
        .containsExactly(0);
    assertThat(
            sandbox
                .run(
                    mc5(
                        example3,
                        List.of(
                            "{{2, 1}, {4, 3}}",
                            "{{3, 4}, {1, 2}}",
                            "{{0, 2}, {3, 0}}",
                            "{{1, 2}, {3, 4}}",
                            "{{2, 4}, {1, 3}}"),
                        0))
                .matchedIndices())
        .containsExactly(0);
  }

  @Test
  @DisplayName("결과 줄을 못 찾거나 모양이 다르면 실패 결과로 바꾼다")
  void parseOutput() {
    assertThat(VariantSandbox.parse("아무 글").ok()).isFalse();
    assertThat(VariantSandbox.parse(VariantSandboxMain.RESULT_MARKER + "{깨진").ok()).isFalse();
  }

  private static long countSandboxDirs(Path tmp) throws IOException {
    try (Stream<Path> files = Files.list(tmp)) {
      return files.filter(p -> p.getFileName().toString().startsWith("variant-sandbox-")).count();
    }
  }
}
