package com.ithopeschool.projectbe.global.json;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

// JSON 의 숫자를 Integer 로 바꿀 때 "소수점이 없는 정수" 만 받는 변환기입니다.
//
// 왜 필요한가요? JSON 을 자바 값으로 바꿔 주는 라이브러리(Jackson)는 기본으로 소수를 정수 칸에 넣을 때
// 소수점 아래를 말없이 버립니다. 그래서 자신감 3.7 이 3 으로, 하루 180.5분이 180분으로 저장됐습니다. (이슈 #21 에서 발견)
// "정해진 값만 받는다" 는 칸이 틀린 값을 받아 주면 안 되기 때문에, 이 칸들은 정수가 아니면 400 으로 돌려보냅니다.
//
//   180   → 180
//   180.5 → 400       180.0 → 400 (정수로 보내 주세요)       "180" → 400 (따옴표 없는 숫자로 보내 주세요)
//   null  → null (이 변환기를 거치지 않습니다. 꼭 있어야 하는지는 @NotNull 이 검사합니다)
//
// 쓰는 법: 요청 DTO 의 Integer 칸에 @JsonDeserialize(using = WholeNumberDeserializer.class) 를 붙입니다.
// 서버 전체 설정을 바꾸지 않은 이유: 다른 API 의 동작까지 한꺼번에 바뀌기 때문입니다. (팀이 정하면 전체로 옮길 수 있습니다)
public class WholeNumberDeserializer extends ValueDeserializer<Integer> {

  @Override
  public Integer deserialize(JsonParser parser, DeserializationContext context) {
    if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
      // 이 문장은 서버 안에서만 쓰이고, 사용자에게는 공통 문장("보낸 값을 다시 확인해 주세요")이 나갑니다.
      return context.reportInputMismatch(this, "정수(소수점 없는 숫자)만 받습니다");
    }
    // Integer 에 안 들어가는 아주 큰 수는 여기서 에러가 나고, 그것도 400 으로 나갑니다.
    return parser.getIntValue();
  }
}
