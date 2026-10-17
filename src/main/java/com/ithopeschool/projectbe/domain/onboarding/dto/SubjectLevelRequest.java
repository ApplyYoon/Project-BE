package com.ithopeschool.projectbe.domain.onboarding.dto;

import com.ithopeschool.projectbe.global.json.WholeNumberDeserializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import tools.jackson.databind.annotation.JsonDeserialize;

// 과목 하나의 현재 수준. 설문 요청의 subjectLevels 안에 들어갑니다. (FE 의 SubjectLevel)
//   { "subjectId": "3", "grade": 2, "confidence": 4 }    등급으로 적었을 때
//   { "subjectId": "5", "score": 85, "confidence": 3 }   점수로 적었을 때
// - 등급과 점수는 둘 중 하나만 보냅니다. 둘 다 보내거나 둘 다 빼면 Service 가 400 으로 돌려보냅니다.
// - subjectId 가 내 과목인지는 Service 에서 검사합니다. (남의 과목이면 404)
// - 숫자 칸은 정수만 받습니다. 자신감 3.7 처럼 소수를 보내면 400 입니다. (WholeNumberDeserializer)
public record SubjectLevelRequest(
    // FE 의 id 는 글자라서 글자로 받습니다. 숫자만, 18자리까지 받습니다. (Long 에 들어가는 길이)
    @NotNull(message = "과목 번호(subjectId)를 보내 주세요")
        @Pattern(regexp = "\\d{1,18}", message = "과목 번호가 올바르지 않아요")
        String subjectId,
    @Min(value = 1, message = "등급은 1 ~ 9 예요")
        @Max(value = 9, message = "등급은 1 ~ 9 예요")
        @JsonDeserialize(using = WholeNumberDeserializer.class)
        Integer grade,
    @Min(value = 0, message = "점수는 0 ~ 100 이에요")
        @Max(value = 100, message = "점수는 0 ~ 100 이에요")
        @JsonDeserialize(using = WholeNumberDeserializer.class)
        Integer score,
    @NotNull(message = "자신감(confidence)을 골라 주세요")
        @Min(value = 1, message = "자신감은 1 ~ 5 예요")
        @Max(value = 5, message = "자신감은 1 ~ 5 예요")
        @JsonDeserialize(using = WholeNumberDeserializer.class)
        Integer confidence) {}
