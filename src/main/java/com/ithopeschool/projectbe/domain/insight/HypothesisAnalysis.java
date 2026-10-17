package com.ithopeschool.projectbe.domain.insight;

// 첫 가설을 2주 뒤에 "어떤 분석으로" 확인할지입니다. (이슈 #35 "가설마다 나중에 확인할 분석이 연결되어 있다")
// 분석 이슈 번호(issue)를 같이 적어 두어서, 가설 하나를 보면 어느 분석 결과와 맞춰 보면 되는지 바로 알 수 있습니다.
// FE 와 주고받을 때는 value 글자("rhythm")를 씁니다.
public enum HypothesisAnalysis {
  // #37 리듬·패턴: 요일·시간대 효율, 이벤트(시험·학원 늦게 끝난 날) 다음 날 효과
  RHYTHM("rhythm", 37),
  // #38 조건-성과 관계: 수면·컨디션이 좋은 날과 나쁜 날의 성과 비교
  CONDITION("condition", 38),
  // #39 실패 원인: 계획을 못 지킨 날의 이유 빈도
  FAILURE_CAUSE("failureCause", 39),
  // #40 인식-실제 갭: "나는 이렇다" 고 답한 것과 실제 기록의 차이
  PERCEPTION_GAP("perceptionGap", 40);

  // FE 와 주고받는 글자
  private final String value;

  // 이 분석을 만드는 이슈 번호
  private final int issue;

  HypothesisAnalysis(String value, int issue) {
    this.value = value;
    this.issue = issue;
  }

  public String getValue() {
    return value;
  }

  public int getIssue() {
    return issue;
  }
}
