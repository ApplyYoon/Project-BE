package com.ithopeschool.projectbe.domain.suggestion;

import java.util.List;

// 개선안(실험)을 얼마나 지켰는지 나타내는 "이행률" 과 판정입니다. (이슈 #53, 기획서 v2 4장 「이행 판정」)
// 이행과 효과를 따로 판정해야, 문제가 사람인지 전략인지 나눌 수 있습니다.
//
//   이행률(%) = 지킨 날 ÷ 실험 기간 전체 일수 × 100 (반올림)
//   70 이상 → 잘 시행 / 40 이상 70 미만 → 부분 시행 / 40 미만 → 못 시행
//
//   14일 중 11일 지킴   →  79%, 잘 시행    (11 ÷ 14 = 78.6%)
//   10일 중 7일 지킴    →  70%, 잘 시행    (70 은 "잘 시행" 에 포함)
//   10일 중 4일 지킴    →  40%, 부분 시행  (40 은 "부분 시행" 에 포함)
//   10일 중 3일 지킴    →  30%, 못 시행
//   기록이 하나도 없음   →  null           ("0%" 가 아니라 "아직 모름")
//
// FE 의 getSuggestionRate (src/lib/suggestionRate.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 판정이 어긋나지 않습니다.
//
// rate: 이행률. 0~100 사이의 정수 / grade: 판정
public record SuggestionRate(int rate, SuggestionGrade grade) {

  // 이 이행률(%) 이상이면 "잘 시행"
  public static final int GOOD_RATE = 70;

  // 이 이행률(%) 이상이고 GOOD_RATE 보다 작으면 "부분 시행". 이보다 작으면 "못 시행"
  public static final int PARTIAL_RATE = 40;

  // 이행률과 판정을 계산합니다. 계산할 수 없으면 null 을 돌려줍니다.
  //
  // checks: 날마다 개선안을 지켰는지 적은 기록. true 면 그날은 지킨 날입니다.
  //         "지켰어요?" 에 답한 날만 들어 있습니다. 안 적은 날은 지키지 못한 날로 셉니다.
  // days: 실험 기간의 전체 일수 (예: 14). 이행률의 "전체" 가 되는 숫자입니다.
  public static SuggestionRate of(List<Boolean> checks, int days) {
    // 기록이 없으면 "못 지켰다" 가 아니라 "아직 모른다" 입니다. 0% 가 아니라 null 을 돌려줍니다.
    if (checks.isEmpty()) {
      return null;
    }
    // 전체 일수가 0 이하면 나눌 수 없습니다.
    if (days <= 0) {
      return null;
    }

    int keptDays = 0;
    for (boolean isKept : checks) {
      if (isKept) {
        keptDays++;
      }
    }

    // FE 와 같은 순서(먼저 나누고 100 을 곱함)로 계산한 뒤 반올림합니다.
    // 순서를 바꾸면 소수 계산 오차 때문에 12.5% 같은 "딱 절반" 에서 FE 와 1% 어긋날 수 있습니다.
    long rounded = Math.round((double) keptDays / days * 100);
    // 기록이 전체 일수보다 많아도 100% 를 넘지 않게 합니다.
    int rate = (int) Math.min(100, rounded);

    return new SuggestionRate(rate, gradeOf(rate));
  }

  // 이행률(%)을 판정으로 바꿉니다. 반올림한 값(화면에 보이는 값)으로 정합니다.
  // "70%" 라고 보이는데 "부분 시행" 이면 이상하기 때문입니다.
  private static SuggestionGrade gradeOf(int rate) {
    if (rate >= GOOD_RATE) {
      return SuggestionGrade.GOOD;
    }
    if (rate >= PARTIAL_RATE) {
      return SuggestionGrade.PARTIAL;
    }
    return SuggestionGrade.POOR;
  }
}
