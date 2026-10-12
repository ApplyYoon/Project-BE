package com.ithopeschool.projectbe.domain.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 메시지 종류가 이슈 #30 에 적힌 다섯 가지와 같은지 확인합니다.
class MessageKindTest {

  @Test
  @DisplayName("종류는 carrot · nudge · stick · event · recovery 다섯 가지다")
  void fiveKindsFromTheIssue() {
    assertThat(Arrays.stream(MessageKind.values()).map(MessageKind::toValue))
        .containsExactlyInAnyOrder("carrot", "nudge", "stick", "event", "recovery");
  }

  @Test
  @DisplayName("FE 와 주고받는 글자는 모두 소문자다")
  void valuesAreLowerCase() {
    for (MessageKind kind : MessageKind.values()) {
      assertThat(kind.toValue()).isEqualTo(kind.name().toLowerCase()).isLowerCase();
    }
  }
}
