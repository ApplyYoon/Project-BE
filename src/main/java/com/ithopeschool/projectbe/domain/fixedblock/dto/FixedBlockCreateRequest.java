package com.ithopeschool.projectbe.domain.fixedblock.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

// "고정 일정 만들기" 요청의 모양입니다. (FE 의 FixedBlock 에서 id · createdAt 을 뺀 것)
//   { "title": "학교", "repeatDays": [1, 2, 3, 4, 5], "startTime": "08:00", "endTime": "16:30" }
// - 네 칸 모두 꼭 보냅니다.
// - 같은 요일을 두 번 보냈는지, 끝 시각이 시작보다 뒤인지(하루 시작 새벽 4시 기준)는 Service 에서 검사합니다.
public record FixedBlockCreateRequest(
    @NotBlank(message = "무슨 일정인지 적어 주세요") @Size(max = 20, message = "일정 이름은 20자 이하로 적어 주세요")
        String title,
    // 0 = 일요일 … 6 = 토요일. 하나 이상.
    @NotEmpty(message = "요일을 하나 이상 골라 주세요") @Size(max = 7, message = "반복 요일은 7개까지 보낼 수 있어요")
        List<
                @NotNull(message = "반복 요일을 0 ~ 6 숫자로 보내 주세요")
                @Min(value = 0, message = "반복 요일은 0(일) ~ 6(토) 으로 보내 주세요")
                @Max(value = 6, message = "반복 요일은 0(일) ~ 6(토) 으로 보내 주세요") Integer>
            repeatDays,
    // "08:00" 처럼 두 자리:두 자리. 00:00 ~ 23:59 만 됩니다.
    @NotNull(message = "시작 시각을 보내 주세요")
        @Pattern(regexp = TIME_PATTERN, message = "시작 시각은 08:00 처럼 보내 주세요")
        String startTime,
    @NotNull(message = "끝 시각을 보내 주세요")
        @Pattern(regexp = TIME_PATTERN, message = "끝 시각은 16:30 처럼 보내 주세요")
        String endTime) {

  // 시각 모양: 00:00 ~ 23:59 (바꾸기 요청도 같이 씁니다)
  public static final String TIME_PATTERN = "([01]\\d|2[0-3]):[0-5]\\d";

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다. 그래서 위의 검사는 공백을 지운 값으로 돌아갑니다.
  public FixedBlockCreateRequest {
    title = title == null ? null : title.trim();
  }
}
