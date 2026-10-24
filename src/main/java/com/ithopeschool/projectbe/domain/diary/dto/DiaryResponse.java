package com.ithopeschool.projectbe.domain.diary.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.checkin.EmotionTag;
import com.ithopeschool.projectbe.domain.diary.Diary;
import com.ithopeschool.projectbe.domain.safety.HelpLine;
import com.ithopeschool.projectbe.domain.safety.SafetyService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 일기 하나를 FE 에 알려 주는 모양입니다. 본인에게만 보내는 응답이라 원문(content)이 들어 있습니다.
//   { "id": "7", "date": "2026-10-24", "content": "오늘은 폰을 보다가 ...", "writtenAt": "...",
//     "analyzed": true, "emotions": ["TIRED"], "causes": ["phone"], "keywords": ["수학"],
//     "showHelp": false, "helpLines": [], "helpMessage": null }
// - id 는 다른 API 와 같이 글자로 보냅니다.
// - 감정은 이름 그대로(TIRED), 원인은 FE 글자(tooMuch)로 보냅니다. 체크인과 같습니다.
// - analyzed 가 false 면 감정·원인·키워드가 빈 배열이어도 "없음" 이 아니라 "모름" 입니다.
// - showHelp 는 저장할 때 위기 표현이 감지됐는지입니다. 목록에서는 늘 false 입니다.
// - 누구의 일기인지(userId)는 담지 않습니다.
public record DiaryResponse(
    String id,
    LocalDate date,
    String content,
    Instant writtenAt,
    boolean analyzed,
    List<String> emotions,
    List<String> causes,
    List<String> keywords,
    boolean showHelp,
    List<HelpLine> helpLines) {

  @JsonProperty
  public String helpMessage() {
    return showHelp ? SafetyService.HELP_MESSAGE : null;
  }

  public static DiaryResponse from(Diary diary) {
    return from(diary, false);
  }

  public static DiaryResponse from(Diary diary, boolean showHelp) {
    return new DiaryResponse(
        String.valueOf(diary.getId()),
        diary.getDiaryDate(),
        diary.getContent(),
        diary.getWrittenAt(),
        diary.getAnalyzedAt() != null,
        // 늘 같은 순서가 되도록 enum 순서로 줄 세웁니다.
        diary.getEmotions().stream().sorted().map(EmotionTag::name).toList(),
        diary.getCauses().stream().sorted().map(MissReason::toValue).toList(),
        List.copyOf(diary.getKeywords()),
        showHelp,
        showHelp ? HelpLine.ALL : List.of());
  }
}
