package com.ithopeschool.projectbe.domain.suggestion;

// 개선안(실험)을 얼마나 지켰는지의 판정입니다. (이슈 #53)
// FE 의 SuggestionGrade('good' | 'partial' | 'poor') 와 같은 세 개입니다.
public enum SuggestionGrade {
  // 잘 시행: 이행률 70% 이상
  GOOD,
  // 부분 시행: 이행률 40% 이상 70% 미만
  PARTIAL,
  // 못 시행: 이행률 40% 미만
  POOR
}
