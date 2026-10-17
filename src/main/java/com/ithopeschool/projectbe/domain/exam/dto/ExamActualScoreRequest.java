package com.ithopeschool.projectbe.domain.exam.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

// "실제 점수 입력" 요청 안의 과목 하나의 실제 점수입니다.
//   { "subjectId": "3", "actualScore": 80 }
// - subjectId 가 이 시험에 예상 점수를 적어 둔 과목인지는 Service 에서 검사합니다.
// - 점수를 BigDecimal 로 받는 이유는 ExamExpectedScoreRequest 의 설명을 보세요. (80.5 를 80 으로 잘라 받지 않으려고)
public record ExamActualScoreRequest(
    @NotNull(message = "과목을 골라 주세요")
        // 18자리까지만 받습니다. 그보다 길면 서버의 숫자(Long)에 안 들어가서 500 이 나기 때문입니다.
        @Pattern(regexp = "\\d{1,18}", message = "과목 번호가 올바르지 않아요")
        String subjectId,
    // 0 ~ 100 사이의 정수만 받습니다. (FE 의 parseScore 와 같은 범위)
    @NotNull(message = "실제 점수를 보내 주세요")
        @Digits(integer = 3, fraction = 0, message = "점수는 0~100 사이의 정수로 보내 주세요")
        @DecimalMin(value = "0", message = "점수는 0~100 사이의 정수로 보내 주세요")
        @DecimalMax(value = "100", message = "점수는 0~100 사이의 정수로 보내 주세요")
        BigDecimal actualScore) {}
