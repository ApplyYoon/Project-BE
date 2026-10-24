package com.ithopeschool.projectbe.domain.cardday;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.card.CardPriority;
import com.ithopeschool.projectbe.domain.care.Stage4;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 미루기 한도 계산을 PostponeRule 주석의 예시 그대로 확인합니다. (이슈 #225)
// 한도: 반복 카드는 카드별 한 달 5회, 1회성 카드는 장당 2회, 절대 카드는 0회 (FE#400 과 같은 숫자)
// 관리 4단계면 어떤 카드도 미룰 수 없습니다. (이슈 #234)
class PostponeRuleTest {

  private static final boolean REPEAT = false;
  private static final boolean ONE_OFF = true;
  private static final boolean NOT_STAGE4 = false;
  private static final boolean STAGE4 = true;

  @Test
  @DisplayName("반복 카드는 그달에 미룬 횟수만 센다: 이번 달 3번 → 2번 남음, 5번 → 0")
  void repeatCard() {
    assertThat(PostponeRule.postponesLeft(CardPriority.FLEXIBLE, REPEAT, 3, 9, NOT_STAGE4))
        .isEqualTo(2);
    assertThat(PostponeRule.postponesLeft(CardPriority.FLEXIBLE, REPEAT, 5, 5, NOT_STAGE4))
        .isZero();
    assertThat(PostponeRule.postponesLeft(CardPriority.FLEXIBLE, REPEAT, 0, 30, NOT_STAGE4))
        .isEqualTo(5);
    assertThat(PostponeRule.blockReason(CardPriority.FLEXIBLE, REPEAT, 4, 4, NOT_STAGE4)).isNull();
    assertThat(PostponeRule.blockReason(CardPriority.FLEXIBLE, REPEAT, 5, 5, NOT_STAGE4))
        .isEqualTo("이번 달에는 이 카드를 더 미룰 수 없어요 (반복 카드는 한 달에 5번까지)");
  }

  @Test
  @DisplayName("1회성 카드는 지금까지 미룬 횟수를 센다: 1번 → 1번 남음, 지난달 2번 → 0")
  void oneOffCard() {
    assertThat(PostponeRule.postponesLeft(CardPriority.FLEXIBLE, ONE_OFF, 1, 1, NOT_STAGE4))
        .isEqualTo(1);
    assertThat(PostponeRule.postponesLeft(CardPriority.FLEXIBLE, ONE_OFF, 0, 2, NOT_STAGE4))
        .isZero();
    assertThat(PostponeRule.blockReason(CardPriority.FLEXIBLE, ONE_OFF, 0, 2, NOT_STAGE4))
        .isEqualTo("이 카드는 더 미룰 수 없어요 (1회성 카드는 2번까지)");
  }

  @Test
  @DisplayName("절대 카드는 한 번도 미룰 수 없다")
  void absoluteCard() {
    assertThat(PostponeRule.postponesLeft(CardPriority.ABSOLUTE, REPEAT, 0, 0, NOT_STAGE4))
        .isZero();
    assertThat(PostponeRule.postponesLeft(CardPriority.ABSOLUTE, ONE_OFF, 0, 0, NOT_STAGE4))
        .isZero();
    assertThat(PostponeRule.blockReason(CardPriority.ABSOLUTE, ONE_OFF, 0, 0, NOT_STAGE4))
        .isEqualTo("절대 카드는 미룰 수 없어요");
  }

  @Test
  @DisplayName("한도를 넘게 센 값이 와도 음수가 아니라 0 이다")
  void neverNegative() {
    assertThat(PostponeRule.postponesLeft(CardPriority.FLEXIBLE, REPEAT, 9, 9, NOT_STAGE4))
        .isZero();
    assertThat(PostponeRule.postponesLeft(CardPriority.FLEXIBLE, ONE_OFF, 9, 9, NOT_STAGE4))
        .isZero();
  }

  @Test
  @DisplayName("관리 4단계면 한도가 남아 있어도 미루기가 잠긴다 (절대 카드는 원래 이유 그대로)")
  void stage4Locked() {
    assertThat(PostponeRule.postponesLeft(CardPriority.FLEXIBLE, REPEAT, 0, 0, STAGE4)).isZero();
    assertThat(PostponeRule.postponesLeft(CardPriority.FLEXIBLE, ONE_OFF, 0, 0, STAGE4)).isZero();
    assertThat(PostponeRule.blockReason(CardPriority.FLEXIBLE, REPEAT, 0, 0, STAGE4))
        .isEqualTo(Stage4.POSTPONE_LOCK_REASON);
    assertThat(PostponeRule.blockReason(CardPriority.ABSOLUTE, REPEAT, 0, 0, STAGE4))
        .isEqualTo("절대 카드는 미룰 수 없어요");
  }
}
