package com.ithopeschool.projectbe.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// 시험 회고 → 다음 카드에 넣을 한 가지 요청의 모양입니다. (이슈 #237 표 10번)
//   { "examId": "7" }
// - examId: 회고를 쓴 내 시험의 번호. 18자리까지만 받습니다. (그보다 길면 서버의 숫자(Long)에 안 들어갑니다)
// 회고 답·점수는 보내지 않습니다. 서버가 DB 에 저장된 그 시험의 회고와 과목 점수를 읽습니다.
public record ExamNextStepRequest(
    @NotBlank(message = "시험 번호를 보내 주세요") @Pattern(regexp = "\\d{1,18}", message = "시험 번호가 올바르지 않아요")
        String examId) {}
