package com.ithopeschool.projectbe.domain.psychtest.dto;

import com.ithopeschool.projectbe.domain.psychtest.PsychItem;
import com.ithopeschool.projectbe.domain.psychtest.PsychScale;
import com.ithopeschool.projectbe.domain.psychtest.PsychScore;
import com.ithopeschool.projectbe.domain.psychtest.PsychTest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

// 심리검사 결과를 FE 에 알려 주는 모양입니다. (이슈 #219)
//   { "id": "3", "takenAt": "2026-10-17T09:00:00Z",
//     "answers": { "S1": 6, "S2": 5, … },          ← 답한 문장만 (지금 검사 35개 · 옛 검사 14개)
//     "scores": { "startResistance": 5.5, "distraction": 2.5, … },
//     "levels": { "startResistance": "high", "distraction": "low", … },
//     "reliable": true }
// - scores · levels 의 이름과 높낮이 글자(high · mid · low)는 FE src/lib/psychTest.ts 와 같습니다. 척도 순서는 기획서 6.1
// 표 순서
// - reliable: 답이 믿을 만한지. 거의 다 같은 답이거나 1-2-1-2 처럼 되풀이했으면 false 입니다. (PsychAnswerCheck, 2026-10-18)
//   false 면 FE 결과 화면이 "다시 해 볼래?" 를 묻습니다. 점수 · 높낮이는 그대로 계산해 줍니다.
// - 누구의 검사인지(userId)는 담지 않습니다.
public record PsychTestResponse(
    String id,
    Instant takenAt,
    Map<String, Integer> answers,
    Map<String, Double> scores,
    Map<String, String> levels,
    boolean reliable) {

  public static PsychTestResponse from(PsychTest test) {
    // 답한 문장만 담습니다. (옛 14문항 검사는 14개, 지금 검사는 35개)
    Map<String, Integer> answers = new LinkedHashMap<>();
    for (PsychItem item : PsychItem.values()) {
      if (test.getAnswers().containsKey(item)) {
        answers.put(item.name(), test.getAnswers().get(item));
      }
    }
    Map<String, Double> scores = new LinkedHashMap<>();
    Map<String, String> levels = new LinkedHashMap<>();
    for (Map.Entry<PsychScale, Double> entry : PsychScore.scoresOf(test.getAnswers()).entrySet()) {
      scores.put(entry.getKey().toValue(), entry.getValue());
      levels.put(entry.getKey().toValue(), PsychScore.levelOf(entry.getValue()).toValue());
    }
    return new PsychTestResponse(
        String.valueOf(test.getId()),
        test.getTakenAt(),
        answers,
        scores,
        levels,
        test.isReliable());
  }
}
