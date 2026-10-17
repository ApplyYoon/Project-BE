package com.ithopeschool.projectbe.domain.plan;

import com.ithopeschool.projectbe.domain.plan.PlanProgress.PlanSession;
import java.util.List;

// 하루치 계획이 얼마나 진행됐는지 계산합니다. 홈의 "2 / 4 완료 · 1시간 42분 / 4시간" 에 쓰는 숫자입니다.
// (이슈 #31 의 "오늘 목표 달성" 은 actualMinutes 가 targetMinutes 이상인지로 판단합니다 — EventMessage.Input)
//
// 규칙
//   1. 노는 계획은 공부가 아니라서 개수와 시간 어디에도 넣지 않습니다. (노는 계획은 playOf 가 따로 셉니다)
//   2. 실제 시간은 계획마다 PlanProgress 로 구한 분을 더합니다. (계획 줄의 숫자를 더하면 이 값이 됩니다)
//      그래서 계획 없이 켠 타이머의 시간은 들어가지 않습니다.
//   3. 목표 시간의 합이 0 이면 진행률은 0% 입니다.
//
// 예시
//   수학 60분(60분 함, 끝냄) · 영어 30분(30분 함, 끝냄) · 국어 50분(12분 함) · 탐구 100분(안 함)
//     →  계획 4개, 끝낸 것 2개, 실제 102분, 목표 240분, 42%
//   계획이 없는 날   →  모두 0
//
// FE 의 getDayProgress · getPlayProgress (src/lib/planProgress.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 숫자가 어긋나지 않습니다.
//
// totalCount: 공부 계획의 수 / doneCount: 그중 끝낸(체크한) 계획의 수
// actualMinutes: 공부 계획들로 실제 공부한 시간의 합(분)
// targetMinutes: 공부 계획들의 목표 시간의 합(분)
// percent: 목표 대비 진행률 (0 ~ 100, 소수점은 버립니다)
public record DayProgress(
    int totalCount, int doneCount, int actualMinutes, int targetMinutes, int percent) {

  // 계획 하나에서 계산에 쓰는 값만 모은 것입니다.
  // id: 계획 번호 / targetMinutes: 목표 시간(분) / done: 끝냈다고 체크했으면 true
  public record PlanItem(Long id, int targetMinutes, boolean done) {}

  // 하루치 노는 계획이 얼마나 진행됐는지입니다. ("노는 계획 60분 · 실제 95분" 한 줄에 씁니다)
  // count: 노는 계획의 수 / targetMinutes: 계획한 시간의 합(분) / actualMinutes: 실제 논 시간의 합(분)
  public record PlayProgress(int count, int targetMinutes, int actualMinutes) {}

  // 하루치 공부 계획의 진행 상황을 계산합니다.
  //
  // studyPlans: 그날의 "공부" 계획들. 노는 계획은 부르는 쪽에서 빼고 넘깁니다. (FE 는 함수 안에서 걸러냅니다)
  // sessions: 타이머 기록들. 다른 계획·다른 날의 기록이 섞여 있어도 됩니다. 계획 번호가 같은 것만 셉니다.
  public static DayProgress of(List<PlanItem> studyPlans, List<PlanSession> sessions) {
    int doneCount = 0;
    int actualMinutes = 0;
    int targetMinutes = 0;
    for (PlanItem plan : studyPlans) {
      if (plan.done()) {
        doneCount++;
      }
      long actualMillis = PlanProgress.millisOf(plan.id(), sessions);
      actualMinutes += PlanProgress.of(plan.targetMinutes(), actualMillis).actualMinutes();
      targetMinutes += plan.targetMinutes();
    }

    int percent = targetMinutes > 0 ? Math.min(100, actualMinutes * 100 / targetMinutes) : 0;
    return new DayProgress(studyPlans.size(), doneCount, actualMinutes, targetMinutes, percent);
  }

  // 하루치 노는 계획의 "계획 / 실제" 를 계산합니다.
  // 노는 시간은 순공에 넣지 않지만, 계획보다 얼마나 더 놀았는지는 숫자로 보여줍니다.
  //
  //   게임 60분(95분 놂) · 영상 30분(안 놂)   →  2개, 계획 90분, 실제 95분
  //   노는 계획이 없는 날                     →  모두 0
  //
  // playPlans: 그날의 "노는" 계획들 / sessions: 노는 기록까지 들어 있는 타이머 기록들
  public static PlayProgress playOf(List<PlanItem> playPlans, List<PlanSession> sessions) {
    int targetMinutes = 0;
    int actualMinutes = 0;
    for (PlanItem plan : playPlans) {
      targetMinutes += plan.targetMinutes();
      long actualMillis = PlanProgress.millisOf(plan.id(), sessions);
      actualMinutes += PlanProgress.of(plan.targetMinutes(), actualMillis).actualMinutes();
    }
    return new PlayProgress(playPlans.size(), targetMinutes, actualMinutes);
  }
}
