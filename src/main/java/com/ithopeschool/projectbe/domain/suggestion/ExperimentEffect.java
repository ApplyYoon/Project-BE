package com.ithopeschool.projectbe.domain.suggestion;

// 개선안(실험)이 효과가 있었는지의 판정입니다. (이슈 #54 가 계산하고, 이슈 #55 의 CareDecision 이 받아서 씁니다)
// FE 의 ExperimentResult('effective' | 'noEffect' | 'unclear') 와 같은 세 개입니다.
//
// 이행 판정(SuggestionGrade)과는 다른 것입니다. "잘 지켰는데 효과가 없다" 가 있을 수 있습니다.
// 그럴 때 문제는 사람이 아니라 전략입니다.
public enum ExperimentEffect {
  // 효과 있음: 실험 전 2주보다 나아짐
  EFFECTIVE,
  // 효과 없음: 나아지지 않음
  NO_EFFECT,
  // 애매함: 기록이 모자라거나 신호가 엇갈려서 단정할 수 없음 (기획서: 애매하면 1주 연장)
  UNCLEAR
}
