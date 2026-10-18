package com.ithopeschool.projectbe.global.answer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// "답이 성의 없어 보이는지" 를 보는 도구입니다. (2026-10-18)
// 사용자: "응답 보고 다 똑같은 거 하면 막 의심하고 … BE 코드로도 될 수도 있고"
// AI 가 아니라 코드 규칙으로 봅니다. 같은 답을 넣으면 언제나 같은 판정이 나오고, 왜 그렇게 봤는지 설명할 수 있습니다.
//
// 어느 응답에든 쓸 수 있게 숫자 목록(1 ~ 7 점, 보기 번호 등)만 받습니다.
// 몇 % 부터 의심할지는 쓰는 쪽이 정합니다. (예: 성향 검사는 PsychAnswerCheck)
// ⚠️ 답 목록은 학생이 **화면에서 본 순서대로** 넣어야 합니다. 되풀이(1-2-1-2)는 순서를 봅니다.
//
//   topShare([4, 4, 4, 5])               -> 0.75  (가장 많은 4 가 4개 중 3개)
//   isAllSame([3, 3, 3])                 -> true
//   repeatShare([1, 2, 1, 2, 1, 2], 2)   -> 1.0   (두 칸 앞과 같은 답의 비율)
//   isRepeating([1, 2, 1, 2, 1, 2], 4, 0.9) -> true
//
// ⚠️ 의심은 "한 번 더 물어볼 이유" 일 뿐 벌이 아닙니다. 판정으로 학생을 따지거나 혼내지 않습니다.
// 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
public final class AnswerPattern {

  // 되풀이로 보려면 묶음이 몇 번은 돌아야 하는지. 두 번(1-2-1-2)은 우연히도 나와서 세 번부터 봅니다.
  static final int MIN_ROUNDS = 3;

  private AnswerPattern() {}

  // 가장 많이 나온 답이 전체에서 차지하는 비율 (0 ~ 1). 답이 없으면 0
  public static double topShare(List<Integer> answers) {
    if (answers.isEmpty()) {
      return 0;
    }
    Map<Integer, Integer> counts = new HashMap<>();
    int top = 0;
    for (Integer answer : answers) {
      int count = counts.merge(answer, 1, Integer::sum);
      top = Math.max(top, count);
    }
    return (double) top / answers.size();
  }

  // 답이 모두 같은지. 답이 2개보다 적으면 판단할 수 없어서 false
  public static boolean isAllSame(List<Integer> answers) {
    return answers.size() >= 2 && topShare(answers) == 1.0;
  }

  // gap 칸 앞의 답과 같은 답의 비율 (0 ~ 1). 비교할 쌍이 없으면 0
  //   1-2-1-2 처럼 번갈아 누르면 gap 2 에서 1.0, 1-4-7-1-4-7 이면 gap 3 에서 1.0 입니다.
  public static double repeatShare(List<Integer> answers, int gap) {
    int pairs = answers.size() - gap;
    if (gap < 1 || pairs < 1) {
      return 0;
    }
    int same = 0;
    for (int i = gap; i < answers.size(); i++) {
      if (Objects.equals(answers.get(i), answers.get(i - gap))) {
        same++;
      }
    }
    return (double) same / pairs;
  }

  // 짧은 묶음(2 ~ maxCycle 칸)을 되풀이해 누른 것 같은지. (1-2-1-2 · 1-4-7-1-4-7 · 1-1-7-7-1-1-7-7)
  //   - minShare: 몇 % 이상 맞아야 되풀이로 볼지. 몇 개는 실수로 다르게 눌렀을 수 있어서 1.0 이 아니어도 됩니다.
  //   - 바로 앞과 같은 답이 대부분이면(4-4-4…) 되풀이가 아니라 "다 같음" 이라 여기서는 false 입니다. (topShare 로 봅니다)
  //   - 묶음이 MIN_ROUNDS 번은 돌 만큼 답이 있어야 봅니다.
  public static boolean isRepeating(List<Integer> answers, int maxCycle, double minShare) {
    if (repeatShare(answers, 1) >= minShare) {
      return false;
    }
    for (int gap = 2; gap <= maxCycle; gap++) {
      if (answers.size() >= gap * MIN_ROUNDS && repeatShare(answers, gap) >= minShare) {
        return true;
      }
    }
    return false;
  }
}
