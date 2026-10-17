package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 칸 모양 도구가 OpenAI strict 모드의 규칙(모든 칸 required · 모르는 칸 금지)을 지키는지 봅니다.
class AiSchemaTest {

  @Test
  @DisplayName("object 는 모든 칸을 넣은 순서대로 required 에 넣고, 모르는 칸을 막는다")
  void objectRequiresEveryField() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("step", AiSchema.string());
    properties.put("items", AiSchema.stringList());

    Map<String, Object> schema = AiSchema.object(properties);

    assertThat(schema.get("type")).isEqualTo("object");
    assertThat(schema.get("required")).isEqualTo(List.of("step", "items"));
    assertThat(schema.get("additionalProperties")).isEqualTo(false);
    assertThat(schema.get("properties")).isEqualTo(properties);
  }

  @Test
  @DisplayName("글 한 칸과 글 목록의 모양")
  void stringAndStringList() {
    assertThat(AiSchema.string()).isEqualTo(Map.of("type", "string"));
    assertThat(AiSchema.stringList())
        .isEqualTo(Map.of("type", "array", "items", Map.of("type", "string")));
  }
}
