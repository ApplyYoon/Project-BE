package com.ithopeschool.projectbe.domain.insight;

// 인사이트 카드에 학생이 누른 반응 버튼입니다. (FE#84, 이슈 #43)
// FE 의 InsightResponse('surprised' | 'alreadyKnew' | 'wrong') 와 같은 세 가지입니다.
//
// 다음 인사이트를 고를 때 이렇게 반영합니다. (InsightPicker, 담당 학생이 정함)
//   SURPRISED     몰랐는데 맞아  →  같은 종류의 인사이트 총점 +10
//   ALREADY_KNEW  이미 알던 거야 →  그대로
//   WRONG         틀렸어        →  같은 종류의 인사이트는 다시 보여 주지 않음
public enum InsightResponse {
  SURPRISED("surprised"),
  ALREADY_KNEW("alreadyKnew"),
  WRONG("wrong");

  // FE 와 주고받는 글자. alreadyKnew 처럼 소문자로만 바꿔서는 안 나오는 글자가 있어서 하나씩 적어 둡니다.
  private final String value;

  InsightResponse(String value) {
    this.value = value;
  }

  public String toValue() {
    return value;
  }

  // FE 가 보낸 글자를 반응으로 바꿉니다. 세 가지 중 하나가 아니면 null 입니다.
  public static InsightResponse fromValue(String value) {
    for (InsightResponse response : values()) {
      if (response.value.equals(value)) {
        return response;
      }
    }
    return null;
  }
}
