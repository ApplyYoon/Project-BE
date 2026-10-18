package com.ithopeschool.projectbe.domain.psychtest;

import com.ithopeschool.projectbe.global.answer.AnswerPattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 성향 검사의 답이 믿을 만한지 봅니다. (2026-10-18, AnswerPattern 을 성향 검사에 쓴 것)
// FE 의 isPsychTestReliable (src/lib/psychTest.ts) 과 같은 규칙입니다. 한쪽을 고치면 다른 쪽도 고쳐 주세요.
// 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
//
// ■ 믿기 어려움 (둘 중 하나라도)
//   1. 가장 많은 답이 90% 이상     35문항이면 32개 이상, 옛 14문항이면 13개 이상이 같은 숫자
//   2. 2 ~ 4칸 묶음을 90% 이상 되풀이   1-2-1-2 · 1-4-7-1-4-7 처럼
//
// ■ 왜 이 숫자인가
//   - 90%: 문장 35개는 척도 7개를 돌아가며 묻습니다. 진짜로 읽고 답했다면 척도마다 생각이 달라서 한 숫자에 이만큼 몰리기 어렵습니다.
//          85%(30개)쯤은 "대부분 보통(4)" 인 학생도 나올 수 있어서, 너무 쉽게 의심하지 않게 90% 로 잡았습니다.
//   - 묶음 4칸까지만: 화면은 S1 A1 P1 R1 F1 E1 C1 → S2 … 순서라, 척도마다 생각이 뚜렷한 학생은 **진짜로 7칸마다** 같은 답이 나옵니다.
//          그래서 7칸 되풀이는 의심하지 않습니다.
//
// ⚠️ 믿기 어려워도 저장은 그대로 합니다. 결과 화면이 "다시 해 볼래?" 를 묻고, AI 는 성향을 단정하지 않습니다.
public final class PsychAnswerCheck {

  // 가장 많은 답이 이 비율 이상이면 믿기 어렵다고 봅니다.
  static final double SAME_ANSWER_LIMIT = 0.9;
  // 되풀이로 볼 묶음의 최대 칸 수
  static final int MAX_CYCLE = 4;
  // 묶음을 이 비율 이상 되풀이하면 믿기 어렵다고 봅니다.
  static final double REPEAT_LIMIT = 0.9;

  private PsychAnswerCheck() {}

  // 답이 믿을 만하면 true. 답이 없으면 판단할 수 없어서 true 입니다.
  public static boolean isReliable(Map<PsychItem, Integer> answers) {
    List<Integer> shown = inShownOrder(answers);
    if (shown.isEmpty()) {
      return true;
    }
    return AnswerPattern.topShare(shown) < SAME_ANSWER_LIMIT
        && !AnswerPattern.isRepeating(shown, MAX_CYCLE, REPEAT_LIMIT);
  }

  // 학생이 화면에서 본 순서대로 답을 늘어놓습니다. (FE psychTestItems 의 순서)
  //   S1 A1 P1 R1 F1 E1 C1 → S2 A2 … → C5   (번호 순서, 같은 번호 안에서는 척도 순서)
  //   옛 14문항 검사는 S1 A1 … C1 → S2 … C2 입니다. 답이 없는 문장은 건너뜁니다.
  static List<Integer> inShownOrder(Map<PsychItem, Integer> answers) {
    List<Integer> shown = new ArrayList<>();
    for (int number = 1; number <= 5; number++) {
      for (PsychScale scale : PsychScale.values()) {
        for (PsychItem item : PsychItem.values()) {
          if (item.scale() == scale && item.number() == number && answers.containsKey(item)) {
            shown.add(answers.get(item));
          }
        }
      }
    }
    return shown;
  }
}
