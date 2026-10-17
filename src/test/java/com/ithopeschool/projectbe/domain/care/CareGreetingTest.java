package com.ithopeschool.projectbe.domain.care;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ithopeschool.projectbe.domain.care.CareGreeting.Greeting;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

// 관리 강도 단계별 인사말 규칙(이슈 #65)을 확인합니다.
// 문장과 고르는 규칙은 FE 의 careGreetings · pickGreeting (src/lib/careTheme.ts) 과 같습니다.
class CareGreetingTest {

  // 그 단계·허용 수위에서 나올 수 있는 인사말을 모두 모읍니다. (random 을 0 부터 조금씩 올려 가며 골라 봅니다)
  private static Set<String> allPickable(int careLevel, int maxHarshness) {
    Set<String> texts = new HashSet<>();
    for (int i = 0; i < 100; i++) {
      texts.add(CareGreeting.pick(careLevel, maxHarshness, i / 100.0));
    }
    return texts;
  }

  // ─── 허용 수위 안에서만 고르기 ───

  @Test
  void whipGreetingsFollowTheAllowedHarshness() {
    // 3단계(심리적 채찍): 허용 수위 1 이면 걱정하는 문장 하나만 나옵니다.
    assertThat(allPickable(3, 1)).containsExactly("요즘 계속 미뤄지는 것 같아서 걱정돼.");
    // 허용 수위 2 면 걱정·담백·마무리 세 문장 중 하나입니다. 팩폭(수위 3)은 나오지 않습니다.
    assertThat(allPickable(3, 2))
        .containsExactlyInAnyOrder(
            "요즘 계속 미뤄지는 것 같아서 걱정돼.", "오늘 계획, 아직 시작 전이네.", "독촉이 안 통하는 것 같으니 당분간 연락 안 할게.");
    // 허용 수위 3 이면 다섯 문장이 모두 나올 수 있습니다.
    assertThat(allPickable(3, 3)).hasSize(5);
  }

  @Test
  void neverPicksAGreetingHarsherThanAllowed() {
    // 어느 단계·어느 허용 수위(1 ~ 5)에서도, 고른 인사말의 수위는 허용 수위를 넘지 않습니다.
    for (int careLevel = 0; careLevel <= 4; careLevel++) {
      for (int maxHarshness = 1; maxHarshness <= 5; maxHarshness++) {
        for (String text : allPickable(careLevel, maxHarshness)) {
          int harshness = harshnessOf(careLevel, text);

          assertThat(harshness)
              .as("%d단계, 허용 수위 %d: %s", careLevel, maxHarshness, text)
              .isLessThanOrEqualTo(maxHarshness);
        }
      }
    }
  }

  // 그 단계의 목록에서 문장을 찾아 수위를 돌려줍니다.
  private static int harshnessOf(int careLevel, String text) {
    for (Greeting greeting : CareGreeting.greetingsOf(careLevel)) {
      if (greeting.text().equals(text)) {
        return greeting.harshness();
      }
    }
    throw new AssertionError("목록에 없는 인사말: " + text);
  }

  @Test
  void fallsBackToTheMildestWhenNothingIsAllowed() {
    // 허용 수위가 0 처럼 너무 낮아서 맞는 문구가 없으면, 그 단계에서 가장 순한 문구 중에서 고릅니다.
    assertThat(allPickable(3, 0)).containsExactly("요즘 계속 미뤄지는 것 같아서 걱정돼.");
    assertThat(allPickable(0, 0)).hasSize(3);
  }

  @Test
  void raisingTheLimitToFiveStillGivesAtMostLevelThreeSentences() {
    // 아직 수위 4 ~ 5 문구는 없습니다. 허용 수위를 5 로 올려도 3 일 때와 나오는 문구가 같습니다.
    assertThat(allPickable(3, 5)).isEqualTo(allPickable(3, 3));
  }

  // ─── 단계별 말투 ───

  @Test
  void recoveryCarrotAndNudgeGreetingsAreAllGentle() {
    // 회복(0)·당근(1)·넛지(2) 단계의 인사말은 모두 수위 1(부드럽게)입니다. 허용 수위가 1 이어도 전부 나올 수 있습니다.
    for (int careLevel = 0; careLevel <= 2; careLevel++) {
      assertThat(CareGreeting.greetingsOf(careLevel))
          .hasSize(3)
          .allSatisfy(greeting -> assertThat(greeting.harshness()).isEqualTo(1));
      assertThat(allPickable(careLevel, 1)).hasSize(3);
    }
  }

  @Test
  void everyLevelHasAGentleGreeting() {
    // 어느 단계든 수위 1 문구가 하나 이상 있어야 합니다. 허용 수위를 1 로 정한 사람에게도 건넬 말이 필요합니다.
    for (int careLevel = 0; careLevel <= 4; careLevel++) {
      assertThat(CareGreeting.greetingsOf(careLevel))
          .as("%d단계", careLevel)
          .anySatisfy(greeting -> assertThat(greeting.harshness()).isEqualTo(1));
    }
  }

  @Test
  void harshnessIsBetweenOneAndThree() {
    // 정해 둔 문구의 수위는 1 ~ 3 안입니다. (4 ~ 5 문구를 넣게 되면 이 테스트와 주석을 같이 고칩니다)
    for (int careLevel = 0; careLevel <= 4; careLevel++) {
      assertThat(CareGreeting.greetingsOf(careLevel))
          .allSatisfy(greeting -> assertThat(greeting.harshness()).isBetween(1, 3));
    }
  }

  @Test
  void levelFourUsesTheSameGreetingsAsLevelThree() {
    // 4단계(강제적 채찍)는 말투가 3단계와 같아서 같은 인사말을 씁니다. (FE 에는 4단계 인사말이 없습니다)
    assertThat(CareGreeting.greetingsOf(4)).isEqualTo(CareGreeting.greetingsOf(3));
    assertThat(allPickable(4, 1)).isEqualTo(allPickable(3, 1));
  }

  // ─── 고르기 ───

  @Test
  void randomDecidesWhichGreetingIsPicked() {
    // random 이 0 이면 첫 번째 문구입니다. 남은 문구가 3개면 3분의 1 마다 다음 문구로 넘어갑니다.
    assertThat(CareGreeting.pick(0, 3, 0)).isEqualTo("오늘은 10분만 해볼까?");
    assertThat(CareGreeting.pick(0, 3, 0.34)).isEqualTo("괜찮아, 천천히 시작하자.");
    assertThat(CareGreeting.pick(0, 3, 0.99)).isEqualTo("오늘은 가볍게 한 가지만 해보자.");
  }

  @Test
  void pickWithoutRandomGivesOneOfTheGreetings() {
    // random 을 넘기지 않으면 아무거나 나오지만, 언제나 그 단계에 정해 둔 문구 중 하나입니다.
    String text = CareGreeting.pick(1, CareGreeting.DEFAULT_MAX_HARSHNESS);

    assertThat(CareGreeting.greetingsOf(1)).extracting(Greeting::text).contains(text);
  }

  @Test
  void rejectsWrongInputs() {
    // 관리 강도가 0 ~ 4 밖이거나 random 이 0 이상 1 미만이 아니면 에러를 냅니다.
    assertThatThrownBy(() -> CareGreeting.pick(5, 3, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CareGreeting.pick(-1, 3, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CareGreeting.pick(1, 3, 1.0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void greetingListCannotBeChangedFromOutside() {
    // 정해 둔 문구 목록은 밖에서 고칠 수 없습니다.
    List<Greeting> greetings = CareGreeting.greetingsOf(3);

    assertThatThrownBy(() -> greetings.add(new Greeting("아무 말", 5)))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
