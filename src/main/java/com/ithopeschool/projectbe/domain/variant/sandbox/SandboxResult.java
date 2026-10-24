package com.ithopeschool.projectbe.domain.variant.sandbox;

import java.util.List;

// 샌드박스가 돌려준 계산 결과입니다. (docs/VARIANT-SPEC.md 6-3)
//
//   ok              : 계산을 끝까지 했는지. false 면 error 에 이유가 있습니다 (시간 초과, 문법 오류 등)
//   computed        : 계산한 정답 값 (Symja 글자)
//   computedLatex   : 계산한 정답의 화면용 LaTeX
//   choicesLatex    : 선지마다 화면용 LaTeX (MC5)
//   integerValue    : 계산값이 정수면 그 값 (SHORT 검사용)
//   matchedIndices  : 계산값과 같은 선지 번호들 (MC5). 정답 선지 하나만 들어 있어야 통과입니다
//   choicesDistinct : 선지끼리 모두 다른지 (MC5)
//   error           : 실패 이유 (ok 가 false 일 때)
public record SandboxResult(
    boolean ok,
    String computed,
    String computedLatex,
    List<String> choicesLatex,
    Integer integerValue,
    List<Integer> matchedIndices,
    boolean choicesDistinct,
    String error) {

  public static SandboxResult failed(String error) {
    return new SandboxResult(false, null, null, List.of(), null, List.of(), false, error);
  }
}
