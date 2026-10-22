package com.ithopeschool.projectbe.domain.call.dto;

import com.ithopeschool.projectbe.domain.ai.dto.CardPhotoRequest;
import com.ithopeschool.projectbe.domain.call.OtherRoomTalk;
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
//
// image (2026-10-18 사진 질문, 상담 채팅만): 마지막 학생 말에 붙인 사진 한 장을 data URL 로 보냅니다. (없으면 null)
//   { "lines": [ …, { "speaker": "me", "text": "이 문제 어디서 틀렸어?" } ], "image":
// "data:image/jpeg;base64,/9j/…" }
//   사진은 그 한 번의 답에만 AI 에게 같이 보내고 버립니다. 저장 · 로그 안 함. 다음 말에는 다시 보내지 않아도 됩니다.
//   모양 · 크기(jpeg · png · webp, 4MB) 규칙은 사진으로 카드 만들기와 같습니다. (CardPhotoService.checkImage)
//   '목표 만들기' 대화(/goal-chats)는 같은 모양을 받지만 사진은 쓰지 않습니다.
//
// otherRooms (2026-10-22, 상담 채팅만): 학생이 오늘 다른 채팅 방에서 한 말 몇 줄입니다. (없으면 null)
//   { "lines": [ … ], "otherRooms": ["상담: 미적분 극한이 헷갈려", "과목 쌤: 곱의 미분법 알려 줘"] }
//   그 한 번의 답에만 대본 끝에 붙이고 저장 · 로그 안 함. 12줄 · 한 줄 200자까지 받고, 쓸 때는 6줄 · 80자로 줄입니다. (OtherRoomTalk)
public record ChatMessageRequest(
    @NotNull(message = "대화(lines)를 보내 주세요")
        @Size(min = 1, max = 60, message = "대화(lines)는 1 ~ 60줄까지 보낼 수 있어요")
        @Valid
        List<CallFinishRequest.Line> lines,
    @Size(max = CardPhotoRequest.MAX_IMAGE_CHARS, message = "사진이 너무 커요. 4MB 이하로 보내 주세요")
        String image,
    @Size(max = 12, message = "다른 방 이야기(otherRooms)는 12줄까지 보낼 수 있어요")
        List<
                @Size(
                    max = OtherRoomTalk.MAX_REQUEST_LINE_LENGTH,
                    message = "다른 방 이야기 한 줄은 200자 이하로 보내 주세요")
                String>
            otherRooms) {

  // 사진 없이 말만 보내는 모양 (테스트 · 목표 만들기 대화에서 씀)
  public ChatMessageRequest(List<CallFinishRequest.Line> lines) {
    this(lines, null, null);
  }

  // 다른 방 이야기 없이 보내는 모양 (테스트에서 씀)
  public ChatMessageRequest(List<CallFinishRequest.Line> lines, String image) {
    this(lines, image, null);
  }
}
