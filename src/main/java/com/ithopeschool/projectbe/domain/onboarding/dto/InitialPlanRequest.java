package com.ithopeschool.projectbe.domain.onboarding.dto;

import com.ithopeschool.projectbe.global.json.WholeNumberDeserializer;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

// "처음 계획 세우기 · 다시 세우기" 요청의 모양입니다. (FE 의 InitialPlan 에서 createdDate 를 뺀 것)
//   { "dailyMinutes": 180, "daysPerWeek": 5 }
// - 세운 날짜(createdDate)는 받지 않고 서버가 오늘 날짜로 정합니다. (새벽 4시에 날짜가 바뀜)
// - 고를 수 있는 값인지(60 ~ 300분, 3 · 5 · 6 · 7일)는 Service 에서 검사합니다.
// - 정수만 받습니다. 180.5 처럼 소수를 보내면 400 입니다. (WholeNumberDeserializer)
public record InitialPlanRequest(
    @NotNull(message = "하루 목표 시간(dailyMinutes)을 골라 주세요")
        @JsonDeserialize(using = WholeNumberDeserializer.class)
        Integer dailyMinutes,
    @NotNull(message = "일주일에 며칠(daysPerWeek)을 골라 주세요")
        @JsonDeserialize(using = WholeNumberDeserializer.class)
        Integer daysPerWeek) {}
