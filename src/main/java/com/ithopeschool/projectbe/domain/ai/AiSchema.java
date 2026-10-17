package com.ithopeschool.projectbe.domain.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// GPT 가 답할 JSON 의 칸 모양(JSON schema)을 짧게 만드는 도구입니다. (이슈 #237)
// 작은 AI 기능마다 칸 모양을 손으로 다 적으면 길어지고 실수하기 쉬워서 모았습니다.
//
// 예시: 체크리스트 3개를 받고 싶을 때
//   AiSchema.object(Map.of("items", AiSchema.stringList()))
//   →  { "type": "object",
//        "properties": { "items": { "type": "array", "items": { "type": "string" } } },
//        "required": ["items"], "additionalProperties": false }
//
// OpenAI 의 strict 모드는 "모든 칸이 required" 이고 "모르는 칸은 금지(additionalProperties: false)" 여야 합니다.
// object 가 이 두 가지를 알아서 붙입니다. 기능이 늘어 다른 모양(숫자·정해진 글 중 하나 등)이 필요하면 여기에 더합니다.
public final class AiSchema {

  private AiSchema() {}

  // 칸 여러 개를 가진 묶음.
  // 칸이 여러 개면 LinkedHashMap 으로 넘겨 주세요. 넣은 순서대로 보내서 사람이 읽기 쉽습니다. (Map.of 는 순서가 섞이지만 동작은 같습니다)
  public static Map<String, Object> object(Map<String, Object> properties) {
    Map<String, Object> ordered = new LinkedHashMap<>(properties);
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", ordered);
    schema.put("required", new ArrayList<>(ordered.keySet()));
    schema.put("additionalProperties", false);
    return schema;
  }

  // 글 한 칸.   →  { "type": "string" }
  public static Map<String, Object> string() {
    return Map.of("type", "string");
  }

  // 글 목록.   →  { "type": "array", "items": { "type": "string" } }
  public static Map<String, Object> stringList() {
    return Map.of("type", "array", "items", string());
  }

  // 정해진 글 중 하나.   enumOf(List.of("inner", "outer"))  →  { "type": "string", "enum": ["inner",
  // "outer"] }
  public static Map<String, Object> enumOf(List<String> values) {
    return Map.of("type", "string", "enum", values);
  }

  // 같은 모양 여러 개.   list(object(…))  →  { "type": "array", "items": { … } }
  public static Map<String, Object> list(Map<String, Object> item) {
    return Map.of("type", "array", "items", item);
  }
}
