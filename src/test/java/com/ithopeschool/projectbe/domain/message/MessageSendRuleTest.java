package com.ithopeschool.projectbe.domain.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.safety.SafetyBrake;
import org.junit.jupiter.api.Test;

// "지금 이 메시지를 보내도 되는지" 규칙(이슈 #32)을 확인합니다.
// feExample 로 시작하는 6개는 FE 함수(canSendAiMessageNotification) 주석의 예시를 그대로 옮긴 것입니다.
class MessageSendRuleTest {

  // 자주 쓰는 안전 브레이크 상태들. 순서는 (stopWhip, recoveryMode, examPeriod, showHelp, quiet) 입니다.
  private static final SafetyBrake ALL_OFF = new SafetyBrake(false, false, false, false, false);
  private static final SafetyBrake QUIET = new SafetyBrake(false, false, false, false, true);
  private static final SafetyBrake STOP_WHIP = new SafetyBrake(true, false, false, false, false);
  private static final SafetyBrake RECOVERY = new SafetyBrake(false, true, false, false, false);
  private static final SafetyBrake EXAM_PERIOD = new SafetyBrake(false, false, true, false, false);

  // ─── FE 예시 ───

  @Test
  void feExample1NothingIsSentDuringQuietHours() {
    // FE 예1) { careLevel: 1, maxCareLevel: 4, brake: quiet 만 켜짐 } → false
    assertThat(MessageSendRule.canSend(1, QUIET, 4)).isFalse();
  }

  @Test
  void feExample2MessageOverTheLimitIsNotSent() {
    // FE 예2) { careLevel: 3, maxCareLevel: 2, brake: 모두 꺼짐 } → false
    assertThat(MessageSendRule.canSend(3, ALL_OFF, 2)).isFalse();
  }

  @Test
  void feExample3MessageAtTheLimitIsSent() {
    // FE 예3) { careLevel: 2, maxCareLevel: 2, brake: 모두 꺼짐 } → true (한도와 같으면 보냅니다)
    assertThat(MessageSendRule.canSend(2, ALL_OFF, 2)).isTrue();
  }

  @Test
  void feExample4WhipIsNotSentWhenCrisisIsDetected() {
    // FE 예4) { careLevel: 3, maxCareLevel: 4, brake: stopWhip 만 켜짐 } → false
    assertThat(MessageSendRule.canSend(3, STOP_WHIP, 4)).isFalse();
  }

  @Test
  void feExample5WhipIsNotSentInRecoveryMode() {
    // FE 예5) { careLevel: 3, maxCareLevel: 4, brake: recoveryMode 만 켜짐 } → false
    assertThat(MessageSendRule.canSend(3, RECOVERY, 4)).isFalse();
  }

  @Test
  void feExample6CarrotIsStillSentWhenTheBrakeIsOn() {
    // FE 예6) { careLevel: 1, maxCareLevel: 4, brake: stopWhip 만 켜짐 } → true
    assertThat(MessageSendRule.canSend(1, STOP_WHIP, 4)).isTrue();
  }

  // ─── 규칙마다 모든 입력으로 확인 ───

  @Test
  void quietHoursBlockEveryMessage() {
    // 규칙 1) 연락 금지 시간에는 단계·한도·브레이크가 무엇이든 보내지 않습니다.
    for (int careLevel = 0; careLevel <= 4; careLevel++) {
      for (int maxCareLevel = 0; maxCareLevel <= 4; maxCareLevel++) {
        for (boolean stopWhip : new boolean[] {false, true}) {
          for (boolean recoveryMode : new boolean[] {false, true}) {
            SafetyBrake brake = new SafetyBrake(stopWhip, recoveryMode, false, false, true);

            assertThat(MessageSendRule.canSend(careLevel, brake, maxCareLevel)).isFalse();
          }
        }
      }
    }
  }

  @Test
  void neverSendsAMessageHarsherThanTheUsersLimit() {
    // 규칙 2) 한도보다 센 메시지는 어떤 경우에도 보내지 않습니다.
    for (int maxCareLevel = 0; maxCareLevel <= 4; maxCareLevel++) {
      for (int careLevel = maxCareLevel + 1; careLevel <= 4; careLevel++) {
        assertThat(MessageSendRule.canSend(careLevel, ALL_OFF, maxCareLevel))
            .as("%d단계 메시지, 한도 %d", careLevel, maxCareLevel)
            .isFalse();
      }
    }
  }

  @Test
  void safetyBrakeBlocksWhipLevelsOnly() {
    // 규칙 3, 4) 안전 브레이크가 켜지거나 시험 기간이면 3·4단계는 막고, 0·1·2단계는 그대로 보냅니다. (한도는 4 로 넉넉하게)
    // 시험 기간은 관리 강도 상한이 2 라서 채찍만 막습니다. (이슈 #232, FE 예5-2)
    for (SafetyBrake brake : new SafetyBrake[] {STOP_WHIP, RECOVERY, EXAM_PERIOD}) {
      assertThat(MessageSendRule.canSend(0, brake, 4)).isTrue();
      assertThat(MessageSendRule.canSend(1, brake, 4)).isTrue();
      assertThat(MessageSendRule.canSend(2, brake, 4)).isTrue();
      assertThat(MessageSendRule.canSend(3, brake, 4)).isFalse();
      assertThat(MessageSendRule.canSend(4, brake, 4)).isFalse();
    }
  }

  @Test
  void everyLevelWithinTheLimitIsSentOnAnOrdinaryDay() {
    // 평범한 날(브레이크 꺼짐, 금지 시간 아님)에는 한도 안의 메시지를 모두 보냅니다. 채찍(3·4)도 한도가 허락하면 보냅니다.
    for (int maxCareLevel = 0; maxCareLevel <= 4; maxCareLevel++) {
      for (int careLevel = 0; careLevel <= maxCareLevel; careLevel++) {
        assertThat(MessageSendRule.canSend(careLevel, ALL_OFF, maxCareLevel))
            .as("%d단계 메시지, 한도 %d", careLevel, maxCareLevel)
            .isTrue();
      }
    }
  }

  @Test
  void showHelpAloneDoesNotBlockMessages() {
    // 도움 안내(showHelp)는 보낼지 말지에 쓰지 않습니다. stopWhip · recoveryMode · examPeriod · quiet 네 값만 봅니다.
    SafetyBrake onlyShowHelp = new SafetyBrake(false, false, false, true, false);

    assertThat(MessageSendRule.canSend(3, onlyShowHelp, 4)).isTrue();
  }

  @Test
  void worksWithAnAppAlertTone() {
    // 실제로 쓰는 흐름: 알림 말투를 정한 뒤(AppAlert.careLevelOf) 그 단계로 보내도 되는지 봅니다.
    // 브레이크가 켜지면 말투가 회복(0)으로 내려가므로, 금지 시간만 아니면 회복 말투로 보냅니다.
    int tone = AppAlert.careLevelOf(AppAlertType.PLAN_LATE, 4, true);

    assertThat(tone).isEqualTo(0);
    assertThat(MessageSendRule.canSend(tone, RECOVERY, 4)).isTrue();
  }
}
