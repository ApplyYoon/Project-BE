package com.ithopeschool.projectbe.domain.message.dto;

import com.ithopeschool.projectbe.domain.message.Message;

// 메시지 하나의 응답 모양입니다. (FE 의 AiMessage 와 이름을 맞췄습니다)
//   { "id": "15", "kind": "nudge", "text": "...", "sentAt": 1790000000000,
//     "read": false, "readAt": null }
public record MessageResponse(
    // 메시지를 구분하는 값. FE 의 id 는 글자(string)라서 숫자를 글자로 바꿔 보냅니다.
    String id,
    // 종류: carrot · nudge · stick · event · recovery
    String kind,
    // 메시지 내용
    String text,
    // 보낸 시각 (밀리초 숫자)
    long sentAt,
    // 읽었으면 true
    boolean read,
    // 처음 읽은 시각 (밀리초 숫자). 아직 안 읽었으면 null
    Long readAt) {

  public static MessageResponse from(Message message) {
    return new MessageResponse(
        String.valueOf(message.getId()),
        message.getKind().toValue(),
        message.getText(),
        message.getSentAt().toEpochMilli(),
        message.isRead(),
        message.getReadAt() == null ? null : message.getReadAt().toEpochMilli());
  }
}
