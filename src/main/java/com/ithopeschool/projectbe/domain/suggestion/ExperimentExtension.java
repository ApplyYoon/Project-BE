package com.ithopeschool.projectbe.domain.suggestion;

// 효과 판정이 "애매함" 일 때 실험을 1주 더 하는 규칙입니다. (이슈 #54 "연장은 한 번만 한다", 기획서 v2 4장 「효과 판정」)
//
//   애매함 + 아직 연장한 적 없음   →  1주(7일) 더 할 수 있음
//   애매함 + 이미 한 번 연장함     →  더 늘릴 수 없음
//   효과 있음 · 효과 없음          →  연장하지 않음 (사용설명서에 올리거나, 전략을 바꿉니다)
//
// 한 번만 연장하는 이유: 기록이 모자라면 몇 번을 늘려도 계속 "애매함" 이 나올 수 있습니다.
// 끝없이 늘어나면 도전 자리(최대 2개)가 비지 않아서 새 개선안을 고를 수 없게 됩니다.
//
// 연장은 시작일을 그대로 두고 기간만 늘립니다. 그래서 지금까지의 이행 기록과 이어서 셉니다.
//   10월 1일 시작, 14일  →  연장하면 21일 (10월 1일 ~ 10월 21일, 결과는 10월 22일)
//
// ⚠️ FE 는 아직 횟수를 세지 않아서 "1주 더 해보기" 를 여러 번 누를 수 있습니다. (src/components/ui/ExperimentSection.tsx)
//    늘리는 일수(7일)만 FE 의 EXTEND_DAYS 와 같습니다. "한 번만" 은 이슈 #54 의 완료 조건을 따른 것입니다.
public final class ExperimentExtension {

  // 한 번 연장할 때 늘리는 일수입니다. (기획서: 애매하면 1주 연장)
  public static final int EXTEND_DAYS = 7;

  // 실험 하나를 연장할 수 있는 횟수입니다.
  public static final int MAX_EXTEND_COUNT = 1;

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private ExperimentExtension() {}

  // 이 실험을 1주 더 할 수 있으면 true 입니다.
  //
  // effect: 지금까지의 효과 판정 (ExperimentJudge)
  // extendedCount: 이 실험을 지금까지 연장한 횟수. 한 번도 안 했으면 0
  public static boolean canExtend(ExperimentEffect effect, int extendedCount) {
    return effect == ExperimentEffect.UNCLEAR && extendedCount < MAX_EXTEND_COUNT;
  }

  // 연장한 뒤의 실험 기간(일)을 돌려줍니다.
  //
  //   14일  →  21일
  public static int extendedDays(int days) {
    return days + EXTEND_DAYS;
  }
}
