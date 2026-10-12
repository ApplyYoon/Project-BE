package com.ithopeschool.projectbe.domain.coach.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

// "어제 한 줄" 과 월간 코치 문단을 부탁할 때 FE 가 보내는 모양입니다. (FE src/lib/dailyCoach.ts, src/lib/monthlyCoach.ts)
// 주간(WeeklyCoachRequest)에서 mondayKey 만 뺀 모양이고, 숫자 하나의 규칙(30자, 쓸 수 있는 글자)은 주간과 똑같습니다.
//
// ⚠️ FE 는 "계산된 숫자" 만 보냅니다. 계획 내용·일기·이름 같은 글 원문은 받지 않습니다.
public record CoachCommentRequest(
    // 문단에 써도 되는 숫자들. AI 는 이 숫자만 가지고 문단을 씁니다. (모양은 WeeklyCoachRequest.Number)
    @NotEmpty(message = "숫자(numbers)를 하나 이상 보내 주세요")
        @Size(max = 10, message = "숫자(numbers)는 10개까지 보낼 수 있어요")
        @Valid
        List<WeeklyCoachRequest.Number> numbers,
    // 내가 정한 관리 강도 최대 한도 (0 회복 ~ 4 강제적 채찍)
    @Min(value = 0, message = "maxCareLevel 은 0 ~ 4 예요")
        @Max(value = 4, message = "maxCareLevel 은 0 ~ 4 예요")
        int maxCareLevel,
    // 내가 허용한 독설 수위 (1 ~ 5)
    @Min(value = 1, message = "maxHarshness 는 1 ~ 5 예요")
        @Max(value = 5, message = "maxHarshness 는 1 ~ 5 예요")
        int maxHarshness,
    // 안전 브레이크가 켜져 있는지. true 면 한도가 몇이든 위로하는 말투만 씁니다. 안 보내면 꺼진 것으로 봅니다.
    Boolean safetyBrakeOn) {}
