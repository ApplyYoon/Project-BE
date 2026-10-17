package com.ithopeschool.projectbe.domain.goal;

import java.util.Locale;

// 목표의 유형. (최종 기획서 「4.7 목표와 숲」, 이슈 #221)
// FE 의 GoalKind('score' | 'completion') 와 같은 두 개입니다.
//   SCORE: 점수형 — "11월 학평 수학 3등급" 처럼 점수 · 등급을 올리는 목표. 지금 점수(currentScore)를 적습니다.
//   COMPLETION: 완주형 — "영단어장 1회독" 처럼 정해진 분량을 끝내는 목표. 전체 분량(totalAmount)과 단위를 적습니다.
//
// DB 에는 이름 그대로(SCORE) 저장하고, FE 와 주고받을 때는 소문자(score)로 바꿉니다. (CardPriority 와 같은 방식)
public enum GoalKind {
  SCORE,
  COMPLETION;

  // FE 와 주고받는 글자로 바꿉니다. (항상 소문자)
  public String toValue() {
    // Locale.ROOT: 컴퓨터의 언어 설정에 따라 소문자가 달라지지 않게 합니다.
    return name().toLowerCase(Locale.ROOT);
  }

  // FE 가 보낸 글자를 유형으로 바꿉니다. 두 가지 중 하나가 아니면 null 입니다.
  public static GoalKind fromValue(String value) {
    for (GoalKind kind : values()) {
      if (kind.toValue().equals(value)) {
        return kind;
      }
    }
    return null;
  }
}
