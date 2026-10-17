package com.ithopeschool.projectbe.domain.care;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.call.CallScript;
import com.ithopeschool.projectbe.domain.care.CareGreeting.Greeting;
import com.ithopeschool.projectbe.domain.care.ToneGuide.Tone;
import com.ithopeschool.projectbe.domain.coach.CoachKind;
import com.ithopeschool.projectbe.domain.coach.CoachPrompt;
import com.ithopeschool.projectbe.domain.message.AppAlert;
import com.ithopeschool.projectbe.domain.message.AppAlertType;
import org.junit.jupiter.api.Test;

// 공통 말투 지침(이슈 #65)을 확인합니다.
//   - 관리 강도와 허용 수위로 말투를 고르는 규칙
//   - 금지 표현 검사가 깎아내리는 말은 잡고 평범한 말은 통과시키는지
//   - 통화 대본과 코치 지침이 금지 규칙을 빠짐없이 담는지
class ToneGuideTest {

  // ─── 오늘의 관리 강도를 아는 곳(통화)의 말투 ───

  @Test
  void toneFollowsTheCareLevel() {
    // 회복(0) → 위로 / 당근(1) → 칭찬 / 넛지(2) → 담백 / 채찍(3·4) → 솔직
    assertThat(ToneGuide.toneOf(0, 3, false)).isEqualTo(Tone.SUPPORT);
    assertThat(ToneGuide.toneOf(1, 3, false)).isEqualTo(Tone.PRAISE);
    assertThat(ToneGuide.toneOf(2, 3, false)).isEqualTo(Tone.PLAIN);
    assertThat(ToneGuide.toneOf(3, 3, false)).isEqualTo(Tone.FRANK);
    // 4(강제적 채찍)는 잠금 기능의 단계라 말투는 3 과 같습니다.
    assertThat(ToneGuide.toneOf(4, 3, false)).isEqualTo(Tone.FRANK);
  }

  @Test
  void lowHarshnessLimitTurnsFrankIntoPlain() {
    // 채찍 단계여도 독설 허용 수위가 2 이하면 담백한 말투까지만 씁니다.
    assertThat(ToneGuide.toneOf(3, 2, false)).isEqualTo(Tone.PLAIN);
    assertThat(ToneGuide.toneOf(4, 1, false)).isEqualTo(Tone.PLAIN);
    assertThat(ToneGuide.toneOf(3, 3, false)).isEqualTo(Tone.FRANK);
    assertThat(ToneGuide.toneOf(3, 5, false)).isEqualTo(Tone.FRANK);
  }

  @Test
  void harshnessLimitNeverMakesTheToneHarsher() {
    // 허용 수위가 5 여도 당근·넛지 단계의 말투가 세지지 않습니다. 한도이지 목표가 아닙니다.
    assertThat(ToneGuide.toneOf(1, 5, false)).isEqualTo(Tone.PRAISE);
    assertThat(ToneGuide.toneOf(2, 5, false)).isEqualTo(Tone.PLAIN);
    assertThat(ToneGuide.toneOf(0, 5, false)).isEqualTo(Tone.SUPPORT);
  }

  @Test
  void supportComesBeforeEverySetting() {
    // 위로가 먼저인 날(기분 저하 · 안전 브레이크)에는 어떤 단계·어떤 수위여도 위로입니다.
    for (int careLevel = 0; careLevel <= 4; careLevel++) {
      for (int maxHarshness = 1; maxHarshness <= 5; maxHarshness++) {
        assertThat(ToneGuide.toneOf(careLevel, maxHarshness, true))
            .as("관리 강도 %d, 허용 수위 %d", careLevel, maxHarshness)
            .isEqualTo(Tone.SUPPORT);
      }
    }
  }

  // ─── 한도만 아는 곳(코치 문단)의 말투 ───

  @Test
  void toneWithinLimit() {
    // 한도가 0(회복)이면 허용 수위와 상관없이 위로
    assertThat(ToneGuide.toneWithinLimit(0, 1)).isEqualTo(Tone.SUPPORT);
    assertThat(ToneGuide.toneWithinLimit(0, 5)).isEqualTo(Tone.SUPPORT);
    // 허용 수위 2 이하면 담백
    assertThat(ToneGuide.toneWithinLimit(1, 1)).isEqualTo(Tone.PLAIN);
    assertThat(ToneGuide.toneWithinLimit(4, 2)).isEqualTo(Tone.PLAIN);
    // 그 밖에는 솔직
    assertThat(ToneGuide.toneWithinLimit(1, 3)).isEqualTo(Tone.FRANK);
    assertThat(ToneGuide.toneWithinLimit(4, 5)).isEqualTo(Tone.FRANK);
  }

  // ─── 금지 표현 검사 ───

  @Test
  void bannedExpressionsAreCaught() {
    assertThat(ToneGuide.hasBannedExpression("완주율이 55% 라니 한심하다.")).isTrue();
    assertThat(ToneGuide.hasBannedExpression("그렇게 게을러서 되겠어?")).isTrue();
    assertThat(ToneGuide.hasBannedExpression("머리가 나쁘면 더 해야지.")).isTrue();
    // 남과 비교하는 말
    assertThat(ToneGuide.hasBannedExpression("남들은 벌써 3시간 했어.")).isTrue();
    assertThat(ToneGuide.hasBannedExpression("다른 애들은 다 하는데.")).isTrue();
  }

  @Test
  void ordinarySentencesPass() {
    // 숫자와 한 일(행동)만 말하는 문장은 아쉬운 점을 짚어도 걸리지 않습니다.
    assertThat(ToneGuide.hasBannedExpression("화요일은 40% 로 조금 낮았어.")).isFalse();
    assertThat(ToneGuide.hasBannedExpression("오늘 계획, 아직 시작 전이네.")).isFalse();
    assertThat(ToneGuide.hasBannedExpression("계획한 시각보다 평균 23분 늦게 시작했어.")).isFalse();
    assertThat(ToneGuide.hasBannedExpression("")).isFalse();
  }

  @Test
  void everyFixedSentenceInTheAppPassesTheCheck() {
    // AI 가 아니라 코드에 적어 둔 문장(인사말 · 앱 알림)도 같은 검사를 통과해야 합니다.
    // 새 문장을 추가했는데 여기서 실패하면, 그 문장에 깎아내리거나 비교하는 말이 들어 있는 것입니다.
    for (int careLevel = 0; careLevel <= 4; careLevel++) {
      for (Greeting greeting : CareGreeting.greetingsOf(careLevel)) {
        assertThat(ToneGuide.hasBannedExpression(greeting.text())).as(greeting.text()).isFalse();
      }
    }
    for (AppAlertType type : AppAlertType.values()) {
      for (AppAlert.Sentence sentence : AppAlert.sentencesOf(type)) {
        assertThat(ToneGuide.hasBannedExpression(sentence.text())).as(sentence.text()).isFalse();
      }
    }
  }

  // ─── 통화와 문장이 같은 지침을 쓰는지 ───

  @Test
  void everyAiInstructionCarriesTheNoPutDownRule() {
    // 통화 대본과 코치 지침(어제 한 줄 · 주간 · 월간)에, 어떤 단계·어떤 수위에서도 금지 규칙이 들어 있습니다.
    for (int careLevel = 0; careLevel <= 4; careLevel++) {
      for (int maxHarshness = 1; maxHarshness <= 5; maxHarshness++) {
        assertThat(CallScript.instructions(careLevel, maxHarshness, null))
            .as("통화: 관리 강도 %d, 허용 수위 %d", careLevel, maxHarshness)
            .contains(ToneGuide.NO_PUT_DOWN_RULE);
        for (CoachKind kind : CoachKind.values()) {
          assertThat(CoachPrompt.instructions(kind, careLevel, maxHarshness))
              .as("코치 %s: 한도 %d, 허용 수위 %d", kind, careLevel, maxHarshness)
              .contains(ToneGuide.NO_PUT_DOWN_RULE);
        }
      }
    }
  }

  @Test
  void theNoPutDownRuleItselfPassesTheCheck() {
    // 금지 규칙 문장은 "~하지 마" 라고 말할 뿐, 금지 표현을 담고 있지 않습니다.
    assertThat(ToneGuide.hasBannedExpression(ToneGuide.NO_PUT_DOWN_RULE)).isFalse();
  }
}
