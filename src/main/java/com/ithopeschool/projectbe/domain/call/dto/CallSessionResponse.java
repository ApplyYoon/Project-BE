package com.ithopeschool.projectbe.domain.call.dto;

// AI 통화를 시작할 때의 응답 모양입니다. (FE src/lib/realtimeCall.ts)
//   { "value": "ek_…", "expiresAt": 1791700000000, "maxCallSeconds": 300, "callId": "12" }
//
// value 는 "임시 키" 입니다. 진짜 GPT 키(sk-…)가 아니라, 이번 통화에만 잠깐 쓰고 버리는 출입증입니다.
// 브라우저는 이 값으로 OpenAI 에 직접 연결합니다. 진짜 키는 서버 밖으로 나가지 않습니다.
public record CallSessionResponse(
    // 임시 키. 이 값으로 통화를 시작할 수 있는 시간은 짧습니다. (expiresAt 까지)
    String value,
    // 임시 키가 끝나는 순간 (밀리초 숫자). 이미 시작한 통화는 이 뒤에도 이어집니다.
    long expiresAt,
    // 통화 한 번의 최대 길이(초). FE 가 이 시간이 되면 통화를 끊습니다. (음성은 길수록 돈이 많이 듭니다)
    int maxCallSeconds,
    // 이번 통화의 기록 번호. 통화가 끝나면 이 번호로 마무리합니다. FE 의 다른 id 처럼 글자(string)로 보냅니다.
    String callId) {}
