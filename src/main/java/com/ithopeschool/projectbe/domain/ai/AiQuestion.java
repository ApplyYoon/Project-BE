package com.ithopeschool.projectbe.domain.ai;

import java.util.Map;

// 작은 AI 기능 하나가 GPT 에게 묻는 내용입니다. AiClient.ask 에 넘깁니다. (이슈 #237)
// 기능마다 다른 것은 이 네 가지뿐이고, 부르는 방법(호출 기록·기다리는 시간·실패 처리)은 AiClient 가 똑같이 합니다.
//
//   name         : 답 모양의 이름. 영어 소문자와 _ 만 씁니다. (예: "checklist")
//   instructions : 지침. GPT 가 지켜야 하는 규칙입니다. 누가 부르든 같습니다.
//   input        : 이번에 GPT 에게 주는 글 (카드 제목 등)
//                  ⚠️ 이름·학교 같은 개인정보는 넣지 않습니다. 기능에 꼭 필요한 글만 넣습니다.
//   schema       : 답 JSON 의 칸 모양 (AiSchema 로 만듭니다). GPT 는 이 모양으로만 답합니다.
public record AiQuestion(
    String name, String instructions, String input, Map<String, Object> schema) {}
