package com.ithopeschool.projectbe.domain.fixedblock.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

// "고정 일정 바꾸기" 요청의 모양입니다. 바꿀 것만 보냅니다. (PATCH)
//   { "title": "영어 학원" }   { "repeatDays": [2, 4] }   { "endTime": "22:00" }
// 보내지 않은(null) 값은 그대로 둡니다. 아무것도 보내지 않은 요청은 Service 에서 400 으로 돌려보냅니다.
// 시작 · 끝 시각 중 하나만 바꿔도, 바뀐 뒤의 두 시각으로 "끝이 시작보다 뒤" 인지 Service 가 봅니다.
public record FixedBlockUpdateRequest(
    @Size(min = 1, max = 20, message = "일정 이름은 1자 이상 20자 이하로 적어 주세요") String title,
    @Size(min = 1, max = 7, message = "요일을 1 ~ 7개 골라 주세요")
        List<
                @NotNull(message = "반복 요일을 0 ~ 6 숫자로 보내 주세요")
                @Min(value = 0, message = "반복 요일은 0(일) ~ 6(토) 으로 보내 주세요")
                @Max(value = 6, message = "반복 요일은 0(일) ~ 6(토) 으로 보내 주세요") Integer>
            repeatDays,
    @Pattern(regexp = FixedBlockCreateRequest.TIME_PATTERN, message = "시작 시각은 08:00 처럼 보내 주세요")
        String startTime,
    @Pattern(regexp = FixedBlockCreateRequest.TIME_PATTERN, message = "끝 시각은 16:30 처럼 보내 주세요")
        String endTime) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다. "   " 처럼 공백뿐인 이름은 빈 글자("")가 되어 위의 검사에 걸립니다.
  public FixedBlockUpdateRequest {
    title = title == null ? null : title.trim();
  }
}
