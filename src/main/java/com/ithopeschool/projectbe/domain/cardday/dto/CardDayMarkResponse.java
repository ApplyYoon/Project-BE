package com.ithopeschool.projectbe.domain.cardday.dto;

import com.ithopeschool.projectbe.domain.cardday.CardDayMark;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

// 카드 하루치 표시(미루기 · 실패) 하나를 FE 에 알려 주는 모양입니다.
//   { "id": "3", "cardId": "7", "date": "2026-10-12", "mark": "postponed", "reason": "…",
//     "cause": null, "newDate": "2026-10-13", "newStartTime": "20:00", "createdAt": 1790000000000 }
// - mark: "postponed"(미루기) / "failed"(실패). FE 의 CardDayMark 와 같은 글자입니다.
// - cause: 실패 원인 (실패일 때만). newDate · newStartTime: 미룬 새 날짜·시각 (미루기일 때만)
// - reason: 사유 글. 위기 표현이 감지된 글은 저장하지 않아서 null 입니다.
// - 누구의 표시인지(userId)는 담지 않습니다.
public record CardDayMarkResponse(
    String id,
    String cardId,
    LocalDate date,
    String mark,
    String reason,
    String cause,
    LocalDate newDate,
    String newStartTime,
    long createdAt) {

  // LocalTime 을 그냥 글자로 바꾸면 "19:00:00" 처럼 초가 붙을 수 있어서, 몇 시 몇 분만 꺼냅니다.
  private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

  public static CardDayMarkResponse from(CardDayMark mark) {
    return new CardDayMarkResponse(
        String.valueOf(mark.getId()),
        String.valueOf(mark.getCardId()),
        mark.getMarkDate(),
        mark.getMark().toValue(),
        mark.getReason(),
        mark.getCause() == null ? null : mark.getCause().toValue(),
        mark.getNewDate(),
        mark.getNewStartTime() == null ? null : mark.getNewStartTime().format(HOUR_MINUTE),
        mark.getCreatedAt().toEpochMilli());
  }
}
