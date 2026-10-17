package com.ithopeschool.projectbe.domain.bias;

import com.ithopeschool.projectbe.domain.care.MissReason;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 학생이 생각 습관 테스트 마지막에 고르는 "내가 생각하는 나의 약점" 칩입니다. (FE src/lib/thinkingTest.ts 의 weaknessOptions)
// 이름과 글자는 FE 와 같습니다. 한쪽을 고치면 다른 쪽도 같이 고쳐야 합니다.
//
// 학생이 스스로 고른 것이라 "생각" 쪽 자료입니다. 통화에서 실제로 나온 이유(현실)와 견주어
// "내가 생각한 약점과 실제 원인의 차이" 를 보여 주는 데 씁니다.
// "기타" 에 직접 쓴 글은 서버로 받지 않습니다. 학생이 쓴 글이 AI 의 지침에 들어가면 엉뚱한 지시가 섞일 수 있어서입니다.
//
// 약점마다 "실제로 못 채운 이유"(MissReason) 중 짝이 되는 것을 적어 두었습니다. 생각과 현실을 견줄 때 씁니다. (ThinkingGap)
//   게임 · 폰·SNS · 유튜브·영상  →  폰·SNS        잠·늦잠 · 체력·피곤  →  피곤
//   게으름 · 미루기              →  하기 싫음      친구·약속            →  약속·행사
//   계획 세우기                  →  계획 과다      집중력 · 기분·스트레스 →  짝 없음 (통화의 이유 버튼에 없어서 견줄 수 없음)
@Getter
@RequiredArgsConstructor
public enum Weakness {
  GAME("게임", MissReason.PHONE),
  PHONE("폰·SNS", MissReason.PHONE),
  VIDEO("유튜브·영상", MissReason.PHONE),
  SLEEP("잠·늦잠", MissReason.TIRED),
  LAZY("게으름", MissReason.UNWILLING),
  DELAY("미루기", MissReason.UNWILLING),
  FOCUS("집중력", null),
  FRIENDS("친구·약속", MissReason.EVENT),
  TIRED("체력·피곤", MissReason.TIRED),
  MOOD("기분·스트레스", null),
  PLANNING("계획 세우기", MissReason.TOO_MUCH);

  // 칩에 적힌 글자
  private final String label;
  // 짝이 되는 "못 채운 이유". 짝이 없으면 null
  private final MissReason missReason;
}
