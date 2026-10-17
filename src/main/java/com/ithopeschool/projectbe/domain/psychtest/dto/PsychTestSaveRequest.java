package com.ithopeschool.projectbe.domain.psychtest.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

// 심리검사 결과 저장 요청의 모양입니다. (이슈 #219)
//   { "answers": { "S1": 6, "S2": 5, "S3": 4, "S4": 5, "S5": 6, "A1": 3, …, "C5": 3 } }
// - answers: 문장 코드(S1 ~ S5 … C1 ~ C5) → 답(1 ~ 7). 35개 모두 보냅니다.
//   옛 14문항 검사(척도마다 앞의 두 문장: S1 · S2 · A1 · A2 …)를 한 결과는 그 14개만 보내도 됩니다.
//   코드가 모르는 것이거나, 일부만 답했거나, 1 ~ 7 밖이면 Service 가 400 으로 막습니다. (어느 것이 틀렸는지 알려 주려고)
// 척도 점수는 보내지 않습니다. 서버가 FE 와 같은 규칙으로 계산합니다. (PsychScore)
public record PsychTestSaveRequest(
    @NotNull(message = "심리검사 답을 보내 주세요") Map<String, Integer> answers) {}
