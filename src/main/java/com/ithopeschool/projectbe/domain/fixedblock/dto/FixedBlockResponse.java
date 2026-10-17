package com.ithopeschool.projectbe.domain.fixedblock.dto;

import com.ithopeschool.projectbe.domain.fixedblock.FixedBlock;
import java.time.format.DateTimeFormatter;
import java.util.List;

// 고정 일정 하나를 FE 에 알려 주는 모양입니다. FE 의 FixedBlock 과 같은 이름을 씁니다.
//   { "id": "3", "title": "학교", "repeatDays": [1, 2, 3, 4, 5],
//     "startTime": "08:00", "endTime": "16:30", "createdAt": 1790000000000 }
// - id 는 FE 타입이 글자(string)라서 숫자를 글자로 바꿔 보냅니다.
// - createdAt 은 밀리초 숫자입니다. (docs/API.md 2-2)
// - 누구의 일정인지(userId)는 담지 않습니다.
public record FixedBlockResponse(
    String id,
    String title,
    List<Integer> repeatDays,
    String startTime,
    String endTime,
    long createdAt) {

  // LocalTime 을 그냥 글자로 바꾸면 "08:00:00" 처럼 초가 붙을 수 있어서, 몇 시 몇 분만 꺼냅니다.
  private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

  public static FixedBlockResponse from(FixedBlock block) {
    return new FixedBlockResponse(
        String.valueOf(block.getId()),
        block.getTitle(),
        block.repeatDayList(),
        block.getStartTime().format(HOUR_MINUTE),
        block.getEndTime().format(HOUR_MINUTE),
        block.getCreatedAt().toEpochMilli());
  }
}
