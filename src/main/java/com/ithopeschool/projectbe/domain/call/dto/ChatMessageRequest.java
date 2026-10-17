package com.ithopeschool.projectbe.domain.call.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

// 상담 채팅에서 학생이 말을 보낼 때의 모양입니다. (이슈 #238)
// 지금까지 주고받은 말을 모두 보내고, 맨 끝이 방금 학생이 쓴 말입니다. (줄 모양은 통화 자막과 같습니다)
//   { "lines": [ { "speaker": "ai", "text": "어제 순공 1시간 40분이었어. 어땠어?" },
//                { "speaker": "me", "text": "폰 보다가 늦게 시작했어" } ] }
//
// ⚠️ 서버는 이 대화를 저장하지 않습니다. AI 의 다음 말을 받는 데만 쓰고, 로그에도 찍지 않습니다. (CLAUDE.md 4장)
//    그래서 FE 가 대화를 들고 있다가 매번 같이 보냅니다. 끝낼 때도 같은 줄들을 /chats/{id}/finish 로 보냅니다.
public record ChatMessageRequest(
    @NotNull(message = "대화(lines)를 보내 주세요")
        @Size(min = 1, max = 60, message = "대화(lines)는 1 ~ 60줄까지 보낼 수 있어요")
        @Valid
        List<CallFinishRequest.Line> lines) {}
