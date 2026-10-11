package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.call.dto.CallSessionRequest.Card;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 스프링을 띄우지 않는 단위 테스트입니다. 학생의 기록(학생 카드)에 따라 통화의 질문과 말투가 달라지는지 봅니다.
class CallScriptTest {

  // 테스트마다 필요한 값만 바꿔 쓰려고, 카드를 만드는 도우미를 둡니다. (나머지는 "보통인 하루")
  private static Card card(
      Integer yesterdayStudy, Integer yesterdayPlan, String missReason, List<Integer> moods) {
    return new Card(
        yesterdayStudy,
        yesterdayPlan,
        missReason,
        3,
        180,
        "19:00",
        64,
        55,
        10,
        moods,
        4,
        null,
        null,
        null);
  }

  private static final Card ORDINARY = card(190, 240, "phone", List.of(3, 3, 4));

  // ---- 말투 ----

  @Test
  @DisplayName("관리 강도에 따라 말투가 달라진다 (회복·당근·넛지·채찍)")
  void toneFollowsCareLevel() {
    assertThat(CallScript.toneRule(0, 3, ORDINARY)).contains("위로하고 응원하는 말만");
    assertThat(CallScript.toneRule(1, 3, ORDINARY)).contains("구체적으로 칭찬");
    assertThat(CallScript.toneRule(2, 3, ORDINARY)).contains("담백하게 사실만");
    assertThat(CallScript.toneRule(3, 3, ORDINARY)).contains("돌려 말하지 말고 짚어");
    // 4단계(강제적 채찍)는 잠금 기능의 단계라, 말투는 3단계와 같습니다.
    assertThat(CallScript.toneRule(4, 3, ORDINARY)).isEqualTo(CallScript.toneRule(3, 3, ORDINARY));
  }

  @Test
  @DisplayName("채찍 단계여도 독설 허용 수위가 2 이하면 담백한 말투로 낮춘다")
  void harshnessLimitsTheWhip() {
    assertThat(CallScript.toneRule(3, 2, ORDINARY)).contains("담백하게 사실만");
    assertThat(CallScript.toneRule(3, 1, ORDINARY)).contains("꾸짖는 말은 쓰지 마");
    // 수위 3 이상이면 그 수위까지만 세게 말합니다.
    assertThat(CallScript.toneRule(3, 4, ORDINARY)).contains("5단계 중 4단계까지만");
  }

  @Test
  @DisplayName("가장 센 설정이어도 사람을 깎아내리지 않는다")
  void strongestToneStillHasLimits() {
    String tone = CallScript.toneRule(4, 5, ORDINARY);

    assertThat(tone).contains("사람을 깎아내리지 말고");
    assertThat(tone).contains("비꼬거나 겁주지 마");
  }

  @Test
  @DisplayName("가장 최근 기분이 처짐 이하면, 관리 강도가 채찍이어도 위로하는 말투로 바꾼다")
  void lowMoodAlwaysMeansSupportiveTone() {
    Card lowMood = card(190, 240, "phone", List.of(4, 3, 2));

    assertThat(CallScript.isMoodLow(lowMood)).isTrue();
    assertThat(CallScript.toneRule(3, 5, lowMood)).contains("위로하고 응원하는 말만");
    assertThat(CallScript.toneRule(1, 3, lowMood)).contains("지적하지 마");
  }

  @Test
  @DisplayName("위로하는 통화(기분이 낮거나 관리 강도 0)는 순서도 바뀐다: 못 한 것을 묻지 않고 기분부터 묻는다")
  void supportiveCallDoesNotStartWithWhatWentWrong() {
    Card missedAndLow = card(20, 180, "tired", List.of(3, 2, 1));

    String lowMood = CallScript.instructions(3, 5, missedAndLow);
    String recovery = CallScript.instructions(0, 3, card(20, 180, "tired", List.of(3)));

    for (String instructions : new String[] {lowMood, recovery}) {
      assertThat(instructions).contains("1. 짧게 인사하고, 요즘 기분이 어떤지 먼저 물어봐.");
      assertThat(instructions).contains("어제 못 한 것을 먼저 꺼내지 마");
      assertThat(instructions).contains("10분만 해도 된다고");
      // 평소 통화의 "어제는 계획의 몇 %" 로 시작하는 순서와 이유를 캐묻는 질문은 들어가지 않습니다.
      assertThat(instructions).doesNotContain("어제 한 것을 한 문장으로 말해 줘");
      assertThat(instructions).doesNotContain("몇 시에 잤는지");
      // 약속은 그대로 같이 정합니다.
      assertThat(instructions).contains("4. 오늘의 약속을 같이 정해.");
    }
    // 같은 기록이어도 기분이 보통이고 관리 강도가 1 이상이면 평소 순서입니다.
    assertThat(CallScript.instructions(3, 5, card(20, 180, "tired", List.of(3))))
        .contains("어제 한 것을 한 문장으로 말해 줘", "몇 시에 잤는지");
  }

  @Test
  @DisplayName("기분은 가장 최근 것만 본다. 예전에 낮았어도 지금 보통이면 낮지 않다")
  void onlyTheLatestMoodCounts() {
    assertThat(CallScript.isMoodLow(card(190, 240, null, List.of(1, 2, 3)))).isFalse();
    assertThat(CallScript.isMoodLow(card(190, 240, null, List.of(5, 5, 1)))).isTrue();
    assertThat(CallScript.isMoodLow(card(190, 240, null, List.of()))).isFalse();
    assertThat(CallScript.isMoodLow(card(190, 240, null, null))).isFalse();
    assertThat(CallScript.isMoodLow(null)).isFalse();
  }

  // ---- 질문 ----

  @Test
  @DisplayName("어제 계획을 다 채웠으면 무엇이 도움이 됐는지 묻는다")
  void asksWhatHelpedWhenPlanWasMet() {
    assertThat(CallScript.yesterdayStep(card(240, 240, null, null))).contains("무엇이 도움이 됐는지");
    // 계획보다 더 해도 100% 로 봅니다.
    assertThat(CallScript.yesterdayStep(card(300, 240, null, null))).contains("다 채웠어");
  }

  @Test
  @DisplayName("어제 계획을 못 채웠으면, 학생이 고른 이유에 맞는 질문을 한다")
  void asksAboutTheChosenReason() {
    assertThat(CallScript.yesterdayStep(card(100, 240, "phone", null)))
        .contains("'폰·SNS'", "폰을 어디에 두면");
    assertThat(CallScript.yesterdayStep(card(100, 240, "tired", null)))
        .contains("'피곤'", "몇 시에 잤는지");
    assertThat(CallScript.yesterdayStep(card(100, 240, "tooMuch", null)))
        .contains("'계획 과다'", "무엇을 줄일지");
    assertThat(CallScript.yesterdayStep(card(100, 240, "sick", null))).contains("'아픔'", "쉬어도 된다고");
    assertThat(CallScript.yesterdayStep(card(100, 240, "unwilling", null))).contains("5분만");
    assertThat(CallScript.yesterdayStep(card(100, 240, "event", null))).contains("약속이 있는 날");
    assertThat(CallScript.yesterdayStep(card(100, 240, "etc", null))).contains("'기타'", "한 가지만");
  }

  @Test
  @DisplayName("못 채웠는데 이유를 고르지 않았으면 무엇이 걸렸는지 묻는다")
  void asksWhyWhenNoReasonWasChosen() {
    assertThat(CallScript.yesterdayStep(card(100, 240, null, null))).contains("무엇이 가장 걸렸는지");
  }

  @Test
  @DisplayName("어제 계획이 없었거나 기록이 없으면 꾸짖지 않고 요즘 어떤지 묻는다")
  void doesNotScoldWhenThereWasNoPlan() {
    String noPlan = CallScript.yesterdayStep(card(60, 0, null, null));
    String noRecord = CallScript.yesterdayStep(card(null, null, null, null));

    assertThat(noPlan).contains("꾸짖지 말고");
    assertThat(noRecord).isEqualTo(noPlan);
    assertThat(CallScript.yesterdayStep(null)).isEqualTo(noPlan);
  }

  @Test
  @DisplayName("오늘 계획이 없으면 하나를 같이 정하고, 있으면 얼마나 할지 묻는다")
  void todayStepDependsOnTodayPlans() {
    Card noPlanToday = new Card(190, 240, null, 0, 0, null, 64, 55, 10, null, 4, null, null, null);

    assertThat(CallScript.todayStep(noPlanToday)).contains("가장 먼저 할 것 하나");
    assertThat(CallScript.todayStep(ORDINARY)).contains("얼마나 할 것 같은지");
    assertThat(CallScript.todayStep(null)).contains("가장 먼저 할 것 하나");
  }

  @Test
  @DisplayName("요즘 시작이 평균 15분 넘게 늦으면 시작 시각도 묻는다 (15분까지는 묻지 않는다)")
  void asksAboutStartTimeWhenUsuallyLate() {
    Card late = new Card(190, 240, null, 3, 180, "19:00", 64, 55, 16, null, 4, null, null, null);
    Card onTime = new Card(190, 240, null, 3, 180, "19:00", 64, 55, 15, null, 4, null, null, null);

    assertThat(CallScript.todayStep(late)).contains("제시간에 시작하려면");
    assertThat(CallScript.todayStep(onTime)).doesNotContain("제시간에 시작하려면");
  }

  // ---- 사실 ----

  @Test
  @DisplayName("달성률은 서버가 팀의 식으로 직접 계산한다 (소수점 버림, 최대 100, 계획이 없으면 계산 불가)")
  void rateIsCalculatedByTheServer() {
    assertThat(CallScript.yesterdayRate(card(190, 240, null, null))).isEqualTo(79);
    assertThat(CallScript.yesterdayRate(card(239, 240, null, null))).isEqualTo(99);
    assertThat(CallScript.yesterdayRate(card(300, 240, null, null))).isEqualTo(100);
    assertThat(CallScript.yesterdayRate(card(60, 0, null, null))).isNull();
    assertThat(CallScript.yesterdayRate(card(null, 240, null, null))).isNull();
  }

  @Test
  @DisplayName("사실에는 받은 숫자가 사람이 읽는 말로 들어간다")
  void factsListWhatWeKnow() {
    List<String> facts = CallScript.facts(ORDINARY);

    assertThat(facts)
        .containsExactly(
            "- 어제 순공시간: 3시간 10분 (계획 4시간 의 79%)",
            "- 어제 계획을 못 채운 이유로 학생이 고른 것: 폰·SNS",
            "- 이번 주 계획 대비 실제: 64%",
            "- 이번 주 완주율(목표를 정한 타이머를 끝까지 한 비율): 55%",
            "- 이번 주 평균 시작 지연: 계획한 시각보다 10분 늦게 시작",
            "- 오늘 계획: 3개, 합쳐서 3시간, 첫 계획은 19:00",
            "- 최근 기분(오래된 날부터): 보통 → 보통 → 좋음",
            "- 연속으로 공부한 날: 4일");
  }

  @Test
  @DisplayName("모르는 값(null)은 사실에 적지 않고, 아무것도 없으면 '기록이 없어' 한 줄만 적는다")
  void unknownValuesAreLeftOut() {
    Card onlyYesterday =
        new Card(60, 0, null, null, null, null, null, null, null, null, null, null, null, null);
    Card nothing =
        new Card(null, null, null, null, null, null, null, null, null, null, 0, null, null, null);

    assertThat(CallScript.facts(onlyYesterday)).containsExactly("- 어제 순공시간: 1시간 (어제는 계획이 없었어)");
    assertThat(CallScript.facts(nothing)).containsExactly("- 아직 기록이 없어. 처음 통화하는 것처럼 대해.");
    assertThat(CallScript.facts(null)).containsExactly("- 아직 기록이 없어. 처음 통화하는 것처럼 대해.");
  }

  // 어제 공부하다 나간 횟수 · 가장 자주 나간 시간대 · 집중 모드를 푼 횟수를 넣은 카드를 만듭니다. (어제 190분 공부, 계획 240분)
  private static Card cardWithAway(Integer awayCount, Integer peakHour, Integer unlockCount) {
    return new Card(
        190, 240, null, 3, 180, "19:00", 64, 55, 10, null, 4, awayCount, peakHour, unlockCount);
  }

  @Test
  @DisplayName("어제 공부하다 나간 횟수와 가장 자주 나간 시간대, 집중 모드를 푼 횟수를 사실에 적는다")
  void awayAndUnlockCountsAreWritten() {
    assertThat(CallScript.facts(cardWithAway(3, 21, 2)))
        .contains("- 어제 공부하다가 다른 데로 나갔다 온 횟수: 3번 (21시대에 가장 자주)")
        .contains("- 어제 집중 모드를 스스로 푼 횟수: 2번");
    // 시간대를 모르면 횟수만 적습니다.
    assertThat(CallScript.facts(cardWithAway(1, null, null)))
        .contains("- 어제 공부하다가 다른 데로 나갔다 온 횟수: 1번");
  }

  @Test
  @DisplayName("한 번도 안 나간 것은 공부를 한 날에만 적는다 (공부를 안 한 날의 0번은 칭찬할 일이 아니다)")
  void zeroAwayIsWrittenOnlyWhenStudied() {
    assertThat(CallScript.facts(cardWithAway(0, null, 0)))
        .contains("- 어제는 공부하는 동안 한 번도 다른 데로 나가지 않았어");
    // 집중 모드를 푼 적이 없으면(0번) 그 줄은 적지 않습니다.
    assertThat(String.join("\n", CallScript.facts(cardWithAway(0, null, 0))))
        .doesNotContain("집중 모드");

    Card noStudy = new Card(0, 240, null, 3, 180, "19:00", 64, 55, 10, null, 4, 0, null, 0);
    assertThat(String.join("\n", CallScript.facts(noStudy))).doesNotContain("나가지 않았어");

    // 값을 안 보냈으면(null) 아무 줄도 적지 않습니다. (예전 FE 와 통화해도 그대로 동작합니다)
    assertThat(String.join("\n", CallScript.facts(cardWithAway(null, null, null))))
        .doesNotContain("나갔다 온")
        .doesNotContain("나가지 않았어");
  }

  @Test
  @DisplayName("어제 계획을 못 채웠고 3번 이상 나갔으면, 탓하지 않고 그때 무슨 일이 있었는지 묻게 한다")
  void manyAwaysAddAQuestion() {
    assertThat(CallScript.yesterdayStep(cardWithAway(3, 21, null)))
        .contains("어제 공부하다가 3번 나갔다 왔어")
        .contains("탓하지 말고");
    // 2번까지는 묻지 않습니다.
    assertThat(CallScript.yesterdayStep(cardWithAway(2, 21, null))).doesNotContain("나갔다 왔어");
    assertThat(CallScript.yesterdayStep(cardWithAway(null, null, null))).doesNotContain("나갔다 왔어");

    // 계획을 다 채운 날에는 여러 번 나갔어도 묻지 않습니다.
    Card done = new Card(240, 240, null, 3, 180, "19:00", 64, 55, 10, null, 4, 5, 21, null);
    assertThat(CallScript.yesterdayStep(done)).doesNotContain("나갔다 왔어");
  }

  @Test
  @DisplayName("위로하는 통화에서는 나간 횟수를 묻는 말이 통화 순서에 들어가지 않는다")
  void supportiveCallDoesNotAskAboutAways() {
    // 가장 최근 기분이 1(많이 처짐)인 날
    Card lowMood =
        new Card(100, 240, null, 3, 180, "19:00", 64, 55, 10, List.of(3, 1), 4, 5, 21, 2);

    String instructions = CallScript.instructions(3, 3, lowMood);
    String order = instructions.substring(instructions.indexOf("[통화 순서]"));

    assertThat(order).doesNotContain("나갔다 왔어");
  }

  @Test
  @DisplayName("분은 '3시간 10분' 모양으로 바꾼다")
  void minutesAreFormatted() {
    assertThat(CallScript.formatMinutes(190)).isEqualTo("3시간 10분");
    assertThat(CallScript.formatMinutes(120)).isEqualTo("2시간");
    assertThat(CallScript.formatMinutes(45)).isEqualTo("45분");
    assertThat(CallScript.formatMinutes(0)).isEqualTo("0분");
  }

  // ---- 지침 전체 ----

  @Test
  @DisplayName("지침에는 어떤 경우에도 안전 규칙이 들어 있다 (숫자 지어내지 않기·깎아내리지 않기·위기 대응·AI 임을 숨기지 않기)")
  void instructionsAlwaysContainTheSafetyRules() {
    for (Card card : new Card[] {ORDINARY, null, card(100, 240, "phone", List.of(1))}) {
      for (int careLevel = 0; careLevel <= 4; careLevel++) {
        for (int harshness = 1; harshness <= 5; harshness++) {
          String instructions = CallScript.instructions(careLevel, harshness, card);

          assertThat(instructions).contains("숫자만 말해");
          assertThat(instructions).contains("깎아내리지 마");
          assertThat(instructions).contains("다른 사람과 비교하지 마");
          assertThat(instructions).contains("공부 이야기를 바로 멈춰");
          assertThat(instructions).contains("믿을 수 있는 어른");
          assertThat(instructions).contains("사람인 척하지 마");
          assertThat(instructions).contains("3분 안에");
        }
      }
    }
  }

  @Test
  @DisplayName("같은 날이라도 기록이 다르면 지침이 달라진다 (질문과 말투가 기록을 따라간다)")
  void differentRecordsGiveDifferentInstructions() {
    String missedByPhone = CallScript.instructions(1, 3, card(100, 240, "phone", List.of(3)));
    String metThePlan = CallScript.instructions(1, 3, card(240, 240, null, List.of(3)));
    String feelingLow = CallScript.instructions(1, 3, card(100, 240, "phone", List.of(1)));

    assertThat(missedByPhone).contains("폰을 어디에 두면").doesNotContain("무엇이 도움이 됐는지");
    assertThat(metThePlan).contains("무엇이 도움이 됐는지").doesNotContain("폰을 어디에 두면");
    assertThat(feelingLow).contains("위로하고 응원하는 말만");
    assertThat(missedByPhone).doesNotContain("위로하고 응원하는 말만");
  }

  @Test
  @DisplayName("기록이 없는 사람의 지침: 처음 통화하는 것처럼 대하고, 컨디션부터 묻는다")
  void instructionsForSomeoneWithNoRecords() {
    String instructions = CallScript.instructions(1, 3, null);

    assertThat(instructions).contains("아직 기록이 없어");
    assertThat(instructions).contains("오늘 컨디션이 어떤지 물어봐");
    assertThat(instructions).contains("가장 먼저 할 것 하나");
  }

  @Test
  @DisplayName("예시: 폰 때문에 계획을 못 채운 학생의 지침 전체")
  void exampleOfAWholeInstruction() {
    String instructions = CallScript.instructions(2, 3, ORDINARY);

    assertThat(instructions)
        .contains(
            "[말투]\n담백하게 사실만 말해.",
            "[오늘의 사실]\n- 어제 순공시간: 3시간 10분 (계획 4시간 의 79%)",
            "1. 짧게 인사하고, 어제 한 것을 한 문장으로 말해 줘. 예: \"어제 순공 3시간 10분, 계획의 79%야.\"",
            "2. 어제 계획을 다 채우지 못했고, 이유는 '폰·SNS' 라고 했어. 공부할 때 폰을 어디에 두면 덜 보게 될지 물어봐.",
            "3. 오늘은 얼마나 할 것 같은지 물어봐.",
            "4. 오늘의 약속을 같이 정해.");
  }
}
