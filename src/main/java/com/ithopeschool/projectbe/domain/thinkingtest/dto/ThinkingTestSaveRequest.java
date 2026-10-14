package com.ithopeschool.projectbe.domain.thinkingtest.dto;

import com.ithopeschool.projectbe.domain.call.dto.CallSessionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

// 생각 습관 테스트를 마쳤을 때 FE 가 보내는 모양입니다. (FE src/lib/thinkingTest.ts)
//   { "testVersion": 2,
//     "scores": [ { "habit": "PLANNING_FALLACY", "average": 6.0 }, … 8개 ],
//     "weaknesses": [ "GAME", "SLEEP" ] }
//
// 문장별 답은 받지 않고 습관마다의 평균(1 ~ 7)만 받습니다. 문장은 앞으로 바뀔 수 있어서입니다.
// "기타" 에 직접 쓴 약점 글은 받지 않습니다.
public record ThinkingTestSaveRequest(
    // 테스트 모양 번호 (FE 의 TEST_VERSION. 2 = 리커트 7점 척도)
    @NotNull(message = "테스트 모양 번호(testVersion)를 보내 주세요")
        @Min(value = 1, message = "testVersion 은 1 이상이에요")
        @Max(value = 100, message = "testVersion 은 100 이하예요")
        Integer testVersion,
    // 습관 8개의 평균. 습관마다 하나씩 정확히 8개
    @NotNull(message = "습관 점수(scores)를 보내 주세요")
        @Size(min = 8, max = 8, message = "습관 점수(scores)는 8개여야 해요")
        @Valid
        List<Score> scores,
    // 내가 꼽은 약점 칩들. 안 골랐으면 빈 목록이나 null
    @Size(max = 11, message = "weaknesses 는 11개까지 보낼 수 있어요")
        List<
                @NotNull(message = "weaknesses 에 빈 값이 있어요")
                @Pattern(
                    regexp = CallSessionRequest.WEAKNESS_NAMES,
                    message = "weaknesses 는 정해진 약점 이름이어야 해요")
                String>
            weaknesses) {

  // 습관 하나의 평균. habit: 생각 습관 이름 / average: 1.0 ~ 7.0
  public record Score(
      @NotNull(message = "습관 이름(habit)을 보내 주세요")
          @Pattern(regexp = CallSessionRequest.BIAS_NAMES, message = "habit 은 정해진 생각 습관 이름이어야 해요")
          String habit,
      @NotNull(message = "습관 평균(average)을 보내 주세요")
          @DecimalMin(value = "1.0", message = "average 는 1 ~ 7 이에요")
          @DecimalMax(value = "7.0", message = "average 는 1 ~ 7 이에요")
          Double average) {}
}
