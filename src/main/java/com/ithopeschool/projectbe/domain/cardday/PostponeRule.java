package com.ithopeschool.projectbe.domain.cardday;

import com.ithopeschool.projectbe.domain.card.CardPriority;

// 미루기 한도 계산기입니다. (최종 기획서 「4.4 타이머」 미루기와 미실행, 이슈 #225)
// 값만 계산하는 순수 함수라서 DB 를 모릅니다. 몇 번 미뤘는지는 Service 가 세서 넘깁니다.
//
//   한도: 반복 카드는 카드별 "한 달에 5회", 1회성 카드는 "장당 2회", 절대 카드는 미루기가 없습니다(0회).
//
//   postponesLeft(FLEXIBLE, 반복,   이번 달 3번, 전체 9번)  → 2   (5 − 3)
//   postponesLeft(FLEXIBLE, 반복,   이번 달 5번, 전체 5번)  → 0   (다 씀)
//   postponesLeft(FLEXIBLE, 1회성, 이번 달 1번, 전체 1번)  → 1   (2 − 1)
//   postponesLeft(FLEXIBLE, 1회성, 이번 달 0번, 전체 2번)  → 0   (지난달에 2번 미룬 1회성 카드도 끝)
//   postponesLeft(ABSOLUTE, 아무거나)                     → 0
//
// ⚠️ "관리 4단계에서는 미루기가 잠긴다" 는 아직 넣지 않았습니다. 지금 관리 단계가 서버에 저장되지 않기 때문입니다.
// 숫자는 출발값입니다. 바꿀 때는 FE(#400)의 상수와 같이 고칩니다.
public final class PostponeRule {

  // 반복 카드 한 장을 한 달(달력의 1일 ~ 말일)에 미룰 수 있는 횟수
  public static final int REPEAT_CARD_MONTHLY_LIMIT = 5;
  // 1회성 카드 한 장을 미룰 수 있는 횟수 (기간 상관없이)
  public static final int ONE_OFF_CARD_LIMIT = 2;
  // 미루는 이유 + 어떻게 할지를 적어야 하는 최소 글자 수 (앞뒤 공백을 뺀 길이)
  public static final int MIN_REASON_LENGTH = 100;

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private PostponeRule() {}

  // 앞으로 더 미룰 수 있는 횟수를 돌려줍니다. (0 이면 미룰 수 없음)
  // usedThisMonth: 미루려는 날짜가 있는 달에 이 카드를 미룬 횟수 (반복 카드에 씀)
  // usedEver: 이 카드를 지금까지 미룬 횟수 (1회성 카드에 씀)
  public static int postponesLeft(
      CardPriority priority, boolean isOneOff, long usedThisMonth, long usedEver) {
    if (priority == CardPriority.ABSOLUTE) {
      return 0;
    }
    long left =
        isOneOff ? ONE_OFF_CARD_LIMIT - usedEver : REPEAT_CARD_MONTHLY_LIMIT - usedThisMonth;
    return (int) Math.max(0, left);
  }

  // 미룰 수 없으면 왜 안 되는지 한 줄을, 미룰 수 있으면 null 을 돌려줍니다. (FE 가 그대로 보여 줄 문장)
  public static String blockReason(
      CardPriority priority, boolean isOneOff, long usedThisMonth, long usedEver) {
    if (priority == CardPriority.ABSOLUTE) {
      return "절대 카드는 미룰 수 없어요";
    }
    if (postponesLeft(priority, isOneOff, usedThisMonth, usedEver) > 0) {
      return null;
    }
    return isOneOff
        ? "이 카드는 더 미룰 수 없어요 (1회성 카드는 " + ONE_OFF_CARD_LIMIT + "번까지)"
        : "이번 달에는 이 카드를 더 미룰 수 없어요 (반복 카드는 한 달에 " + REPEAT_CARD_MONTHLY_LIMIT + "번까지)";
  }
}
