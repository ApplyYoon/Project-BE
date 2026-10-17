package com.ithopeschool.projectbe.domain.checkin;

import java.util.ArrayList;
import java.util.List;

// 오늘 체크인에서 물어볼 질문을 고릅니다. (이슈 #25)
// 하루 입력을 30초 안에 끝내려고, 필요 없는 질문은 빼고 돌려줍니다.
//
// 규칙
//   1. 기본으로 묻는 질문은 "기분" 하나입니다. (FE#242: 타이머를 시작할 때 1탭)
//   2. 컨디션 · 예상 순공시간은 매일 "자세히 체크인" 에서만 묻습니다.
//   3. "어제 못 채운 이유" 는 어제가 미달한 날일 때만 자세히 체크인에서 묻습니다.
//      미달 = 어제 달성률이 MISS_THRESHOLD_PERCENT(100%) 미만. 계획이 없던 날(달성률 null)은 미달이 아닙니다.
//      단, 어제 공부 기록에 그만둔 이유(stopReason, #13)가 이미 있으면 그 값을 쓰므로 묻지 않습니다.
//   4. "어제 약속 지켰어?" 는 어제 체크인에 약속이 있을 때만 자세히 체크인에서 묻습니다.
//   5. "개선안 실천 여부" 는 아직 묻지 않습니다. 개선안 실험(#52)이 서버에 아직 없기 때문입니다.
//      (이슈 #25: "#52 가 아직 없으면 그 질문은 빼고 먼저 만들어도 됩니다")
//
// 예시 (어제 달성률, 어제 그만둔 이유가 있나, 어제 약속이 있나 → 자세히에서 묻는 질문)
//   79,   없음, 없음  →  컨디션, 예상 순공, 못 채운 이유
//   100,  없음, 없음  →  컨디션, 예상 순공
//   null, 없음, 없음  →  컨디션, 예상 순공          (계획이 없던 날은 미달이 아님)
//   50,   있음, 있음  →  컨디션, 예상 순공, 약속 지켰는지  (이유는 이미 알고 있음)
//
// 통화 대본(CallScript)도 "어제 못 채웠는지" 를 isMissedDay 로 정합니다. 체크인과 통화의 기준이 같아야 해서입니다.
//
// basic: 기본으로 묻는 질문 / detail: 자세히 체크인에서만 묻는 질문
// 질문 이름은 체크인 저장(PUT /api/v1/checkins/{date})의 필드 이름과 같습니다. (promiseKept 는 FE CheckIn 의 이름)
public record CheckInQuestions(List<String> basic, List<String> detail) {

  // 미달 기준(%). 달성률이 이 값보다 낮으면 "못 채운 날" 입니다.
  // 100 으로 정한 이유: 연속 미달 계산(MissStreak)과 통화 대본이 이미 "100% 미만 = 못 채운 날" 로 보고 있어서,
  // 기준을 하나로 맞추려고 담당 학생이 정했습니다.
  public static final int MISS_THRESHOLD_PERCENT = 100;

  // 어제가 "못 채운 날" 인지. 계획이 없어서 달성률을 계산할 수 없는 날(null)은 못 채운 날이 아닙니다.
  //
  //   79 → true / 99 → true / 100 → false / null → false
  public static boolean isMissedDay(Integer achievementRate) {
    return achievementRate != null && achievementRate < MISS_THRESHOLD_PERCENT;
  }

  // 오늘 물어볼 질문을 고릅니다.
  //
  // yesterdayRate: 어제 달성률 (0 ~ 100, 계획이 없던 날은 null). DaySummary.of 의 achievementRate
  // yesterdayHasStopReason: 어제 공부 기록 중에 그만둔 이유를 고른 기록이 있는지
  // yesterdayHasPromise: 어제 체크인에 약속("만약 ~하면 → ~한다")이 있는지
  public static CheckInQuestions pick(
      Integer yesterdayRate, boolean yesterdayHasStopReason, boolean yesterdayHasPromise) {
    // 규칙 1
    List<String> basic = List.of("mood");

    // 규칙 2
    List<String> detail = new ArrayList<>();
    detail.add("condition");
    detail.add("expectedMinutes");

    // 규칙 3
    if (isMissedDay(yesterdayRate) && !yesterdayHasStopReason) {
      detail.add("missReason");
    }
    // 규칙 4
    if (yesterdayHasPromise) {
      detail.add("promiseKept");
    }
    return new CheckInQuestions(basic, List.copyOf(detail));
  }
}
