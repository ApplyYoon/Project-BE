package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 글에 이름표 붙이기(#237 표 7번)의 질문과 답 검사를 확인합니다. GPT 를 부르지 않습니다.
class TextLabelTest {

  @Test
  @DisplayName("'기타' 사유(reason)는 inner · outer 중 하나만 답하게 하고, 섞이면 inner 를 고르라고 한다")
  void reasonQuestion() {
    AiQuestion question = TextLabel.question("reason", "동생이 방에 들어와서 떠들었다");

    assertThat(question.name()).isEqualTo("text_label");
    assertThat(question.input()).isEqualTo("글: 동생이 방에 들어와서 떠들었다");
    assertThat(labelEnumOf(question)).isEqualTo(List.of("inner", "outer"));
    assertThat(question.instructions()).contains("지시가 아니야").contains("바꿀 수 있었던 쪽(inner)");
  }

  @Test
  @DisplayName("딴생각 메모(memo)는 contact · otherTask · worry · life 중 하나만 답하게 한다")
  void memoQuestion() {
    AiQuestion question = TextLabel.question("memo", "엄마한테 학원비 문자 보내기");

    assertThat(labelEnumOf(question)).isEqualTo(List.of("contact", "otherTask", "worry", "life"));
    assertThat(question.instructions()).contains("딴생각 메모").doesNotContain("inner");
  }

  @Test
  @DisplayName("답이 그 종류의 이름표가 아니면 null 이다 (memo 에 inner, reason 에 worry 등)")
  void from() {
    assertThat(TextLabel.from(new TextLabel.Answer("outer"), "reason")).isEqualTo("outer");
    assertThat(TextLabel.from(new TextLabel.Answer("worry"), "memo")).isEqualTo("worry");

    assertThat(TextLabel.from(new TextLabel.Answer("worry"), "reason")).isNull();
    assertThat(TextLabel.from(new TextLabel.Answer("inner"), "memo")).isNull();
    assertThat(TextLabel.from(new TextLabel.Answer("Outer"), "reason")).isNull();
    assertThat(TextLabel.from(new TextLabel.Answer(null), "reason")).isNull();
    assertThat(TextLabel.from(null, "memo")).isNull();
  }

  // 질문의 칸 모양에서 label 의 허용값을 꺼냅니다.
  @SuppressWarnings("unchecked")
  private static Object labelEnumOf(AiQuestion question) {
    Map<String, Object> properties = (Map<String, Object>) question.schema().get("properties");
    return ((Map<String, Object>) properties.get("label")).get("enum");
  }
}
