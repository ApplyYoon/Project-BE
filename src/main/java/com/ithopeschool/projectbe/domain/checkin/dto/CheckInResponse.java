package com.ithopeschool.projectbe.domain.checkin.dto;

import com.ithopeschool.projectbe.domain.checkin.CheckIn;
import com.ithopeschool.projectbe.domain.checkin.EmotionTag;
import java.time.LocalDate;
import java.util.List;

// 체크인 하나를 FE 에 알려 주는 모양입니다. FE 의 CheckIn 타입과 같은 이름입니다.
//   { "id": "3", "date": "2026-10-17", "source": "manual", "condition": 4, "mood": 3,
//     "missReason": "etc", "missReasonNote": "동아리 발표", "expectedMinutes": 120,
//     "promiseIf": "폰을 보고 싶으면", "promiseThen": "물 한 잔 마신다", "suggestionKept": true,
//     "emotions": ["STUCK", "TIRED"] }
// - id 는 다른 API 와 같이 글자로 보냅니다.
// - source · missReason 은 FE 글자("quick", "tooMuch")로 보냅니다.
// - 답하지 않은 값은 null 입니다. (0 이 아닙니다) 감정 태그는 안 골랐으면 빈 배열 [] 입니다.
// - 감정 태그는 이름 그대로(STUCK), EmotionTag 에 적힌 순서로 보냅니다. (이슈 #220)
// - 누구의 체크인인지(userId)는 담지 않습니다.
public record CheckInResponse(
    String id,
    LocalDate date,
    String source,
    Integer condition,
    Integer mood,
    String missReason,
    String missReasonNote,
    Integer expectedMinutes,
    String promiseIf,
    String promiseThen,
    Boolean suggestionKept,
    List<String> emotions) {

  public static CheckInResponse from(CheckIn checkIn) {
    return new CheckInResponse(
        String.valueOf(checkIn.getId()),
        checkIn.getCheckInDate(),
        checkIn.getSource().toValue(),
        checkIn.getCondition(),
        checkIn.getMood(),
        checkIn.getMissReason() == null ? null : checkIn.getMissReason().toValue(),
        checkIn.getMissReasonNote(),
        checkIn.getExpectedMinutes(),
        checkIn.getPromiseIf(),
        checkIn.getPromiseThen(),
        checkIn.getSuggestionKept(),
        // EnumSet 이 아니어도 늘 같은 순서가 되도록 EmotionTag 순서로 다시 줄 세웁니다.
        checkIn.getEmotions().stream().sorted().map(EmotionTag::name).toList());
  }
}
