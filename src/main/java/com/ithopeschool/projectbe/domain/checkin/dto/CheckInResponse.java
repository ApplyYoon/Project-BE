package com.ithopeschool.projectbe.domain.checkin.dto;

import com.ithopeschool.projectbe.domain.checkin.CheckIn;
import com.ithopeschool.projectbe.domain.safety.HelpLine;
import java.time.LocalDate;
import java.util.List;

// 체크인 하나를 FE 에 알려 주는 모양입니다. FE 의 CheckIn 타입과 같은 이름입니다.
//   { "id": "3", "date": "2026-10-17", "source": "manual", "condition": 4, "mood": 3,
//     "missReason": "etc", "missReasonNote": "동아리 발표", "expectedMinutes": 120,
//     "promiseIf": "폰을 보고 싶으면", "promiseThen": "물 한 잔 마신다", "suggestionKept": true }
// - id 는 다른 API 와 같이 글자로 보냅니다.
// - source · missReason 은 FE 글자("quick", "tooMuch")로 보냅니다.
// - 답하지 않은 값은 null 입니다. (0 이 아닙니다)
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
    boolean showHelp,
    List<HelpLine> helpLines) {

  @com.fasterxml.jackson.annotation.JsonProperty
  public String helpMessage() {
    return showHelp ? com.ithopeschool.projectbe.domain.safety.SafetyService.HELP_MESSAGE : null;
  }

  // 기존 호출자도 도움 안내가 없는 응답을 만들 수 있습니다.
  public CheckInResponse(
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
      Boolean suggestionKept) {
    this(
        id,
        date,
        source,
        condition,
        mood,
        missReason,
        missReasonNote,
        expectedMinutes,
        promiseIf,
        promiseThen,
        suggestionKept,
        false,
        List.of());
  }

  public static CheckInResponse from(CheckIn checkIn) {
    return from(checkIn, false);
  }

  public static CheckInResponse from(CheckIn checkIn, boolean showHelp) {
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
        showHelp,
        showHelp ? HelpLine.ALL : List.of());
  }
}
