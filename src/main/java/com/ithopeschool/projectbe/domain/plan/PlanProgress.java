package com.ithopeschool.projectbe.domain.plan;

import java.util.List;

// 계획 하나의 "실제 / 목표" 를 계산합니다. 계획 줄의 "32 / 50분" 과 진행 막대, 자동 완료에 씁니다.
// (이슈 #31 의 "오늘 목표 달성" 이 이 숫자를 봅니다. 하루치 합계는 DayProgress 가 냅니다)
//
// 규칙
//   1. 그 계획에서 타이머를 시작한 기록(어느 계획으로 켰는지가 같은 기록)만 셉니다.
//      과목이 같아도 계획 없이 켠 타이머는 세지 않습니다.
//   2. 목표를 채웠는지는 분이 아니라 밀리초로 비교합니다. (49분 59초는 50분 계획을 못 채운 것입니다)
//   3. 목표 시간이 0 이하인 계획은 진행률을 계산할 수 없어서 0%, "못 채움" 입니다.
//
// 예시 (50분 계획)
//   기록 없음                →  실제 0분,  0%,   못 채움, 남은 시간 50분
//   32분 공부                →  실제 32분, 64%,  못 채움, 남은 시간 18분
//   20분 + 30분 두 번 공부    →  실제 50분, 100%, 채움,    남은 시간 0분
//   49분 59초 공부           →  실제 49분, 99%,  못 채움, 남은 시간 1분
//   70분 공부 (목표를 넘김)   →  실제 70분, 100%, 채움,    남은 시간 0분
//
// FE 의 getPlanStudyMs · getPlanProgress (src/lib/planProgress.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 숫자가 어긋나지 않습니다.
//
// actualMinutes: 그 계획으로 실제 한 시간(분). 1분이 안 되는 나머지는 버립니다
// percent: 목표 대비 진행률 (0 ~ 100, 소수점은 버립니다)
// isReached: 목표 시간을 채웠으면 true
// remainingMinutes: 목표까지 남은 시간(분, 올림). 채웠으면 0
public record PlanProgress(
    int actualMinutes, int percent, boolean isReached, int remainingMinutes) {

  private static final long MILLIS_PER_MINUTE = 60_000;

  // 타이머 기록 하나에서 계산에 쓰는 값만 모은 것입니다.
  // planId: 어느 계획으로 켠 타이머인지. 계획 없이 켰으면 null
  // millis: 실제 길이(밀리초). 일시정지한 시간을 뺀 값이고, StudyTime.studyMillis 의 결과를 넣습니다.
  // (공부 기록 테이블이 아직 바뀌는 중이라, 엔티티가 아니라 이 계산에 필요한 두 값만 받습니다)
  public record PlanSession(Long planId, long millis) {}

  // 계획 하나에 연결된 기록의 실제 길이를 모두 더합니다. (밀리초)
  public static long millisOf(Long planId, List<PlanSession> sessions) {
    long totalMillis = 0;
    for (PlanSession session : sessions) {
      if (planId.equals(session.planId())) {
        totalMillis += session.millis();
      }
    }
    return totalMillis;
  }

  // 계획 하나의 진행 상황을 계산합니다.
  //
  // targetMinutes: 그 계획의 목표 시간(분)
  // actualMillis: 그 계획으로 실제 한 시간(밀리초). millisOf 의 결과
  public static PlanProgress of(int targetMinutes, long actualMillis) {
    // 정수끼리 나누면 소수점이 버려집니다. (49분 59초 → 49분)
    int actualMinutes = (int) (actualMillis / MILLIS_PER_MINUTE);
    long targetMillis = targetMinutes * MILLIS_PER_MINUTE;

    if (targetMillis <= 0) {
      return new PlanProgress(actualMinutes, 0, false, 0);
    }

    boolean isReached = actualMillis >= targetMillis;
    if (isReached) {
      return new PlanProgress(actualMinutes, 100, true, 0);
    }

    int percent = (int) (actualMillis * 100 / targetMillis);
    // 남은 시간은 올림합니다. 1초만 남아도 "1분 남음" 입니다. (남은 밀리초에 59,999 를 더한 뒤 나누면 올림이 됩니다)
    int remainingMinutes =
        (int) ((targetMillis - actualMillis + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE);
    return new PlanProgress(actualMinutes, percent, false, remainingMinutes);
  }
}
