package com.ithopeschool.projectbe.domain.variant.sandbox;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.matheclipse.core.convert.AST2Expr;
import org.matheclipse.parser.client.Parser;
import org.matheclipse.parser.client.ast.ASTNode;
import org.matheclipse.parser.client.ast.FloatNode;
import org.matheclipse.parser.client.ast.FractionNode;
import org.matheclipse.parser.client.ast.FunctionNode;
import org.matheclipse.parser.client.ast.IntegerNode;
import org.matheclipse.parser.client.ast.NumberNode;
import org.matheclipse.parser.client.ast.SymbolNode;

// G3 코드 안전 검사입니다. AI 가 쓴 Symja 코드와 선지를 **계산하지 않고 모양만 읽어서** 검사합니다.
// (docs/VARIANT-SPEC.md 6-2) 서버(메인 프로세스)에서 돌기 때문에 절대로 계산(eval)하지 않습니다.
//
// 금지 목록이 아니라 **허용 목록** 방식입니다. 허용한 함수 · 기호만 쓸 수 있고, 나머지는 모두 거부합니다.
//   - Symja 의 내장 이름(함수 · 상수)은 아래 CODE_ALLOWED 에 있어야 합니다. (파일 읽기 Import, 프로그램 실행 Run 등은 없음)
//   - 내장 이름이 아닌 이름은 학생 문제의 변수(x, k, r1 …)로 봅니다. 영문자로 시작하고 영문자 · 숫자만, 20자까지.
//   - 글자(따옴표 "...")와 패턴(x_)은 쓸 수 없습니다. (글자를 코드로 바꾸는 우회를 막으려고)
//   - 코드가 수만 있는 식 하나(예: "6", "3/2")면 계산 없이 정답을 적은 것(하드코딩)이라 거부합니다.
//   - 선지는 "값" 만 됩니다: 수 · 목록 · 부등식 조건을 만드는 기호와 변수 x 만 허용합니다.
public final class SymjaGuard {

  public static final int MAX_CODE_LENGTH = 2000;
  public static final int MAX_CHOICE_LENGTH = 200;
  // 한 코드 안의 마디 수 한도 (아주 긴 식으로 서버를 느리게 만드는 것을 막음)
  private static final int MAX_NODES = 3000;

  // 변수 이름의 모양
  private static final Pattern VARIABLE = Pattern.compile("[A-Za-z][A-Za-z0-9]{0,19}");

  // 값 · 계산 모두에 쓰는 기본 기호 (수식 모양을 만드는 것들)
  private static final Set<String> VALUE_ALLOWED =
      Set.of(
          "Plus",
          "Times",
          "Power",
          "Rational",
          "Complex",
          "Sqrt",
          "Minus",
          "Subtract",
          "Divide",
          "List",
          "I",
          "Pi",
          "Less",
          "LessEqual",
          "Greater",
          "GreaterEqual",
          "Inequality",
          "And",
          "Or",
          "Not",
          "True",
          "False");

  // 계산 코드에서 더 쓸 수 있는 것들 (고1 공통수학1 에 필요한 것 위주)
  private static final Set<String> CODE_ONLY_ALLOWED =
      Set.of(
          // 문장 · 변수 · 규칙
          "CompoundExpression",
          "Set",
          "SetDelayed",
          "Rule",
          "RuleDelayed",
          "ReplaceAll",
          "Function",
          "Slot",
          "Module",
          "Block",
          "With",
          "If",
          "Which",
          "Equal",
          "Unequal",
          "Increment",
          "PreIncrement",
          "AddTo",
          "SubtractFrom",
          "Null",
          // 목록
          "Part",
          "Span",
          "Table",
          "Range",
          "Sum",
          "Product",
          "Map",
          "Apply",
          "Select",
          "Count",
          "Length",
          "First",
          "Last",
          "Rest",
          "Most",
          "Take",
          "Drop",
          "Append",
          "Prepend",
          "Join",
          "Union",
          "Intersection",
          "Complement",
          "Sort",
          "Reverse",
          "Total",
          "Flatten",
          "DeleteDuplicates",
          "MemberQ",
          "FreeQ",
          "Do",
          "While",
          "For",
          // 수
          "Abs",
          "Sign",
          "Floor",
          "Ceiling",
          "Round",
          "Mod",
          "Quotient",
          "GCD",
          "LCM",
          "Divisors",
          "PrimeQ",
          "IntegerQ",
          "EvenQ",
          "OddQ",
          "NumericQ",
          "Boole",
          "Max",
          "Min",
          "Numerator",
          "Denominator",
          "Factorial",
          "Binomial",
          "Permutations",
          "Subsets",
          "Tuples",
          // 식 · 방정식
          "Solve",
          "Reduce",
          "Roots",
          "Expand",
          "ExpandAll",
          "Factor",
          "Simplify",
          "FullSimplify",
          "Together",
          "Apart",
          "Cancel",
          "Collect",
          "Coefficient",
          "CoefficientList",
          "Exponent",
          "PolynomialQuotient",
          "PolynomialRemainder",
          "PolynomialGCD",
          "PolynomialQ",
          "Discriminant",
          // 복소수
          "Re",
          "Im",
          "Conjugate",
          // 행렬
          "Det",
          "Inverse",
          "Transpose",
          "Dot",
          "IdentityMatrix",
          "MatrixPower",
          "Dimensions",
          "ConstantArray");

  // 계산 없이 모양만 만드는 기호 (하드코딩 검사에서 "계산이 없다" 고 보는 것들)
  private static final Set<String> ARITHMETIC_ONLY =
      Set.of(
          "Plus",
          "Times",
          "Power",
          "Rational",
          "Complex",
          "Sqrt",
          "Minus",
          "Subtract",
          "Divide",
          "List",
          "I",
          "Pi",
          "Less",
          "LessEqual",
          "Greater",
          "GreaterEqual",
          "Inequality",
          "And",
          "Or",
          "Not",
          "True",
          "False");

  private SymjaGuard() {}

  // 검사 결과: 통과면 reason 이 비어 있습니다.
  public record Verdict(boolean passed, String reason) {
    static Verdict pass() {
      return new Verdict(true, null);
    }

    static Verdict fail(String reason) {
      return new Verdict(false, reason);
    }
  }

  // 계산 코드 검사
  public static Verdict checkCode(String code) {
    if (code == null || code.isBlank()) {
      return Verdict.fail("검증 코드가 비어 있어요");
    }
    if (code.length() > MAX_CODE_LENGTH) {
      return Verdict.fail("검증 코드가 " + MAX_CODE_LENGTH + "자를 넘어요");
    }
    Optional<ASTNode> parsed = parse(code);
    if (parsed.isEmpty()) {
      return Verdict.fail("검증 코드의 문법이 틀렸어요");
    }
    Scan scan = new Scan(true);
    Optional<String> problem = scan.visit(parsed.get());
    if (problem.isPresent()) {
      return Verdict.fail(problem.get());
    }
    if (!scan.hasComputation) {
      return Verdict.fail("검증 코드가 계산 없이 정답 값만 적었어요(하드코딩). 조건에서 출발해 계산해 주세요");
    }
    return Verdict.pass();
  }

  // 선지 하나 검사: 값만 됩니다. (변수는 x 하나만)
  public static Verdict checkChoice(String choice) {
    if (choice == null || choice.isBlank()) {
      return Verdict.fail("빈 선지가 있어요");
    }
    if (choice.length() > MAX_CHOICE_LENGTH) {
      return Verdict.fail("선지가 " + MAX_CHOICE_LENGTH + "자를 넘어요");
    }
    Optional<ASTNode> parsed = parse(choice);
    if (parsed.isEmpty()) {
      return Verdict.fail("선지의 문법이 틀렸어요: " + choice);
    }
    Optional<String> problem = new Scan(false).visit(parsed.get());
    return problem
        .map(reason -> Verdict.fail(reason + " (선지: " + choice + ")"))
        .orElse(Verdict.pass());
  }

  private static Optional<ASTNode> parse(String text) {
    try {
      // true: 함수를 f(x) 처럼 둥근 괄호로 쓰는 문법 (프롬프트와 같은 문법)
      return Optional.ofNullable(new Parser(true).parse(text));
    } catch (RuntimeException e) {
      // 문법 오류(SyntaxError)도 여기로 옵니다. 틀린 코드라고만 알려 줍니다.
      return Optional.empty();
    }
  }

  // 이름이 Symja 의 내장 이름이면 공식 이름(예: "solve" → "Solve"), 아니면 빈 값
  // Symja 는 대문자 · 소문자를 가리지 않고 내장 이름으로 알아듣기 때문에(예: e → E, n → N) 소문자로 바꿔 찾습니다.
  static Optional<String> builtinName(String name) {
    return Optional.ofNullable(BUILTIN_NAMES.get(name.toLowerCase(Locale.ROOT)));
  }

  // Symja 의 내장 이름 목록 (소문자 → 공식 이름)
  // Symja 엔진을 켜지 않고도 읽을 수 있는 이름 배열에서 만듭니다. 엔진을 켜면 서버 메모리를 수십 MB 더 쓰기 때문입니다.
  private static final Map<String, String> BUILTIN_NAMES = loadBuiltinNames();

  private static Map<String, String> loadBuiltinNames() {
    Map<String, String> names = new HashMap<>();
    String[][] groups = {
      AST2Expr.FUNCTION_STRINGS,
      AST2Expr.SYMBOL_STRINGS,
      AST2Expr.UPPERCASE_SYMBOL_STRINGS,
      AST2Expr.DOLLAR_STRINGS,
      AST2Expr.PHYSICAL_CONSTANTS_STRINGS
    };
    for (String[] group : groups) {
      for (String name : group) {
        names.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
      }
    }
    return Map.copyOf(names);
  }

  // 식의 마디를 하나씩 돌며 검사합니다.
  private static final class Scan {
    private final boolean code;
    private int nodes;
    // 계산 함수(Solve 등)나 대입(=)이 한 번이라도 있었는지
    private boolean hasComputation;

    Scan(boolean code) {
      this.code = code;
    }

    Optional<String> visit(ASTNode node) {
      if (++nodes > MAX_NODES) {
        return Optional.of("식이 너무 길어요");
      }
      if (node instanceof FunctionNode function) {
        for (ASTNode child : function) {
          Optional<String> problem = visit(child);
          if (problem.isPresent()) {
            return problem;
          }
        }
        return Optional.empty();
      }
      if (node instanceof SymbolNode symbol) {
        return checkName(symbol.getString());
      }
      if (node instanceof IntegerNode
          || node instanceof FractionNode
          || node instanceof FloatNode
          || node instanceof NumberNode) {
        return Optional.empty();
      }
      // 글자("..."), 패턴(x_) 등은 쓸 수 없습니다.
      return Optional.of("쓸 수 없는 모양이 있어요: " + node.getClass().getSimpleName());
    }

    private Optional<String> checkName(String name) {
      Optional<String> builtin = builtinName(name);
      if (builtin.isPresent()) {
        String official = builtin.get();
        boolean allowed =
            VALUE_ALLOWED.contains(official) || (code && CODE_ONLY_ALLOWED.contains(official));
        if (!allowed) {
          return Optional.of("쓸 수 없는 함수 · 기호예요: " + official);
        }
        if (!ARITHMETIC_ONLY.contains(official)) {
          hasComputation = true;
        }
        return Optional.empty();
      }
      // 내장 이름이 아니면 문제의 변수입니다.
      if (!VARIABLE.matcher(name).matches()) {
        return Optional.of("변수 이름은 영문자로 시작하는 영문자 · 숫자만 쓸 수 있어요: " + name);
      }
      if (!code && !name.equals("x")) {
        return Optional.of("선지에는 변수 x 만 쓸 수 있어요: " + name);
      }
      return Optional.empty();
    }
  }
}
