package com.ithopeschool.projectbe.domain.cardday.dto;

import com.ithopeschool.projectbe.domain.session.dto.SessionEndReportRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

// "사후 입력" 요청의 모양입니다. (최종 기획서 「4.4 타이머」 사후 입력, 이슈 #225)
// 오프라인 세션을 켜지 못했을 때, 끝난 뒤에 "언제부터 언제까지 했다" 를 적습니다.
//   { "date": "2026-10-12", "startedAt": 1790000000000, "endedAt": 1790003000000,
//     "endReport": { "checked": { "i1": 1790003000000 }, "proof": { "link": "https://…" } } }
// - date: 어느 날의 카드인지. startedAt 도 그날(새벽 4시 기준) 안이어야 합니다.
// - endReport: 공부 기록 저장의 종료 팝업과 같은 모양 (선택). 증빙(proof)이 없으면 의지력 계산에서 실행률 50% 입니다.
public record LateEntryRequest(
    @NotNull(message = "어느 날의 카드인지 보내 주세요") LocalDate date,
    @NotNull(message = "시작 시각을 보내 주세요") Long startedAt,
    @NotNull(message = "끝 시각을 보내 주세요") Long endedAt,
    @Valid SessionEndReportRequest endReport) {}
