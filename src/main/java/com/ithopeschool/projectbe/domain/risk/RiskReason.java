package com.ithopeschool.projectbe.domain.risk;

// 위험하다고 본 이유(위험 신호) 하나입니다. (이슈 #49 분석 ⑧) DB 에는 이름 그대로 저장합니다.
// 규칙을 더하면 여기에 한 줄을 더하고 RiskRule 에 규칙을 적습니다. 딸린 테이블(risk_forecast_reasons)은 칸을 늘릴 필요가 없습니다.
public enum RiskReason {
  // 그 요일의 최근 달성률이 전체 평균보다 눈에 띄게 낮음
  WEAK_WEEKDAY("약한 요일"),
  // 어젯밤 늦게(새벽 1시 이후) 잠
  LATE_SLEEP("늦게 잠");

  // 화면·문서에 쓰는 이름
  private final String label;

  RiskReason(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
