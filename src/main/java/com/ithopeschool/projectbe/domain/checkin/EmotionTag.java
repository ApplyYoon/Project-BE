package com.ithopeschool.projectbe.domain.checkin;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 하루 첫 태그에서 고르는 감정입니다. 여러 개를 고를 수 있고, 안 골라도 됩니다. (이슈 #220, 기획서 4.2 「홈」)
//
// 쓰임
//   - 막막함 · 불안함 → 그날 카드에 5초 카운트다운 · 쪼개기를 켭니다. (FE #399)
//   - 피곤함 → 유연 카드를 줄이자고 한 번 제안합니다.
//   - 감정적 추론 반박의 재료: "컨디션 2라고 한 날도 평소의 90%는 했어"
//
// FE 와 주고받을 때도 이름 그대로(STUCK) 씁니다. DB 에도 이름 그대로 저장합니다.
// 고른 감정이 하나도 없는 것은 "모름" 이지 "괜찮음" 이 아닙니다.
@Getter
@RequiredArgsConstructor
public enum EmotionTag {
  STUCK("막막함"),
  ANXIOUS("불안함"),
  TIRED("피곤함"),
  ANNOYED("짜증남"),
  CALM("평온함"),
  MOTIVATED("의욕 있음");

  // 화면에 보이는 이름
  private final String label;

  // 보낸 글자를 감정으로 바꿉니다. 6가지 중 하나가 아니면 null 입니다.
  public static EmotionTag fromName(String name) {
    for (EmotionTag tag : values()) {
      if (tag.name().equals(name)) {
        return tag;
      }
    }
    return null;
  }
}
