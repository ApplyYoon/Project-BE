package com.ithopeschool.projectbe.domain.stats.dto;

import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.stats.MissReasonStats;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// GET /api/v1/stats/miss-reasons 의 응답입니다. (이슈 #39 분석 ④ 실패 원인)
// last14Days: 최근 14일 / last30Days: 최근 30일
public record MissReasonStatsResponse(Period last14Days, Period last30Days) {

  // 한 기간의 결과
  // from, to: 계산한 기간 'YYYY-MM-DD' (둘 다 포함, to 는 어제)
  // missedDayCount: 기간 안에서 계획을 못 채운 날 수 (이유를 안 고른 날 포함)
  // reasons: 원인마다의 결과. 나온 날이 많은 것부터
  // topReasons: 주요 원인 최대 2개 ('기타' 는 뺌). 이유가 하나도 없으면 빈 목록 []
  public record Period(
      LocalDate from,
      LocalDate to,
      int missedDayCount,
      List<Reason> reasons,
      List<String> topReasons) {}

  // 원인 하나
  // reason: 원인 글자 ("tired", "phone", "tooMuch" 등. 체크인의 missReason 과 같은 글자)
  // dayCount: 그 원인이 나온 못 채운 날 수
  // weekdayCounts: 요일별 날 수. 7칸이고 0번째 = 월요일 … 6번째 = 일요일
  // averageRecoveryDays: 평균 회복 속도(일). 회복한 날이 없으면 null
  // notRecoveredCount: 기간 끝까지 다시 다 채우지 못한 날 수
  // dates: 근거 날짜들 'YYYY-MM-DD' (오래된 날부터)
  public record Reason(
      String reason,
      int dayCount,
      List<Integer> weekdayCounts,
      Double averageRecoveryDays,
      int notRecoveredCount,
      List<LocalDate> dates) {}

  public static MissReasonStatsResponse of(MissReasonStats last14Days, MissReasonStats last30Days) {
    return new MissReasonStatsResponse(periodOf(last14Days), periodOf(last30Days));
  }

  private static Period periodOf(MissReasonStats stats) {
    List<Reason> reasons = new ArrayList<>();
    for (MissReasonStats.ReasonStat stat : stats.reasons()) {
      reasons.add(
          new Reason(
              stat.reason().toValue(),
              stat.dayCount(),
              stat.weekdayCounts(),
              stat.averageRecoveryDays(),
              stat.notRecoveredCount(),
              stat.dates()));
    }
    // 원인은 FE 와 주고받는 글자("phone" 등)로 바꿔서 돌려줍니다.
    List<String> topReasons = new ArrayList<>();
    for (MissReason reason : stats.topReasons()) {
      topReasons.add(reason.toValue());
    }
    return new Period(stats.from(), stats.to(), stats.missedDayCount(), reasons, topReasons);
  }
}
