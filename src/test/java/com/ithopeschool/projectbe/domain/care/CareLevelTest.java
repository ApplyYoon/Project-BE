package com.ithopeschool.projectbe.domain.care;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

// "관리 강도 0~4단계 계산" 규칙(이슈 #62)을 확인합니다.
// feExample 로 시작하는 테스트는 FE 함수(getCareLevel) 주석의 예시를 그대로 옮긴 것이고,
// answerTable 로 시작하는 테스트는 같은 주석의 정답표(입력을 전부 넣어 본 표)입니다.
class CareLevelTest {

  private static final boolean BRAKE_ON = true;
  private static final boolean BRAKE_OFF = false;

  // 당근 3종. 셋은 항상 같은 결과가 나옵니다.
  private static final CareAction[] CARROTS = {
    CareAction.CARROT_RAISE, CareAction.CARROT_CHANGE_STRATEGY, CareAction.CARROT_KEEP
  };

  // ---------- 1) 안전 브레이크가 가장 먼저 ----------

  @Test
  void feExampleSafetyBrakeIsAlwaysRecovery() {
    assertThat(CareLevel.of(3, CareAction.WHIP, 4, BRAKE_ON)).isEqualTo(0);
    assertThat(CareLevel.of(4, CareAction.KEEP_EASIER, 4, BRAKE_ON)).isEqualTo(0);
    assertThat(CareLevel.of(0, CareAction.WHIP, 4, BRAKE_ON)).isEqualTo(0);
  }

  @Test
  void answerTableSafetyBrakeIsZeroForEveryInput() {
    // 브레이크가 켜지면 입력 175가지(어제 5 × 대응 7 × 한도 5) 전부 0 입니다.
    for (int yesterday = 0; yesterday <= 4; yesterday++) {
      for (CareAction action : CareAction.values()) {
        for (int max = 0; max <= 4; max++) {
          assertThat(CareLevel.of(yesterday, action, max, BRAKE_ON))
              .as("[어제 %d, %s, 한도 %d, 브레이크 켜짐]", yesterday, action, max)
              .isEqualTo(0);
        }
      }
    }
  }

  // ---------- 2) 회복 우선이면 0 ----------

  @Test
  void feExampleRecoveryFirstIsZero() {
    assertThat(CareLevel.of(3, CareAction.RECOVERY_FIRST, 4, BRAKE_OFF)).isEqualTo(0);
    assertThat(CareLevel.of(4, CareAction.RECOVERY_FIRST, 4, BRAKE_OFF)).isEqualTo(0);
  }

  @Test
  void answerTableRecoveryFirstIsZeroForEveryInput() {
    // 어제 단계·한도가 무엇이든 0 입니다.
    for (int yesterday = 0; yesterday <= 4; yesterday++) {
      for (int max = 0; max <= 4; max++) {
        assertThat(CareLevel.of(yesterday, CareAction.RECOVERY_FIRST, max, BRAKE_OFF))
            .as("[어제 %d, 회복 우선, 한도 %d]", yesterday, max)
            .isEqualTo(0);
      }
    }
  }

  // ---------- 3) 대응별로 움직이는 칸 수 ----------

  @Test
  void feExampleWhipGoesUpOnlyOneStep() {
    // 하루에 두 단계 이상은 올라가지 않습니다.
    assertThat(CareLevel.of(2, CareAction.WHIP, 4, BRAKE_OFF)).isEqualTo(3);
    // 한도가 높아도 한 칸만
    assertThat(CareLevel.of(0, CareAction.WHIP, 4, BRAKE_OFF)).isEqualTo(1);
    // 이미 맨 위
    assertThat(CareLevel.of(4, CareAction.WHIP, 4, BRAKE_OFF)).isEqualTo(4);
  }

  @Test
  void feExampleCarrotGoesDownTwoSteps() {
    // 성공하면 바로 내려갑니다. 두 칸 내림, 최소 0.
    assertThat(CareLevel.of(4, CareAction.CARROT_RAISE, 4, BRAKE_OFF)).isEqualTo(2);
    assertThat(CareLevel.of(3, CareAction.CARROT_KEEP, 4, BRAKE_OFF)).isEqualTo(1);
    // 정확히 0 에 닿습니다.
    assertThat(CareLevel.of(2, CareAction.CARROT_CHANGE_STRATEGY, 4, BRAKE_OFF)).isEqualTo(0);
    // 한 칸만 남아도 0 에서 멈춥니다.
    assertThat(CareLevel.of(1, CareAction.CARROT_RAISE, 4, BRAKE_OFF)).isEqualTo(0);
  }

  @Test
  void feExampleGoalDownGoesDownOneStep() {
    assertThat(CareLevel.of(4, CareAction.GOAL_DOWN, 4, BRAKE_OFF)).isEqualTo(3);
    assertThat(CareLevel.of(1, CareAction.GOAL_DOWN, 4, BRAKE_OFF)).isEqualTo(0);
    assertThat(CareLevel.of(0, CareAction.GOAL_DOWN, 4, BRAKE_OFF)).isEqualTo(0);
  }

  @Test
  void feExampleKeepEasierStaysTheSame() {
    assertThat(CareLevel.of(2, CareAction.KEEP_EASIER, 4, BRAKE_OFF)).isEqualTo(2);
    // 한도가 높아도 저절로 올라가지 않습니다.
    assertThat(CareLevel.of(1, CareAction.KEEP_EASIER, 4, BRAKE_OFF)).isEqualTo(1);
  }

  // ---------- 4) 최대 한도를 넘지 않음 ----------

  @Test
  void feExampleMaxLevelIsNeverExceeded() {
    // 3 이 되지만 한도 때문에 2
    assertThat(CareLevel.of(2, CareAction.WHIP, 2, BRAKE_OFF)).isEqualTo(2);
    // 한도를 낮춘 날은 바로 한도로 내려갑니다.
    assertThat(CareLevel.of(4, CareAction.KEEP_EASIER, 1, BRAKE_OFF)).isEqualTo(1);
    // 어제가 한도보다 높아도 한도가 이깁니다.
    assertThat(CareLevel.of(4, CareAction.WHIP, 2, BRAKE_OFF)).isEqualTo(2);
    // 4 − 2 = 2 지만 한도 1 로 잘립니다.
    assertThat(CareLevel.of(4, CareAction.CARROT_RAISE, 1, BRAKE_OFF)).isEqualTo(1);
    // 한도 0 이면 항상 회복
    assertThat(CareLevel.of(0, CareAction.WHIP, 0, BRAKE_OFF)).isEqualTo(0);
    assertThat(CareLevel.of(3, CareAction.CARROT_KEEP, 0, BRAKE_OFF)).isEqualTo(0);
  }

  // ---------- 정답표: 브레이크가 꺼졌을 때의 모든 입력 ----------
  // 행 = 어제 단계 0~4, 열 = 최대 한도 0~4, 값 = 오늘 단계

  @Test
  void answerTableWhip() {
    int[][] expected = {
      {0, 1, 1, 1, 1},
      {0, 1, 2, 2, 2},
      {0, 1, 2, 3, 3},
      {0, 1, 2, 3, 4},
      {0, 1, 2, 3, 4},
    };
    assertTable(CareAction.WHIP, expected);
  }

  @Test
  void answerTableCarrots() {
    int[][] expected = {
      {0, 0, 0, 0, 0},
      {0, 0, 0, 0, 0},
      {0, 0, 0, 0, 0},
      {0, 1, 1, 1, 1},
      {0, 1, 2, 2, 2},
    };
    for (CareAction carrot : CARROTS) {
      assertTable(carrot, expected);
    }
  }

  @Test
  void answerTableGoalDown() {
    int[][] expected = {
      {0, 0, 0, 0, 0},
      {0, 0, 0, 0, 0},
      {0, 1, 1, 1, 1},
      {0, 1, 2, 2, 2},
      {0, 1, 2, 3, 3},
    };
    assertTable(CareAction.GOAL_DOWN, expected);
  }

  @Test
  void answerTableKeepEasier() {
    int[][] expected = {
      {0, 0, 0, 0, 0},
      {0, 1, 1, 1, 1},
      {0, 1, 2, 2, 2},
      {0, 1, 2, 3, 3},
      {0, 1, 2, 3, 4},
    };
    assertTable(CareAction.KEEP_EASIER, expected);
  }

  // 표의 25칸(어제 5 × 한도 5)을 전부 확인합니다.
  private static void assertTable(CareAction action, int[][] expected) {
    for (int yesterday = 0; yesterday <= 4; yesterday++) {
      for (int max = 0; max <= 4; max++) {
        assertThat(CareLevel.of(yesterday, action, max, BRAKE_OFF))
            .as("[어제 %d, %s, 한도 %d, 브레이크 꺼짐]", yesterday, action, max)
            .isEqualTo(expected[yesterday][max]);
      }
    }
  }

  // ---------- 규칙을 한 줄씩 다시 확인 ----------

  @Test
  void levelNeverGoesUpMoreThanOneStepInADay() {
    // 어떤 입력에서도 오늘 단계는 어제보다 한 칸 넘게 오르지 않습니다.
    for (int yesterday = 0; yesterday <= 4; yesterday++) {
      for (CareAction action : CareAction.values()) {
        for (int max = 0; max <= 4; max++) {
          assertThat(CareLevel.of(yesterday, action, max, BRAKE_OFF))
              .as("[어제 %d, %s, 한도 %d]", yesterday, action, max)
              .isLessThanOrEqualTo(yesterday + 1);
        }
      }
    }
  }

  @Test
  void levelNeverExceedsMaxLevel() {
    // 어떤 입력에서도 오늘 단계는 사용자가 정한 한도를 넘지 않습니다.
    for (int yesterday = 0; yesterday <= 4; yesterday++) {
      for (CareAction action : CareAction.values()) {
        for (int max = 0; max <= 4; max++) {
          assertThat(CareLevel.of(yesterday, action, max, BRAKE_OFF))
              .as("[어제 %d, %s, 한도 %d]", yesterday, action, max)
              .isBetween(0, max);
        }
      }
    }
  }

  // ---------- 처음 쓰는 날 ----------

  @Test
  void firstDayStartsAtCarrot() {
    // 어제 단계가 없는 날은 1단계(당근)에서 시작합니다.
    assertThat(CareLevel.firstDay(4, BRAKE_OFF)).isEqualTo(1);
    assertThat(CareLevel.firstDay(1, BRAKE_OFF)).isEqualTo(1);
  }

  @Test
  void firstDayKeepsMaxLevelAndSafetyBrake() {
    // 한도 0 이면 처음부터 회복입니다.
    assertThat(CareLevel.firstDay(0, BRAKE_OFF)).isEqualTo(0);
    // 브레이크가 켜졌으면 처음 쓰는 날도 회복입니다.
    assertThat(CareLevel.firstDay(4, BRAKE_ON)).isEqualTo(0);
  }

  // ---------- 잘못된 입력 ----------

  @Test
  void levelOutOfRangeIsRejected() {
    // 0~4 밖의 값은 조용히 고치지 않고 에러를 냅니다.
    assertThatThrownBy(() -> CareLevel.of(5, CareAction.WHIP, 4, BRAKE_OFF))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("어제 단계");
    assertThatThrownBy(() -> CareLevel.of(-1, CareAction.WHIP, 4, BRAKE_OFF))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CareLevel.of(2, CareAction.WHIP, 7, BRAKE_OFF))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("최대 한도");
    assertThatThrownBy(() -> CareLevel.firstDay(-2, BRAKE_OFF))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void missingActionIsRejected() {
    assertThatThrownBy(() -> CareLevel.of(2, null, 4, BRAKE_OFF))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
