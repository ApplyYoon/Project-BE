package com.ithopeschool.projectbe.domain.care;

// AI 가 오늘 어떻게 대응할지의 종류입니다. (이슈 #55, 기획서 v2 4장 「분기: 당근 / 채찍 / 목표 조정」)
// FE 의 CareAction 과 같은 7개입니다. (FE 는 'recovery-first' 처럼 소문자와 '-' 로 씁니다)
public enum CareAction {
  // 회복 모드 우선. 채찍 금지
  RECOVERY_FIRST,
  // 당근 + 목표 10% 상향
  CARROT_RAISE,
  // 당근(노력 인정) + 전략 교체
  CARROT_CHANGE_STRATEGY,
  // 당근만. 목표·전략은 그대로 (효과가 애매해서 1주 더 지켜볼 때)
  CARROT_KEEP,
  // 같은 전략 유지 + 난이도 한 단계 낮춤
  KEEP_EASIER,
  // 목표 하향 (회복 모드)
  GOAL_DOWN,
  // 채찍 (한도 안에서 한 단계씩. 한도는 관리 강도 계산(이슈 #62)이 지킵니다)
  WHIP
}
