package com.ithopeschool.projectbe.domain.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ithopeschool.projectbe.domain.goal.WeeklyGoal;
import com.ithopeschool.projectbe.domain.goal.WeeklyGoal.Kind;
import com.ithopeschool.projectbe.domain.message.AppAlert.GoalLabel;
import com.ithopeschool.projectbe.domain.message.AppAlert.Sentence;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

// 앱 알림의 말투와 문구 규칙(이슈 #31)을 확인합니다.
// feExample 로 시작하는 테스트는 FE 함수(src/lib/appAlert.ts) 주석의 예시를 그대로 옮긴 것입니다.
class AppAlertTest {

  // FE 예시에서 쓰는 값들
  private static final Map<String, String> PLAN_VALUES =
      Map.of("time", "19:00", "plan", "국어 비문학 3지문");
  private static final Map<String, String> TIMER_VALUES = Map.of("subject", "국어", "minutes", "50");

  // ─── careLevelOf: 어느 말투로 보낼지 ───

  @Test
  void feExampleCareLevels() {
    // FE 예) ('planLate', 4, false) → 2   (넛지 그대로)
    assertThat(AppAlert.careLevelOf(AppAlertType.PLAN_LATE, 4, false)).isEqualTo(2);
    // FE 예) ('planLate', 1, false) → 1   (한도가 당근이면 당근 말투로)
    assertThat(AppAlert.careLevelOf(AppAlertType.PLAN_LATE, 1, false)).isEqualTo(1);
    // FE 예) ('planLate', 0, false) → 0
    assertThat(AppAlert.careLevelOf(AppAlertType.PLAN_LATE, 0, false)).isEqualTo(0);
    // FE 예) ('planLate', 4, true) → 0   (안전 브레이크)
    assertThat(AppAlert.careLevelOf(AppAlertType.PLAN_LATE, 4, true)).isEqualTo(0);
    // FE 예) ('goalDone', 4, false) → 1   (기본 말투보다 세지지는 않습니다)
    assertThat(AppAlert.careLevelOf(AppAlertType.GOAL_DONE, 4, false)).isEqualTo(1);
  }

  @Test
  void safetyBrakeAlwaysMeansRecoveryTone() {
    // 안전 브레이크가 켜지면 어떤 종류든, 한도가 얼마든 회복(0) 말투입니다.
    for (AppAlertType type : AppAlertType.values()) {
      for (int maxCareLevel = 0; maxCareLevel <= 4; maxCareLevel++) {
        assertThat(AppAlert.careLevelOf(type, maxCareLevel, true)).as("%s", type).isEqualTo(0);
      }
    }
  }

  @Test
  void careLevelNeverGoesOverTheUsersLimit() {
    // 어떤 종류든 사용자가 정한 한도를 넘지 않고, 그 종류의 기본 말투보다 세지지도 않습니다.
    for (AppAlertType type : AppAlertType.values()) {
      for (int maxCareLevel = 0; maxCareLevel <= 4; maxCareLevel++) {
        int careLevel = AppAlert.careLevelOf(type, maxCareLevel, false);

        assertThat(careLevel).as("%s 한도 %d", type, maxCareLevel).isLessThanOrEqualTo(maxCareLevel);
        assertThat(careLevel)
            .as("%s 한도 %d", type, maxCareLevel)
            .isLessThanOrEqualTo(type.getCareLevel());
      }
    }
  }

  // ─── textOf: 문장 만들기 ───

  @Test
  void feExamplePlanLateTexts() {
    // FE 예) ('planLate', 2, 3), random 0 → '19:00 국어 비문학 3지문, 시작할 시간이야'
    assertThat(AppAlert.textOf(AppAlertType.PLAN_LATE, 2, 3, PLAN_VALUES, 0))
        .isEqualTo("19:00 국어 비문학 3지문, 시작할 시간이야");
    // FE 예) random 이 0.9 면 '19:00 국어 비문학 3지문, 아직 시작 전이야. 타이머부터 켜자.'
    assertThat(AppAlert.textOf(AppAlertType.PLAN_LATE, 2, 3, PLAN_VALUES, 0.9))
        .isEqualTo("19:00 국어 비문학 3지문, 아직 시작 전이야. 타이머부터 켜자.");
    // FE 예) ('planLate', 2, 1) → '19:00 국어 비문학 3지문, 시작할 시간이야'   (수위 2 문장은 나오지 않습니다)
    assertThat(AppAlert.textOf(AppAlertType.PLAN_LATE, 2, 1, PLAN_VALUES, 0.9))
        .isEqualTo("19:00 국어 비문학 3지문, 시작할 시간이야");
    // FE 예) ('planLate', 1, 3) → '19:00 국어 비문학 3지문 할 시간이야. 지금 켜면 계획대로야.'
    assertThat(AppAlert.textOf(AppAlertType.PLAN_LATE, 1, 3, PLAN_VALUES, 0))
        .isEqualTo("19:00 국어 비문학 3지문 할 시간이야. 지금 켜면 계획대로야.");
    // FE 예) ('planLate', 0, 3) → '19:00 국어 비문학 3지문, 준비되면 10분만 해볼까?'
    assertThat(AppAlert.textOf(AppAlertType.PLAN_LATE, 0, 3, PLAN_VALUES, 0))
        .isEqualTo("19:00 국어 비문학 3지문, 준비되면 10분만 해볼까?");
  }

  @Test
  void feExampleTimerDoneTexts() {
    // FE 예) ('timerDone', 1, 3) → '국어 50분 완주!'
    assertThat(AppAlert.textOf(AppAlertType.TIMER_DONE, 1, 3, TIMER_VALUES, 0))
        .isEqualTo("국어 50분 완주!");
    // FE 예) ('timerDone', 0, 3) → '국어 50분을 채웠어. 잠깐 쉬어도 돼.'
    assertThat(AppAlert.textOf(AppAlertType.TIMER_DONE, 0, 3, TIMER_VALUES, 0))
        .isEqualTo("국어 50분을 채웠어. 잠깐 쉬어도 돼.");
  }

  @Test
  void usesTheClosestToneBelowTheGivenLevel() {
    // 그 말투의 문장이 없으면 그보다 낮은 것 중 가장 가까운 말투의 문장을 씁니다.
    // 타이머 완주에는 넛지(2) 문장이 없어서, 말투 2 ~ 4 로 불러도 당근(1) 문장이 나옵니다.
    for (int careLevel = 1; careLevel <= 4; careLevel++) {
      assertThat(AppAlert.textOf(AppAlertType.TIMER_DONE, careLevel, 3, TIMER_VALUES, 0))
          .isEqualTo("국어 50분 완주!");
    }
  }

  @Test
  void fallsBackToTheMildestSentenceWhenAllAreTooHarsh() {
    // 허용 수위가 0 이라 맞는 문장이 하나도 없으면, 그 말투에서 가장 순한 문장을 씁니다.
    assertThat(AppAlert.textOf(AppAlertType.PLAN_LATE, 2, 0, PLAN_VALUES, 0.9))
        .isEqualTo("19:00 국어 비문학 3지문, 시작할 시간이야");
  }

  @Test
  void missedThreeDaysIsAlwaysRecoveryTone() {
    // 3일 연속 미달은 말투를 4 로 불러도 회복 문장만 나옵니다. 독촉하는 문장이 아예 없습니다.
    assertThat(AppAlert.textOf(AppAlertType.MISSED_3_DAYS, 4, 3, Map.of(), 0))
        .isEqualTo("요즘 힘들지. 오늘은 10분만 해볼까?");
    assertThat(AppAlert.textOf(AppAlertType.MISSED_3_DAYS, 4, 3, Map.of(), 0.5))
        .isEqualTo("괜찮아, 천천히 다시 시작하자. 오늘은 한 가지만 해보자.");
    assertThat(AppAlert.sentencesOf(AppAlertType.MISSED_3_DAYS))
        .allSatisfy(sentence -> assertThat(sentence.careLevel()).isEqualTo(0));
  }

  @Test
  void missingValueBecomesEmptyText() {
    // 넘기지 않은 값의 자리는 빈 글자가 됩니다. 중괄호가 그대로 보이지 않습니다.
    assertThat(AppAlert.textOf(AppAlertType.TIMER_DONE, 1, 3, Map.of("minutes", "50"), 0))
        .isEqualTo(" 50분 완주!");
  }

  @Test
  void valuesArePutInAsTheyAre() {
    // 값에 $ 나 \ 같은 글자가 들어 있어도 그대로 끼워집니다. (계획 내용은 사용자가 적은 글입니다)
    Map<String, String> values = Map.of("time", "19:00", "plan", "수학 $1 \\문제");

    assertThat(AppAlert.textOf(AppAlertType.PLAN_LATE, 2, 3, values, 0))
        .isEqualTo("19:00 수학 $1 \\문제, 시작할 시간이야");
  }

  @Test
  void randomPicksAmongTheRemainingSentences() {
    // random 은 남은 문장 중 몇 번째를 쓸지 정합니다. 남은 문장이 2개면 0.5 미만은 첫 번째, 0.5 부터는 두 번째입니다.
    assertThat(AppAlert.textOf(AppAlertType.PLAN_LATE, 2, 3, PLAN_VALUES, 0.49))
        .isEqualTo("19:00 국어 비문학 3지문, 시작할 시간이야");
    assertThat(AppAlert.textOf(AppAlertType.PLAN_LATE, 2, 3, PLAN_VALUES, 0.5))
        .isEqualTo("19:00 국어 비문학 3지문, 아직 시작 전이야. 타이머부터 켜자.");
  }

  @Test
  void textWithoutRandomIsOneOfTheSentences() {
    // random 을 넘기지 않으면 남은 문장 중 아무거나 나옵니다. 어느 것이 나와도 정해 둔 문장 중 하나입니다.
    String text = AppAlert.textOf(AppAlertType.PLAN_LATE, 2, 3, PLAN_VALUES);

    assertThat(text).isIn("19:00 국어 비문학 3지문, 시작할 시간이야", "19:00 국어 비문학 3지문, 아직 시작 전이야. 타이머부터 켜자.");
  }

  @Test
  void rejectsWrongInputs() {
    // 말투가 음수이거나 random 이 0 이상 1 미만이 아니면 에러를 냅니다. (잘못된 값이 들어온 곳을 바로 찾을 수 있게)
    assertThatThrownBy(() -> AppAlert.textOf(AppAlertType.PLAN_LATE, -1, 3, PLAN_VALUES, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AppAlert.textOf(AppAlertType.PLAN_LATE, 2, 3, PLAN_VALUES, 1.0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AppAlert.textOf(AppAlertType.PLAN_LATE, 2, 3, PLAN_VALUES, -0.1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ─── 문장 목록이 지켜야 하는 규칙 ───

  @Test
  void everyTypeHasARecoverySentence() {
    // 모든 종류에 회복(0) 말투 문장이 하나 이상 있어야 합니다.
    // 한도를 0 으로 낮춘 사람과 안전 브레이크가 켜진 사람에게도 보낼 문장이 필요하기 때문입니다.
    for (AppAlertType type : AppAlertType.values()) {
      assertThat(AppAlert.sentencesOf(type))
          .as("%s", type)
          .anySatisfy(sentence -> assertThat(sentence.careLevel()).isEqualTo(0));
    }
  }

  @Test
  void noSentenceIsHarsherThanItsTypeAllows() {
    // 문장의 말투는 그 종류의 기본 말투를 넘지 않습니다. 앱 알림에는 채찍(3 이상) 문장이 없습니다.
    // 독설 수위는 1(부드럽게) ~ 3(따끔하게) 안입니다.
    for (AppAlertType type : AppAlertType.values()) {
      for (Sentence sentence : AppAlert.sentencesOf(type)) {
        assertThat(sentence.careLevel())
            .as("%s", sentence.text())
            .isBetween(0, type.getCareLevel());
        assertThat(sentence.careLevel()).as("%s", sentence.text()).isLessThanOrEqualTo(2);
        assertThat(sentence.harshness()).as("%s", sentence.text()).isBetween(1, 3);
      }
    }
  }

  @Test
  void everyToneAndHarshnessProducesACompleteSentence() {
    // 종류 6가지 × 말투 0~4 × 허용 수위 0~5 어느 조합으로 불러도 문장이 만들어지고, 중괄호가 남지 않습니다.
    Map<String, String> values =
        Map.of(
            "time", "19:00", "plan", "국어", "subject", "국어", "minutes", "50", "target", "65%",
            "label", "완주율", "before", "55%", "after", "66%");

    for (AppAlertType type : AppAlertType.values()) {
      for (int careLevel = 0; careLevel <= 4; careLevel++) {
        for (int maxHarshness = 0; maxHarshness <= 5; maxHarshness++) {
          String text = AppAlert.textOf(type, careLevel, maxHarshness, values, 0);

          assertThat(text).as("%s %d %d", type, careLevel, maxHarshness).isNotBlank();
          assertThat(text).doesNotContain("{").doesNotContain("}");
        }
      }
    }
  }

  // ─── 종류마다 붙는 값 ───

  @Test
  void labelsAndDefaultTonesAreTheSameAsFe() {
    // FE 의 appAlertLabels · appAlertCareLevels 와 같은 값입니다.
    assertThat(AppAlertType.PLAN_LATE.getLabel()).isEqualTo("계획 시간 지남");
    assertThat(AppAlertType.TIMER_DONE.getLabel()).isEqualTo("타이머 완주");
    assertThat(AppAlertType.GOAL_DONE.getLabel()).isEqualTo("목표 달성");
    assertThat(AppAlertType.MISSED_3_DAYS.getLabel()).isEqualTo("3일 연속 미달");
    assertThat(AppAlertType.WEEK_GOAL_ACHIEVED.getLabel()).isEqualTo("주간 목표 달성");
    assertThat(AppAlertType.WEEK_GOAL_MISSED.getLabel()).isEqualTo("주간 목표 결과");

    assertThat(AppAlertType.PLAN_LATE.getCareLevel()).isEqualTo(2);
    assertThat(AppAlertType.TIMER_DONE.getCareLevel()).isEqualTo(1);
    assertThat(AppAlertType.GOAL_DONE.getCareLevel()).isEqualTo(1);
    assertThat(AppAlertType.MISSED_3_DAYS.getCareLevel()).isEqualTo(0);
    assertThat(AppAlertType.WEEK_GOAL_ACHIEVED.getCareLevel()).isEqualTo(1);
    assertThat(AppAlertType.WEEK_GOAL_MISSED.getCareLevel()).isEqualTo(2);
  }

  @Test
  void eventMessageTypesMapToTheSameAlertTypes() {
    // EventMessage.pick 이 고른 종류는 같은 이름의 알림 종류로 바뀝니다.
    assertThat(AppAlertType.from(EventMessageType.PLAN_LATE)).isEqualTo(AppAlertType.PLAN_LATE);
    assertThat(AppAlertType.from(EventMessageType.GOAL_DONE)).isEqualTo(AppAlertType.GOAL_DONE);
    assertThat(AppAlertType.from(EventMessageType.MISSED_3_DAYS))
        .isEqualTo(AppAlertType.MISSED_3_DAYS);
  }

  // ─── 지난주 목표 결과 알림 ───

  @Test
  void weekGoalLabelsAreTheSameAsFe() {
    // FE 의 weekGoalLabels 와 같은 값입니다. "지난주만큼" 목표는 완주율로 판정해서 이름도 완주율입니다.
    assertThat(AppAlert.weekGoalLabelOf(Kind.COMPLETION)).isEqualTo(new GoalLabel("완주율", "%"));
    assertThat(AppAlert.weekGoalLabelOf(Kind.KEEP)).isEqualTo(new GoalLabel("완주율", "%"));
    assertThat(AppAlert.weekGoalLabelOf(Kind.PLAN_RATE)).isEqualTo(new GoalLabel("계획 대비 실제", "%"));
    assertThat(AppAlert.weekGoalLabelOf(Kind.START_DELAY)).isEqualTo(new GoalLabel("시작 지연", "분"));
  }

  @Test
  void weekGoalAchievedText() {
    // "완주율 65% 넘기기"(지난주 55%)를 66% 로 달성한 주의 알림입니다.
    WeeklyGoal goal = new WeeklyGoal(Kind.COMPLETION, 65, 55);
    Map<String, String> values = AppAlert.weekGoalValues(goal, 66);

    assertThat(values)
        .containsOnly(
            Map.entry("label", "완주율"),
            Map.entry("before", "55%"),
            Map.entry("after", "66%"),
            Map.entry("target", "65%"));
    assertThat(AppAlert.textOf(AppAlertType.WEEK_GOAL_ACHIEVED, 1, 3, values, 0))
        .isEqualTo("지난주 목표 달성! 완주율 55% → 66%");
    assertThat(AppAlert.textOf(AppAlertType.WEEK_GOAL_ACHIEVED, 0, 3, values, 0))
        .isEqualTo("지난주 목표를 채웠어. 완주율 55% → 66%. 이번 주는 편하게 가도 돼.");
  }

  @Test
  void weekGoalMissedText() {
    // "시작 지연 18분 안으로"(지난주 23분)를 24분으로 못 채운 주의 알림입니다. 단위가 % 가 아니라 분입니다.
    WeeklyGoal goal = new WeeklyGoal(Kind.START_DELAY, 18, 23);
    Map<String, String> values = AppAlert.weekGoalValues(goal, 24);

    assertThat(AppAlert.textOf(AppAlertType.WEEK_GOAL_MISSED, 2, 1, values, 0))
        .isEqualTo("지난주 시작 지연 23분 → 24분 (목표 18분). 이번 주에 다시 해보자.");
    assertThat(AppAlert.textOf(AppAlertType.WEEK_GOAL_MISSED, 0, 1, values, 0))
        .isEqualTo("지난주 시작 지연 23분 → 24분. 이번 주는 가볍게 다시 시작하자.");
  }

  @Test
  void sentencesListCannotBeChangedFromOutside() {
    // 정해 둔 문장 목록은 밖에서 고칠 수 없습니다.
    List<Sentence> sentences = AppAlert.sentencesOf(AppAlertType.PLAN_LATE);

    assertThatThrownBy(() -> sentences.add(new Sentence(4, 3, "아무 말")))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
