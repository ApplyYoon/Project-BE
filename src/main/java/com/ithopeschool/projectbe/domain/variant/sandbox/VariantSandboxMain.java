package com.ithopeschool.projectbe.domain.variant.sandbox;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.matheclipse.core.basic.Config;
import org.matheclipse.core.eval.EvalEngine;
import org.matheclipse.core.eval.ExprEvaluator;
import org.matheclipse.core.interfaces.IExpr;
import tools.jackson.databind.json.JsonMapper;

// 샌드박스 프로그램입니다. 서버가 아니라 **따로 띄운 자바 프로세스**에서 돕니다. (VariantSandbox 가 띄움)
// AI 가 쓴 Symja 코드와 선지를 실제로 계산하는 곳은 여기 한 곳뿐입니다. (docs/VARIANT-SPEC.md 6-3)
//
//   입력(stdin)  : SandboxRequest JSON 한 덩어리
//   출력(stdout) : RESULT_MARKER 로 시작하는 한 줄 + SandboxResult JSON
//
// 왜 따로 띄우나요? AI 가 쓴 코드가 끝없이 돌거나 메모리를 다 쓰더라도 서버는 멀쩡해야 하기 때문입니다.
// 서버는 이 프로세스에 시간 · 메모리 제한을 걸고, 넘으면 강제로 끕니다.
//
// 계산 순서
//   1) 선지를 먼저 계산합니다. (AI 코드가 x = 5 처럼 변수에 값을 넣어도 선지의 x 는 영향을 받지 않게)
//   2) AI 코드를 계산합니다. 마지막 값이 정답입니다.
//   3) 정답과 선지의 종류 · 같음을 판정하고(SymjaValues), 화면용 LaTeX 를 만듭니다.
public final class VariantSandboxMain {

  // 결과 줄의 앞머리. Symja 가 경고 글을 찍더라도 결과 줄을 헷갈리지 않게 붙입니다.
  public static final String RESULT_MARKER = "@@VARIANT_SANDBOX_RESULT@@";

  private static final JsonMapper JSON = JsonMapper.builder().build();

  // 에러 문장의 최대 길이 (verification_log 에 들어갑니다)
  private static final int MAX_ERROR_LENGTH = 300;

  private VariantSandboxMain() {}

  public static void main(String[] args) throws IOException {
    PrintStream realOut = new PrintStream(System.out, true, StandardCharsets.UTF_8);
    // AI 코드나 Symja 가 찍는 글이 결과 JSON 에 섞이지 않게, 계산하는 동안 화면 출력을 버립니다.
    PrintStream silent = new PrintStream(OutputStream.nullOutputStream());
    System.setOut(silent);
    System.setErr(silent);

    SandboxResult result;
    try {
      String input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
      result = run(JSON.readValue(input, SandboxRequest.class));
    } catch (RuntimeException | StackOverflowError e) {
      result = SandboxResult.failed("입력을 읽지 못했어요: " + e.getClass().getSimpleName());
    }
    realOut.println(RESULT_MARKER + JSON.writeValueAsString(result));
    realOut.flush();
    // Symja 가 만든 일꾼 스레드가 남아 있어도 바로 끝냅니다.
    System.exit(0);
  }

  // 일감 하나를 계산합니다. 테스트에서는 이 메서드를 바로 부릅니다.
  public static SandboxResult run(SandboxRequest request) {
    // 파일 읽기 · 쓰기 같은 기능을 끕니다. (G3 허용 목록 검사에 더해 한 번 더 막는 것)
    Config.FILESYSTEM_ENABLED = false;
    Config.JAVA_UNSAFE = false;

    try {
      ExprEvaluator evaluator = new ExprEvaluator();
      EvalEngine engine = evaluator.getEvalEngine();
      PrintStream silent = new PrintStream(OutputStream.nullOutputStream());
      engine.setQuietMode(true);
      engine.setOutPrintStream(silent);
      engine.setErrorPrintStream(silent);
      SymjaValues values = new SymjaValues(engine);

      boolean isShort = "SHORT".equals(request.format());
      List<String> choiceTexts = request.choices() == null ? List.of() : request.choices();

      // 1) 선지 먼저
      List<IExpr> choices = new ArrayList<>();
      for (int i = 0; i < choiceTexts.size(); i++) {
        IExpr choice = evaluator.eval(choiceTexts.get(i));
        if (values.kindOf(choice) == SymjaValues.Kind.OTHER) {
          return SandboxResult.failed(
              "선지 " + (i + 1) + " 이(가) 수 · 집합 · 행렬 · x 의 부등식 조건이 아니에요: " + shorten(choice));
        }
        choices.add(choice);
      }

      // 2) AI 코드
      IExpr computed = evaluator.eval(request.code());
      SymjaValues.Kind kind = values.kindOf(computed);
      if (kind == SymjaValues.Kind.OTHER) {
        return SandboxResult.failed("계산한 값이 수 · 집합 · 행렬 · x 의 부등식 조건이 아니에요: " + shorten(computed));
      }

      // 3) 판정과 LaTeX
      String computedLatex = values.latex(computed);
      List<String> choicesLatex = new ArrayList<>();
      for (IExpr choice : choices) {
        choicesLatex.add(values.latex(choice));
      }

      Integer integerValue = kind == SymjaValues.Kind.NUMBER ? values.integerValue(computed) : null;
      List<Integer> matchedIndices = new ArrayList<>();
      boolean distinct = true;
      if (!isShort) {
        for (int i = 0; i < choices.size(); i++) {
          if (values.same(computed, choices.get(i))) {
            matchedIndices.add(i);
          }
          for (int j = i + 1; j < choices.size(); j++) {
            if (values.same(choices.get(i), choices.get(j))) {
              distinct = false;
            }
          }
        }
      }
      return new SandboxResult(
          true,
          shorten(computed),
          computedLatex,
          choicesLatex,
          integerValue,
          matchedIndices,
          distinct,
          null);
    } catch (RuntimeException | StackOverflowError e) {
      // 문법 오류, 0 으로 나누기 등. 받은 글을 그대로 찍지 않고 종류와 짧은 설명만 남깁니다.
      return SandboxResult.failed(
          "계산 중 오류: " + e.getClass().getSimpleName() + " " + shorten(e.getMessage()));
    }
  }

  private static String shorten(Object value) {
    String text = String.valueOf(value).replaceAll("\\s+", " ").strip();
    return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH) + "…";
  }
}
