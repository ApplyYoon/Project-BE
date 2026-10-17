package com.ithopeschool.projectbe.domain.exam;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

// 시험 종류가 FE 와 주고받는 글자(소문자)와 서로 잘 바뀌는지 확인합니다.
class ExamTypeTest {

  @ParameterizedTest(name = "{0} ↔ {1}")
  @CsvSource({"MOCK, mock", "SCHOOL, school", "QUIZ, quiz", "ETC, etc"})
  @DisplayName("네 가지 종류가 FE 의 소문자 글자와 서로 바뀐다")
  void convertsBothWays(ExamType type, String value) {
    assertThat(type.toValue()).isEqualTo(value);
    assertThat(ExamType.fromValue(value)).isEqualTo(type);
  }

  @Test
  @DisplayName("종류는 FE 의 ExamType 과 같은 네 개뿐이다")
  void onlyFourTypes() {
    assertThat(ExamType.values())
        .containsExactly(ExamType.MOCK, ExamType.SCHOOL, ExamType.QUIZ, ExamType.ETC);
  }

  @ParameterizedTest(name = "\"{0}\" 은 null")
  @ValueSource(strings = {"MOCK", "Mock", "midterm", "", " mock", "모의고사"})
  @NullSource
  @DisplayName("네 가지가 아닌 글자(대문자·모르는 글자·빈 글자·null)는 null 이다")
  void unknownValueIsNull(String value) {
    assertThat(ExamType.fromValue(value)).isNull();
  }
}
