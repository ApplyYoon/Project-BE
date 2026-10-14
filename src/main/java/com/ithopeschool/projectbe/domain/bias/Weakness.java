package com.ithopeschool.projectbe.domain.bias;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 학생이 생각 습관 테스트 마지막에 고르는 "내가 생각하는 나의 약점" 칩입니다. (FE src/lib/thinkingTest.ts 의 weaknessOptions)
// 이름과 글자는 FE 와 같습니다. 한쪽을 고치면 다른 쪽도 같이 고쳐야 합니다.
//
// 학생이 스스로 고른 것이라 "생각" 쪽 자료입니다. 통화에서 실제로 나온 이유(현실)와 견주어
// "내가 생각한 약점과 실제 원인의 차이" 를 보여 주는 데 씁니다.
// "기타" 에 직접 쓴 글은 서버로 받지 않습니다. 학생이 쓴 글이 AI 의 지침에 들어가면 엉뚱한 지시가 섞일 수 있어서입니다.
@Getter
@RequiredArgsConstructor
public enum Weakness {
  GAME("게임"),
  PHONE("폰·SNS"),
  VIDEO("유튜브·영상"),
  SLEEP("잠·늦잠"),
  LAZY("게으름"),
  DELAY("미루기"),
  FOCUS("집중력"),
  FRIENDS("친구·약속"),
  TIRED("체력·피곤"),
  MOOD("기분·스트레스"),
  PLANNING("계획 세우기");

  // 칩에 적힌 글자
  private final String label;
}
