package com.ithopeschool.projectbe.domain.subject;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

// DB 에 저장하는 색(BLUE)과 FE 와 주고받는 색(blue)을 서로 바꾸는 규칙을 확인합니다.
class SubjectColorTest {

  @Test
  @DisplayName("FE 와 주고받는 글자는 소문자 다섯 개다")
  void fiveLowercaseValues() {
    assertThat(SubjectColor.values())
        .extracting(SubjectColor::toValue)
        .containsExactly("red", "orange", "green", "blue", "purple");
  }

  @ParameterizedTest
  @EnumSource(SubjectColor.class)
  @DisplayName("색 → 글자 → 색으로 바꿔도 처음과 같다")
  void roundTrip(SubjectColor color) {
    assertThat(SubjectColor.fromValue(color.toValue())).isEqualTo(color);
  }

  @ParameterizedTest
  @ValueSource(strings = {"pink", "yellow", "BLUE", "Blue", " blue", "blue ", "bl ue", "RED"})
  @DisplayName("다섯 가지가 아닌 글자는 null (대문자·공백이 붙은 글자도 받지 않는다)")
  void unknownValueIsNull(String value) {
    assertThat(SubjectColor.fromValue(value)).isNull();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @DisplayName("null 이나 빈 글자도 null")
  void emptyIsNull(String value) {
    assertThat(SubjectColor.fromValue(value)).isNull();
  }
}
