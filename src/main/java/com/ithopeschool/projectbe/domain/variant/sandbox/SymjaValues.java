package com.ithopeschool.projectbe.domain.variant.sandbox;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.matheclipse.core.eval.EvalEngine;
import org.matheclipse.core.eval.TeXUtilities;
import org.matheclipse.core.expression.F;
import org.matheclipse.core.expression.S;
import org.matheclipse.core.interfaces.IAST;
import org.matheclipse.core.interfaces.IExpr;

// 정답 값의 "종류" 를 알아내고, 두 값이 같은지 판정하고, 화면용 LaTeX 를 만드는 도구입니다.
// (docs/VARIANT-SPEC.md 6-1 표)
//
// ⚠️ 이 도구는 Symja 로 계산을 합니다. 그래서 **샌드박스 프로그램(VariantSandboxMain) 안에서만** 씁니다.
//    서버(메인 프로세스)에서 AI 가 쓴 글자를 계산하지 않는다는 절대 규칙 2번 때문입니다. (테스트는 예외)
// ⚠️ 계산이 오래 걸리는 식(아주 큰 거듭제곱 등)은 이 도구가 막지 않습니다. 샌드박스의 시간 제한이 막습니다.
//
// 값의 종류와 "같다" 의 뜻
//   수(NUMBER)       3/2, 2*Sqrt(3), -1+2*I        두 값의 차가 0 인지 (PossibleZeroQ). 1/2 와 0.5 는 같습니다
//   집합(SET)        {-1, 2}                       겹치는 원소를 빼고 크기가 같고, 원소마다 같은 수가 있는지 (순서 무시)
//   행렬(MATRIX)     {{1,2},{3,4}}                 크기가 같고 성분마다 같은 수인지
//   조건(CONDITION)  1 < x < 3,  x < 1 || x > 3     두 조건의 경계값과 그 사이 · 바깥 값을 넣어 참 · 거짓이 모두 같은지
// 종류가 다르면 다릅니다. (수 3 과 집합 {3} 은 다름)
public final class SymjaValues {

  // 값의 종류
  public enum Kind {
    NUMBER,
    SET,
    MATRIX,
    CONDITION,
    // 위의 넷이 아님 (문자가 남은 식 등). 이런 값은 정답으로 쓸 수 없습니다
    OTHER
  }

  private final EvalEngine engine;

  public SymjaValues(EvalEngine engine) {
    this.engine = engine;
  }

  public Kind kindOf(IExpr value) {
    if (value.isList()) {
      if (value.isMatrix() != null && allNumbersInMatrix(value)) {
        return Kind.MATRIX;
      }
      return allNumbers((IAST) value) ? Kind.SET : Kind.OTHER;
    }
    if (isCondition(value)) {
      return Kind.CONDITION;
    }
    return isNumber(value) ? Kind.NUMBER : Kind.OTHER;
  }

  // 두 값이 같은지. 종류가 다르거나, 정답으로 쓸 수 없는 값(OTHER)이면 false 입니다.
  public boolean same(IExpr a, IExpr b) {
    Kind kind = kindOf(a);
    if (kind == Kind.OTHER || kind != kindOf(b)) {
      return false;
    }
    return switch (kind) {
      case NUMBER -> sameNumber(a, b);
      case SET -> sameSet((IAST) a, (IAST) b);
      case MATRIX -> sameMatrix((IAST) a, (IAST) b);
      case CONDITION -> sameCondition(a, b);
      case OTHER -> false;
    };
  }

  // 정수이면 그 값, 아니면 null. (SHORT 정답 검사용. 2.0 같은 소수는 정수로 보지 않습니다)
  public Integer integerValue(IExpr value) {
    IExpr simplified = engine.evaluate(F.Simplify(value));
    if (!simplified.isInteger()) {
      return null;
    }
    int number = simplified.toIntDefault(Integer.MIN_VALUE);
    return number == Integer.MIN_VALUE ? null : number;
  }

  // 화면용 LaTeX. AI 가 쓴 글자가 아니라 값에서 만들어서, 보이는 것과 값이 어긋날 수 없습니다.
  // "또는 · 그리고" 는 기호(\lor, \land) 대신 한국어로 바꿉니다. (학생이 읽기 쉽게)
  public String latex(IExpr value) {
    StringWriter out = new StringWriter();
    new TeXUtilities(engine, true).toTeX(value, out);
    return out.toString()
        .replace("\n", " ")
        .replace("\\lor", "\\text{ 또는 }")
        .replace("\\land", "\\text{ 그리고 }")
        .replaceAll("\\s+", " ")
        .strip();
  }

  // ---- 수 ----

  private boolean isNumber(IExpr value) {
    return value.isNumericFunction(true) && !value.isList();
  }

  private boolean sameNumber(IExpr a, IExpr b) {
    return engine.evaluate(F.PossibleZeroQ(F.Subtract(a, b))).isTrue();
  }

  // ---- 집합 · 행렬 ----

  private boolean allNumbers(IAST list) {
    for (int i = 1; i <= list.argSize(); i++) {
      if (!isNumber(list.get(i))) {
        return false;
      }
    }
    return true;
  }

  private boolean allNumbersInMatrix(IExpr matrix) {
    IAST rows = (IAST) matrix;
    for (int i = 1; i <= rows.argSize(); i++) {
      if (!rows.get(i).isList() || !allNumbers((IAST) rows.get(i))) {
        return false;
      }
    }
    return true;
  }

  // 순서를 무시한 집합 비교: 겹치는 원소를 뺀 뒤, 크기가 같고 a 의 원소마다 b 에 같은 수가 있으면 같습니다.
  private boolean sameSet(IAST a, IAST b) {
    List<IExpr> left = distinct(a);
    List<IExpr> right = distinct(b);
    if (left.size() != right.size()) {
      return false;
    }
    for (IExpr element : left) {
      if (right.stream().noneMatch(other -> sameNumber(element, other))) {
        return false;
      }
    }
    return true;
  }

  private List<IExpr> distinct(IAST list) {
    List<IExpr> out = new ArrayList<>();
    for (int i = 1; i <= list.argSize(); i++) {
      IExpr element = list.get(i);
      if (out.stream().noneMatch(seen -> sameNumber(seen, element))) {
        out.add(element);
      }
    }
    return out;
  }

  private boolean sameMatrix(IAST a, IAST b) {
    int[] sizeA = a.isMatrix();
    int[] sizeB = b.isMatrix();
    if (sizeA[0] != sizeB[0] || sizeA[1] != sizeB[1]) {
      return false;
    }
    for (int row = 1; row <= sizeA[0]; row++) {
      IAST rowA = (IAST) a.get(row);
      IAST rowB = (IAST) b.get(row);
      for (int col = 1; col <= sizeA[1]; col++) {
        if (!sameNumber(rowA.get(col), rowB.get(col))) {
          return false;
        }
      }
    }
    return true;
  }

  // ---- 조건 (x 에 대한 부등식) ----

  // 조건식인지: 참 · 거짓 그 자체, 또는 "x 와 수" 를 비교하는 식을 그리고 · 또는 · 아니다로 묶은 것
  private boolean isCondition(IExpr value) {
    if (value.isTrue() || value.isFalse()) {
      return true;
    }
    if (value.isAST(S.And) || value.isAST(S.Or)) {
      IAST parts = (IAST) value;
      for (int i = 1; i <= parts.argSize(); i++) {
        if (!isCondition(parts.get(i))) {
          return false;
        }
      }
      return parts.argSize() > 0;
    }
    if (value.isAST(S.Not, 2)) {
      return isCondition(((IAST) value).arg1());
    }
    if (value.isAST(S.Inequality) || isComparison(value)) {
      boolean hasVariable = false;
      for (IExpr part : comparedTerms(value)) {
        if (isConditionVariable(part)) {
          hasVariable = true;
        } else if (!isRealNumber(part)) {
          return false;
        }
      }
      return hasVariable;
    }
    return false;
  }

  // 비교식에서 비교하는 값들만 꺼냅니다.
  //   Less(1, x, 3)                        → 1, x, 3
  //   Inequality(1, Less, x, LessEqual, 3) → 1, x, 3   (짝수 자리의 Less · LessEqual 은 비교 기호라서 뺍니다)
  private List<IExpr> comparedTerms(IExpr comparison) {
    IAST parts = (IAST) comparison;
    int step = comparison.isAST(S.Inequality) ? 2 : 1;
    List<IExpr> terms = new ArrayList<>();
    for (int i = 1; i <= parts.argSize(); i += step) {
      terms.add(parts.get(i));
    }
    return terms;
  }

  // 조건의 변수(x)인지: 수가 아닌 기호. (Symja 는 Less 같은 내장 기호도 "변수" 로 보기 때문에 비교하는 값 자리만 봅니다)
  private boolean isConditionVariable(IExpr part) {
    return part.isSymbol() && !isNumber(part) && !part.isTrue() && !part.isFalse();
  }

  // 조건에 쓰인 변수들을 모읍니다. (보통 x 하나)
  private void collectVariables(IExpr value, List<IExpr> out) {
    if (value.isAST(S.Inequality) || isComparison(value)) {
      for (IExpr part : comparedTerms(value)) {
        if (isConditionVariable(part) && !out.contains(part)) {
          out.add(part);
        }
      }
    } else if (value.isAST()) {
      IAST parts = (IAST) value;
      for (int i = 1; i <= parts.argSize(); i++) {
        collectVariables(parts.get(i), out);
      }
    }
  }

  private boolean isComparison(IExpr value) {
    return value.isAST(S.Less)
        || value.isAST(S.LessEqual)
        || value.isAST(S.Greater)
        || value.isAST(S.GreaterEqual)
        || value.isAST(S.Equal)
        || value.isAST(S.Unequal);
  }

  private boolean isRealNumber(IExpr value) {
    return isNumber(value) && value.isRealResult();
  }

  // 두 조건이 같은지: 두 조건에 나오는 경계값을 모두 모아 작은 순서로 세우고,
  // 경계값 자체 · 이웃한 경계값의 한가운데 · 가장 작은 값보다 1 작은 값 · 가장 큰 값보다 1 큰 값을 x 에 넣어 봅니다.
  // 조건은 경계값 사이에서는 참 · 거짓이 바뀌지 않으므로, 이 점들에서 모두 같으면 두 조건은 같습니다.
  private boolean sameCondition(IExpr a, IExpr b) {
    List<IExpr> bounds = new ArrayList<>();
    collectBounds(a, bounds);
    collectBounds(b, bounds);
    List<IExpr> sorted = sortDistinct(bounds);

    List<IExpr> points = new ArrayList<>();
    if (sorted.isEmpty()) {
      points.add(F.C0);
    } else {
      points.add(engine.evaluate(F.Subtract(sorted.getFirst(), F.C1)));
      for (int i = 0; i < sorted.size(); i++) {
        points.add(sorted.get(i));
        if (i + 1 < sorted.size()) {
          points.add(engine.evaluate(F.Times(F.C1D2, F.Plus(sorted.get(i), sorted.get(i + 1)))));
        }
      }
      points.add(engine.evaluate(F.Plus(sorted.getLast(), F.C1)));
    }

    for (IExpr point : points) {
      Boolean left = truthAt(a, point);
      Boolean right = truthAt(b, point);
      if (left == null || !left.equals(right)) {
        return false;
      }
    }
    return true;
  }

  // 조건의 변수(x)에 값을 넣고 계산합니다. 참 · 거짓으로 정해지지 않으면 null.
  private Boolean truthAt(IExpr condition, IExpr point) {
    List<IExpr> variables = new ArrayList<>();
    collectVariables(condition, variables);
    IExpr substituted = condition;
    for (IExpr variable : variables) {
      // 규칙(x -> 값)으로 바꾸면 그 기호만 바뀌고 Less 같은 비교 기호는 그대로입니다.
      IExpr replaced = substituted.replaceAll(F.Rule(variable, point));
      if (replaced.isPresent()) {
        substituted = replaced;
      }
    }
    IExpr result = engine.evaluate(substituted);
    if (result.isTrue()) {
      return true;
    }
    if (result.isFalse()) {
      return false;
    }
    return null;
  }

  // 조건 안의 모든 수(경계값)를 모읍니다. Sqrt(2) 처럼 식 모양인 수도 수 하나로 봅니다.
  private void collectBounds(IExpr value, List<IExpr> out) {
    if (isRealNumber(value)) {
      out.add(value);
    } else if (value.isAST()) {
      IAST parts = (IAST) value;
      for (int i = 1; i <= parts.argSize(); i++) {
        collectBounds(parts.get(i), out);
      }
    }
  }

  // 작은 순서로 세우고 같은 값은 하나만 남깁니다. (크기 비교는 소수로 바꿔서 합니다)
  private List<IExpr> sortDistinct(List<IExpr> values) {
    List<IExpr> sorted = new ArrayList<>(values);
    sorted.sort(Comparator.comparingDouble(v -> engine.evalDouble(v)));
    List<IExpr> out = new ArrayList<>();
    for (IExpr value : sorted) {
      if (out.isEmpty() || !sameNumber(out.getLast(), value)) {
        out.add(value);
      }
    }
    return out;
  }
}
