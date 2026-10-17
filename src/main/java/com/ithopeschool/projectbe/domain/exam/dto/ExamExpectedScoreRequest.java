package com.ithopeschool.projectbe.domain.exam.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

// "시험 등록" 요청 안의 과목 하나의 예상 점수입니다. (FE 의 ExamSubjectScore 에서 actualScore 를 뺀 것)
//   { "subjectId": "3", "expectedScore": 85 }
// - subjectId 가 내 과목인지는 Service 에서 검사합니다.
//
// 점수를 왜 Integer 가 아니라 BigDecimal 로 받나요?
// Integer 로 받으면 85.5 를 보냈을 때 에러 없이 85 로 잘라서 받아 버립니다. (점수가 말없이 바뀜)
// BigDecimal 은 소수점을 그대로 받아서, @Digits(fraction = 0) 이 "정수가 아니다" 를 잡아낼 수 있습니다.
// 검사를 통과한 값은 정수라서 Service 에서 intValueExact() 로 안전하게 정수로 바꿉니다.
public record ExamExpectedScoreRequest(
    @NotNull(message = "과목을 골라 주세요")
        // 18자리까지만 받습니다. 그보다 길면 서버의 숫자(Long)에 안 들어가서 500 이 나기 때문입니다.
        @Pattern(regexp = "\\d{1,18}", message = "과목 번호가 올바르지 않아요")
        String subjectId,
    // 0 ~ 100 사이의 정수만 받습니다. (FE 의 parseScore 와 같은 범위. 범위의 기준은 ExamDday.MIN_SCORE · MAX_SCORE)
    // @Digits(integer = 3, fraction = 0): 소수점 아래는 0자리, 정수 부분은 3자리까지 (85.5 는 안 됨)
    @NotNull(message = "예상 점수를 보내 주세요")
        @Digits(integer = 3, fraction = 0, message = "점수는 0~100 사이의 정수로 보내 주세요")
        @DecimalMin(value = "0", message = "점수는 0~100 사이의 정수로 보내 주세요")
        @DecimalMax(value = "100", message = "점수는 0~100 사이의 정수로 보내 주세요")
        BigDecimal expectedScore) {}
