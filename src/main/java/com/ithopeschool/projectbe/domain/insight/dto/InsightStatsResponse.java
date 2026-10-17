package com.ithopeschool.projectbe.domain.insight.dto;

// 내 인사이트 반응의 통계입니다. "[몰랐는데 맞아] 비율" 이 이 앱의 핵심 지표입니다. (이슈 #43)
//   { "reactedCount": 4, "surprisedCount": 3, "surprisedPercent": 75 }
// - reactedCount: 반응을 누른 인사이트 수 (세 가지 중 무엇이든)
// - surprisedCount: 그중 [몰랐는데 맞아] 를 누른 수
// - surprisedPercent: surprisedCount ÷ reactedCount × 100, 소수점은 버립니다.
//   반응을 누르지 않은 인사이트는 "모름" 이라 빼고 셉니다. (담당 학생이 정함)
//   아직 하나도 누르지 않았으면 나눌 수 없어서 null 입니다. (0% 와 구별)
public record InsightStatsResponse(
    long reactedCount, long surprisedCount, Integer surprisedPercent) {}
