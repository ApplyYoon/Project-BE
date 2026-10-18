package com.ithopeschool.projectbe.domain.experiment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

// 자기 실험 시작(POST) 요청의 모양입니다. FE 의 Experiment 와 같은 이름을 씁니다.
//   { "hypothesis": "폰·SNS 때문에 놓치는 날이 많다", "cause": "phone", "strategyId": "phone-bag",
//     "cardIds": ["12", "15"], "startDate": "2026-10-18", "minGainPp": 10 }
//
// - cause · startDate · minGainPp 는 선택입니다. 안 보내면 원인 없음 · 오늘 · 10%p 입니다.
// - 전략 id · 원인 값이 정해진 것인지, 내 카드인지는 Service 에서 검사합니다.
public record ExperimentStartRequest(
    @NotBlank(message = "가설을 적어 주세요") @Size(max = 100, message = "가설은 100자 이하로 적어 주세요")
        String hypothesis,
    String cause,
    @NotBlank(message = "전략을 골라 주세요") String strategyId,
    @NotEmpty(message = "실험 규칙을 붙일 카드를 하나 이상 골라 주세요") @Size(max = 5, message = "카드는 5장까지 붙일 수 있어요")
        List<
                @NotNull(message = "cardIds 에 빈 값이 있어요")
                @Pattern(regexp = "\\d{1,18}", message = "카드 번호는 숫자로 보내 주세요") String>
            cardIds,
    LocalDate startDate,
    @Min(value = 1, message = "그만둘 기준은 1~50%p 사이로 보내 주세요")
        @Max(value = 50, message = "그만둘 기준은 1~50%p 사이로 보내 주세요")
        Integer minGainPp) {}
