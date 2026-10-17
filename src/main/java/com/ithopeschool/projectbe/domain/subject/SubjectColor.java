package com.ithopeschool.projectbe.domain.subject;

import java.util.Locale;

// 과목 태그에 칠할 수 있는 색. 이 다섯 가지 말고는 DB 에 들어갈 수 없습니다.
// FE 의 SubjectColor('red' | 'orange' | 'green' | 'blue' | 'purple') 와 같은 다섯 개입니다.
//
// DB 에는 이름 그대로(BLUE) 저장하고, FE 와 주고받을 때는 소문자(blue)로 바꿉니다. (이슈 #10)
//   BLUE.toValue()           -> "blue"
//   SubjectColor.fromValue("blue") -> BLUE
// 색을 더하려면 아래에 한 줄만 적으면 됩니다. 단, DB 가 받아 주는 색도 같이 늘어나므로 팀과 먼저 정합니다.
public enum SubjectColor {
  RED,
  ORANGE,
  GREEN,
  BLUE,
  PURPLE;

  // FE 와 주고받는 글자로 바꿉니다. (항상 소문자)
  public String toValue() {
    // Locale.ROOT: 컴퓨터의 언어 설정에 따라 소문자가 달라지지 않게 합니다.
    return name().toLowerCase(Locale.ROOT);
  }

  // FE 가 보낸 글자를 색으로 바꿉니다. 다섯 가지 중 하나가 아니면 null 입니다.
  // 대문자(BLUE)나 앞뒤 공백이 붙은 글자도 받지 않습니다. FE 와 약속한 모양은 소문자 그대로입니다.
  public static SubjectColor fromValue(String value) {
    for (SubjectColor color : values()) {
      if (color.toValue().equals(value)) {
        return color;
      }
    }
    return null;
  }
}
