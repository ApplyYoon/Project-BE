package com.ithopeschool.projectbe.domain.experiment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 자기 실험의 상태입니다. FE 의 ExperimentStatus('running' · 'effective' · 'noEffect' · 'unclear')와 같습니다.
// DB 에는 이름 그대로(RUNNING) 저장하고, FE 와는 value("running")로 주고받습니다.
@Getter
@RequiredArgsConstructor
public enum ExperimentStatus {
  // 진행 중 (14일이 아직 안 끝남)
  RUNNING("running"),
  // 효과 있음: 실행률이 그만둘 기준보다 많이 오름 → 나 사용설명서
  EFFECTIVE("effective"),
  // 효과 없음: 다른 전략으로 바꿉니다
  NO_EFFECT("noEffect"),
  // 기록이 모자라 알 수 없음
  UNCLEAR("unclear");

  private final String value;
}
