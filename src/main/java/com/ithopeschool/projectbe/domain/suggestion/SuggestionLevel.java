package com.ithopeschool.projectbe.domain.suggestion;

// 개선안(전략)의 강도입니다. (이슈 #50, 기획서 v2 4장 「개선안 만들기」)
// FE 의 SuggestionLevel('easy' | 'normal' | 'hard') 과 같은 순서 · 같은 뜻입니다.
//
//   쉬움(EASY)   : 버튼 하나 · 1~2분이면 되는 것. 부담이 가장 적습니다.
//   보통(NORMAL) : 생활 순서를 조금 바꾸는 것.
//   강함(HARD)   : 환경을 아예 바꾸거나 다른 사람과 약속하는 것. 효과는 크지만 지키기 어렵습니다.
//
// FE 와 주고받을 때는 소문자 글자로 바꿉니다.  EASY.toValue() -> "easy"
public enum SuggestionLevel {
  EASY("easy"),
  NORMAL("normal"),
  HARD("hard");

  // FE 와 주고받는 글자
  private final String value;

  SuggestionLevel(String value) {
    this.value = value;
  }

  // FE 와 주고받는 글자로 바꿉니다.
  public String toValue() {
    return value;
  }
}
