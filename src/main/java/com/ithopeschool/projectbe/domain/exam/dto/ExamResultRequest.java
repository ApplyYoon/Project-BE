package com.ithopeschool.projectbe.domain.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

// "실제 점수 입력" 요청의 모양입니다.
//   { "subjectScores": [ { "subjectId": "3", "actualScore": 80 }, { "subjectId": "4",
// "actualScore": 72 } ] }
// - 보낸 과목의 실제 점수만 넣습니다. 보내지 않은 과목은 그대로입니다.
// - 같은 과목을 두 번 보내면 안 됩니다. (Service 에서 검사)
public record ExamResultRequest(
    // 50개까지만 받습니다. (시험 등록과 같은 한도입니다)
    // @Valid: 목록 안의 과목 점수 하나하나도 똑같이 검사합니다.
    @NotEmpty(message = "실제 점수를 하나 이상 보내 주세요") @Size(max = 50, message = "과목은 한 번에 50개까지 보낼 수 있어요")
        List<@Valid ExamActualScoreRequest> subjectScores) {}
