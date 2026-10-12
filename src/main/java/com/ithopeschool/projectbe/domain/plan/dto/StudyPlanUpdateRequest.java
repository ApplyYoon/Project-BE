package com.ithopeschool.projectbe.domain.plan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// "계획 바꾸기" 요청의 모양입니다. 바꿀 것만 보냅니다. (PATCH)
//   { "done": true }   { "content": "수학 4단원", "targetMinutes": 60 }   { "startTime": "" }
// - 보내지 않은(null) 값은 그대로 둡니다. 아무것도 보내지 않은 요청은 Service 에서 400 으로 돌려보냅니다.
// - startTime 을 빈 글자("")로 보내면 시작 시각을 지웁니다. ("시간 미정". FE 의 changeStartTime 과 같은 약속)
// - kind 를 "play" 로 바꾸면 과목은 비워집니다. "study" 로 바꾸면 과목이 꼭 있어야 합니다.
public record StudyPlanUpdateRequest(
    @Pattern(regexp = "\\d*", message = "과목 번호가 올바르지 않아요") String subjectId,
    @Size(min = 1, max = 200, message = "할 일은 1자 이상 200자 이하로 적어 주세요") String content,
    @Min(value = 1, message = "목표 시간은 1분 이상이어야 해요")
        @Max(value = 600, message = "목표 시간은 600분(10시간) 이하여야 해요")
        Integer targetMinutes,
    Boolean done,
    // 빈 글자("")도 받습니다. (시작 시각 지우기)
    @Pattern(regexp = "|([01]\\d|2[0-3]):[0-5]\\d", message = "시작 시각은 19:00 처럼 보내 주세요")
        String startTime,
    String kind) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다. "   " 처럼 공백뿐인 내용은 빈 글자("")가 되어 위의 검사에 걸립니다.
  public StudyPlanUpdateRequest {
    content = content == null ? null : content.trim();
  }

  // 바꿀 값을 하나도 보내지 않았는지 알려 줍니다.
  public boolean isEmpty() {
    return subjectId == null
        && content == null
        && targetMinutes == null
        && done == null
        && startTime == null
        && kind == null;
  }
}
