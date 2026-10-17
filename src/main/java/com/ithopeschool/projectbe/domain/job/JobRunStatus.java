package com.ithopeschool.projectbe.domain.job;

// 작업 실행 한 번의 결과 종류입니다.
public enum JobRunStatus {
  // 작업을 실행했고 끝까지 잘 끝남
  DONE,
  // 같은 작업이 이미 실행 중이라서 또 시작하지 않고 건너뜀 (두 번 겹쳐 돌지 않게 막은 것)
  SKIPPED,
  // 작업을 실행하다가 예외가 남
  FAILED
}
