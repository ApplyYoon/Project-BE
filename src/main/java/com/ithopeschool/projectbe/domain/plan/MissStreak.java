package com.ithopeschool.projectbe.domain.plan;

import java.util.List;

// "계획을 못 채운 날이 연속으로 며칠인지" 세는 계산기입니다. (이슈 #31 의 "3일 연속 미달")
// 어제부터 하루씩 거슬러 올라가며 셉니다. 오늘은 아직 끝나지 않았으니 세지 않습니다.
//
//   계획이 있었고 달성률이 100% 미만인 날  →  못 채운 날 (계속 셉니다)
//   계획이 없던 날, 또는 100% 채운 날      →  여기서 멈춥니다
//
//   어제 50%, 그저께 80%, 3일 전 100%  →  2
//   어제 계획 없음                     →  0
//
// FE 의 countConsecutiveMissDays (src/lib/goalAdjustInput.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 판단이 어긋나지 않습니다.
public final class MissStreak {

  // 최대로 거슬러 올라가 볼 일수입니다. 부르는 쪽은 이만큼의 달성률만 준비하면 됩니다. (FE 와 같은 30일)
  public static final int MAX_LOOKBACK_DAYS = 30;

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private MissStreak() {}

  // 연속으로 못 채운 날 수를 돌려줍니다.
  //
  // ratesFromYesterday: 날짜별 달성률을 "어제, 그저께, 3일 전 …" 순서로 담은 목록입니다.
  //   값은 AchievementRate.percentOf 의 결과를 그대로 넣습니다. (0~100, 계획이 없던 날은 null)
  //   null 이 들어 있어야 하므로 List.of 가 아니라 Arrays.asList 나 ArrayList 로 만듭니다.
  public static int countConsecutiveMissDays(List<Integer> ratesFromYesterday) {
    int missDays = 0;
    for (Integer rate : ratesFromYesterday) {
      // 30일보다 더 오래된 날은 보지 않습니다.
      if (missDays >= MAX_LOOKBACK_DAYS) {
        break;
      }
      // 계획이 없던 날(null)이거나 다 채운 날(100)을 만나면 연속이 끝납니다.
      if (rate == null || rate >= 100) {
        break;
      }
      missDays++;
    }
    return missDays;
  }
}
