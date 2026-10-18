package com.ithopeschool.projectbe.domain.ai.dto;

import java.util.List;

// 성향 요약 3줄의 결과입니다. 아무것도 저장하지 않습니다. (이슈 #237 표 9번)
//   { "lines": ["시작하기까지 시간이 걸리는 편일 수 있어", "…", "…"] }
// - lines: 심리검사에서 가장 두드러진 척도 3개에 한 줄씩 (AI 가 씀, 60자까지, 숫자 없음)
// - lines 가 null 이면 만들지 못한 것입니다. (서버에 심리검사가 없음 · AI 꺼짐 · 하루 한도 · 실패 · 쓸 수 없는 답)
// - 심리검사 답이 거의 다 같아서 믿기 어려우면 AI 를 부르지 않고 정해 둔 3줄입니다. (ProfileLines.UNSURE_LINES)
//   에러가 아니라 200 이고, FE 는 기본 문장을 씁니다.
// 학생이 쓴 글을 받지 않아서 위기 표현 검사(showHelp)는 없습니다.
public record ProfileLinesResponse(List<String> lines) {}
