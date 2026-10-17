package com.ithopeschool.projectbe.domain.goal;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.goal.WeeklyGoal.Kind;
import com.ithopeschool.projectbe.domain.goal.WeeklyGoal.Progress;
import com.ithopeschool.projectbe.domain.goal.WeeklyGoal.Status;
import com.ithopeschool.projectbe.domain.goal.WeeklyGoal.WeekFacts;
import com.ithopeschool.projectbe.domain.safety.SafetyBrake;
import org.junit.jupiter.api.Test;

// "이번 주 목표" 제안·판정 규칙(이슈 #88)을 확인합니다.
// feExample 로 시작하는 테스트는 FE 함수(src/lib/weeklyGoal.ts) 주석의 예시를 그대로 옮긴 것입니다.
class WeeklyGoalTest {

  // 한 주의 숫자를 만드는 도우미입니다.
  // 예: facts(55, 20, 10, 90) → 완주율 55%(타이머 20번), 평균 시작 지연 10분, 계획 대비 90%
  private static WeekFacts facts(
      Integer completionRate, int sampleCount, Integer startDelayMinutes, Integer planRate) {
    return new WeekFacts(completionRate, sampleCount, startDelayMinutes, planRate);
  }

  // 자주 쓰는 목표들
  private static final WeeklyGoal COMPLETION_65 = new WeeklyGoal(Kind.COMPLETION, 65, 55);
  private static final WeeklyGoal START_DELAY_18 = new WeeklyGoal(Kind.START_DELAY, 18, 23);
  private static final WeeklyGoal PLAN_RATE_74 = new WeeklyGoal(Kind.PLAN_RATE, 74, 64);
  private static final WeeklyGoal KEEP_80 = new WeeklyGoal(Kind.KEEP, 80, 80);

  // ─── candidates: 목표 후보 만들기 ───

  @Test
  void feExample1LowCompletionRate() {
    // FE 예1) 규칙 1: 완주율 55%(20번), 지연 10분, 계획 대비 90% → [완주율 65% 넘기기, 지난주만큼(55%)]
    assertThat(WeeklyGoal.candidates(facts(55, 20, 10, 90)))
        .containsExactly(
            new WeeklyGoal(Kind.COMPLETION, 65, 55), new WeeklyGoal(Kind.KEEP, 55, 55));
  }

  @Test
  void feExample2LongStartDelay() {
    // FE 예2) 규칙 2: 완주율 70%(10번), 지연 23분, 계획 대비 90% → [시작 지연 18분 안으로, 지난주만큼(70%)]
    assertThat(WeeklyGoal.candidates(facts(70, 10, 23, 90)))
        .containsExactly(
            new WeeklyGoal(Kind.START_DELAY, 18, 23), new WeeklyGoal(Kind.KEEP, 70, 70));
  }

  @Test
  void feExample3LowPlanRate() {
    // FE 예3) 규칙 3: 완주율 70%(10번), 지연 5분, 계획 대비 64% → [계획의 74% 채우기, 지난주만큼(70%)]
    assertThat(WeeklyGoal.candidates(facts(70, 10, 5, 64)))
        .containsExactly(new WeeklyGoal(Kind.PLAN_RATE, 74, 64), new WeeklyGoal(Kind.KEEP, 70, 70));
  }

  @Test
  void feExample4EverythingIsFine() {
    // FE 예4) 규칙 4: 완주율 80%(10번), 지연 5분, 계획 대비 95% → [지난주만큼(80%)]
    assertThat(WeeklyGoal.candidates(facts(80, 10, 5, 95)))
        .containsExactly(new WeeklyGoal(Kind.KEEP, 80, 80));
  }

  @Test
  void feExample5SeveralRulesMatchInOrder() {
    // FE 예5) 여러 개가 맞음: 완주율 40%(10번), 지연 30분, 계획 대비 50%
    //         → [완주율 50% 넘기기, 시작 지연 25분 안으로, 계획의 60% 채우기, 지난주만큼(40%)]
    assertThat(WeeklyGoal.candidates(facts(40, 10, 30, 50)))
        .containsExactly(
            new WeeklyGoal(Kind.COMPLETION, 50, 40),
            new WeeklyGoal(Kind.START_DELAY, 25, 30),
            new WeeklyGoal(Kind.PLAN_RATE, 60, 50),
            new WeeklyGoal(Kind.KEEP, 40, 40));
  }

  @Test
  void feExample6NotEnoughTimers() {
    // FE 예6) 기록 부족: 목표가 있는 타이머가 4번뿐 → []
    assertThat(WeeklyGoal.candidates(facts(40, 4, 30, 50))).isEmpty();
  }

  @Test
  void feExample7UnknownNumbersMakeNoGoal() {
    // FE 예7) 지연을 모름(null), 계획도 없음(null): 완주율 70%(10번) → [지난주만큼(70%)]
    assertThat(WeeklyGoal.candidates(facts(70, 10, null, null)))
        .containsExactly(new WeeklyGoal(Kind.KEEP, 70, 70));
  }

  // ─── 안전 브레이크가 켜진 주 ───

  // 안전 브레이크의 네 값: 채찍 멈춤 · 회복 모드 · 도움 안내 · 연락 금지 시간
  private static final SafetyBrake BRAKE_OFF = new SafetyBrake(false, false, false, false);

  @Test
  void brakeOffSuggestsTheSameAsBefore() {
    // 지난주: 완주율 55%(20번), 지연 10분, 계획 대비 90% → [완주율 65% 넘기기, 지난주만큼(55%)]
    WeekFacts lastWeek = facts(55, 20, 10, 90);

    assertThat(WeeklyGoal.candidates(lastWeek, BRAKE_OFF))
        .isEqualTo(WeeklyGoal.candidates(lastWeek))
        .hasSize(2);
  }

  @Test
  void noSuggestionWhileTheSafetyBrakeIsOn() {
    WeekFacts lastWeek = facts(55, 20, 10, 90);

    // 채찍 멈춤(위기 표현 · 시험 기간)이 켜진 주
    assertThat(WeeklyGoal.candidates(lastWeek, new SafetyBrake(true, false, false, false)))
        .isEmpty();
    // 회복 모드(기분 저하가 이어짐)가 켜진 주
    assertThat(WeeklyGoal.candidates(lastWeek, new SafetyBrake(false, true, false, false)))
        .isEmpty();
    // 둘 다 켜지고 도움 안내까지 보여주는 주
    assertThat(WeeklyGoal.candidates(lastWeek, new SafetyBrake(true, true, true, false))).isEmpty();
  }

  @Test
  void quietHoursAloneDoNotStopTheSuggestion() {
    // 연락 금지 시간은 "지금 연락해도 되는지" 일 뿐입니다. 목표 제안과는 상관없습니다.
    WeekFacts lastWeek = facts(55, 20, 10, 90);

    assertThat(WeeklyGoal.candidates(lastWeek, new SafetyBrake(false, false, false, true)))
        .isEqualTo(WeeklyGoal.candidates(lastWeek));
  }

  @Test
  void brakeOffStillNeedsEnoughRecords() {
    // 안전 브레이크가 꺼져 있어도, 지난주 타이머가 4번뿐이면 제안하지 않습니다.
    assertThat(WeeklyGoal.candidates(facts(55, 4, 10, 90), BRAKE_OFF)).isEmpty();
  }

  @Test
  void fiveTimersAreEnoughToSuggest() {
    // 경계값: 목표가 있는 타이머가 딱 5번이면 제안합니다.
    assertThat(WeeklyGoal.candidates(facts(80, 5, 5, 95)))
        .containsExactly(new WeeklyGoal(Kind.KEEP, 80, 80));
  }

  @Test
  void noSuggestionWithoutCompletionRate() {
    // 완주율을 모르면(null) 다른 숫자가 있어도 제안하지 않습니다.
    assertThat(WeeklyGoal.candidates(facts(null, 0, 30, 50))).isEmpty();
  }

  @Test
  void boundariesOfEachRule() {
    // 경계값: 완주율 60% 는 "미만" 이 아니라서 완주율 목표가 없고, 59% 는 있습니다.
    assertThat(WeeklyGoal.candidates(facts(60, 10, 5, 95)))
        .containsExactly(new WeeklyGoal(Kind.KEEP, 60, 60));
    assertThat(WeeklyGoal.candidates(facts(59, 10, 5, 95)))
        .containsExactly(
            new WeeklyGoal(Kind.COMPLETION, 69, 59), new WeeklyGoal(Kind.KEEP, 59, 59));

    // 시작 지연 15분은 "넘음" 이 아니라서 목표가 없고, 16분은 있습니다.
    assertThat(WeeklyGoal.candidates(facts(80, 10, 15, 95)))
        .containsExactly(new WeeklyGoal(Kind.KEEP, 80, 80));
    assertThat(WeeklyGoal.candidates(facts(80, 10, 16, 95)))
        .containsExactly(
            new WeeklyGoal(Kind.START_DELAY, 11, 16), new WeeklyGoal(Kind.KEEP, 80, 80));

    // 계획 대비 80% 는 "미만" 이 아니라서 목표가 없고, 79% 는 있습니다.
    assertThat(WeeklyGoal.candidates(facts(80, 10, 5, 80)))
        .containsExactly(new WeeklyGoal(Kind.KEEP, 80, 80));
    assertThat(WeeklyGoal.candidates(facts(80, 10, 5, 79)))
        .containsExactly(new WeeklyGoal(Kind.PLAN_RATE, 89, 79), new WeeklyGoal(Kind.KEEP, 80, 80));
  }

  @Test
  void zeroPercentIsANumberNotUnknown() {
    // 0% 는 "모름" 이 아니라 숫자입니다. 완주율 0%·계획 대비 0% 로도 목표를 만듭니다.
    assertThat(WeeklyGoal.candidates(facts(0, 10, null, 0)))
        .containsExactly(
            new WeeklyGoal(Kind.COMPLETION, 10, 0),
            new WeeklyGoal(Kind.PLAN_RATE, 10, 0),
            new WeeklyGoal(Kind.KEEP, 0, 0));
  }

  // ─── title · reason: 보여줄 문장 ───

  @Test
  void feExampleTitles() {
    // FE 예) 종류마다 한 줄 제목
    assertThat(COMPLETION_65.title()).isEqualTo("완주율 65% 넘기기");
    assertThat(START_DELAY_18.title()).isEqualTo("시작 지연 18분 안으로");
    assertThat(PLAN_RATE_74.title()).isEqualTo("계획의 74% 채우기");
    assertThat(KEEP_80.title()).isEqualTo("이번 주도 지난주만큼 (완주율 80%)");
  }

  @Test
  void feExampleReason() {
    // FE 예) { kind: 'completion', target: 65, baseline: 55 } → '지난주 완주율은 55% 였어. 10%p 만 올려 보자.'
    assertThat(COMPLETION_65.reason()).isEqualTo("지난주 완주율은 55% 였어. 10%p 만 올려 보자.");
  }

  @Test
  void reasonsShowLastWeeksNumber() {
    // 나머지 종류도 지난주 숫자를 그대로 보여줍니다.
    assertThat(START_DELAY_18.reason()).isEqualTo("지난주엔 평균 23분 늦게 시작했어. 5분만 당겨 보자.");
    assertThat(PLAN_RATE_74.reason()).isEqualTo("지난주엔 계획의 64% 를 했어. 10%p 만 더 채워 보자.");
    assertThat(KEEP_80.reason()).isEqualTo("지난주 숫자가 괜찮았어. 이번 주도 그만큼만 하면 돼.");
  }

  // ─── progress: 달성 판정 ───

  @Test
  void feExampleProgressCompletionMissed() {
    // FE 예) 목표 "완주율 65% 넘기기", 지금 완주율 60% → { current: 60, status: 'missed', percent: 92 }
    assertThat(COMPLETION_65.progress(facts(60, 10, null, null)))
        .isEqualTo(new Progress(60, Status.MISSED, 92));
  }

  @Test
  void feExampleProgressCompletionAchieved() {
    // FE 예) 목표 "완주율 65% 넘기기", 지금 완주율 70% → { current: 70, status: 'achieved', percent: 100 }
    assertThat(COMPLETION_65.progress(facts(70, 10, null, null)))
        .isEqualTo(new Progress(70, Status.ACHIEVED, 100));
  }

  @Test
  void feExampleProgressStartDelayMissed() {
    // FE 예) 목표 "시작 지연 18분 안으로", 지금 24분 → { current: 24, status: 'missed', percent: 75 }
    assertThat(START_DELAY_18.progress(facts(70, 10, 24, null)))
        .isEqualTo(new Progress(24, Status.MISSED, 75));
  }

  @Test
  void feExampleProgressStartDelayAchieved() {
    // FE 예) 목표 "시작 지연 18분 안으로", 지금 10분 → { current: 10, status: 'achieved', percent: 100 }
    assertThat(START_DELAY_18.progress(facts(70, 10, 10, null)))
        .isEqualTo(new Progress(10, Status.ACHIEVED, 100));
  }

  @Test
  void feExampleProgressUnknownWithoutPlans() {
    // FE 예) 목표 "계획의 74% 채우기", 계획이 없음(null) → { current: null, status: 'unknown', percent: 0 }
    assertThat(PLAN_RATE_74.progress(facts(70, 10, 5, null)))
        .isEqualTo(new Progress(null, Status.UNKNOWN, 0));
  }

  @Test
  void eachKindLooksAtItsOwnNumber() {
    // 종류마다 보는 숫자가 다릅니다. 완주율 90%, 시작 지연 30분, 계획 대비 50% 인 주라면:
    WeekFacts week = facts(90, 10, 30, 50);

    assertThat(COMPLETION_65.progress(week).current()).isEqualTo(90);
    assertThat(KEEP_80.progress(week).current()).isEqualTo(90);
    assertThat(START_DELAY_18.progress(week).current()).isEqualTo(30);
    assertThat(PLAN_RATE_74.progress(week).current()).isEqualTo(50);

    assertThat(COMPLETION_65.progress(week).status()).isEqualTo(Status.ACHIEVED);
    assertThat(KEEP_80.progress(week).status()).isEqualTo(Status.ACHIEVED);
    assertThat(START_DELAY_18.progress(week).status()).isEqualTo(Status.MISSED);
    assertThat(PLAN_RATE_74.progress(week).status()).isEqualTo(Status.MISSED);
  }

  @Test
  void exactlyTheTargetIsAchieved() {
    // 경계값: 목표와 딱 같으면 달성입니다. (비율은 "이상", 시작 지연은 "이하")
    assertThat(COMPLETION_65.progress(facts(65, 10, null, null)).status())
        .isEqualTo(Status.ACHIEVED);
    assertThat(COMPLETION_65.progress(facts(64, 10, null, null)).status()).isEqualTo(Status.MISSED);
    assertThat(START_DELAY_18.progress(facts(70, 10, 18, null)).status())
        .isEqualTo(Status.ACHIEVED);
    assertThat(START_DELAY_18.progress(facts(70, 10, 19, null)).status()).isEqualTo(Status.MISSED);
  }

  @Test
  void keepGoalIsJudgedByCompletionRate() {
    // "지난주만큼" 은 완주율이 지난주(80%) 이상이면 달성입니다.
    assertThat(KEEP_80.progress(facts(80, 10, null, null)))
        .isEqualTo(new Progress(80, Status.ACHIEVED, 100));
    // 60% 면 미달이고, 막대는 60 ÷ 80 = 75
    assertThat(KEEP_80.progress(facts(60, 10, null, null)))
        .isEqualTo(new Progress(60, Status.MISSED, 75));
  }

  @Test
  void zeroIsMissedNotUnknown() {
    // 0% 는 "알 수 없음" 이 아니라 미달입니다. 막대는 0 입니다.
    assertThat(COMPLETION_65.progress(facts(0, 10, null, null)))
        .isEqualTo(new Progress(0, Status.MISSED, 0));
  }

  @Test
  void zeroMinuteDelayGoalIsMissedWithEmptyBar() {
    // 시작 지연 목표가 0분이면, 1분이라도 늦으면 미달이고 막대는 0 입니다. (0 ÷ 지금)
    WeeklyGoal zeroDelay = new WeeklyGoal(Kind.START_DELAY, 0, 3);

    assertThat(zeroDelay.progress(facts(70, 10, 0, null)))
        .isEqualTo(new Progress(0, Status.ACHIEVED, 100));
    assertThat(zeroDelay.progress(facts(70, 10, 4, null)))
        .isEqualTo(new Progress(4, Status.MISSED, 0));
  }

  @Test
  void judgingTwiceGivesTheSameResult() {
    // 이슈 #88 완료 조건: 판정이 두 번 돌아도 결과가 같습니다. (따로 기억해 두는 상태가 없습니다)
    WeekFacts week = facts(60, 10, 24, 50);

    assertThat(COMPLETION_65.progress(week)).isEqualTo(COMPLETION_65.progress(week));
    assertThat(START_DELAY_18.progress(week)).isEqualTo(START_DELAY_18.progress(week));
  }

  @Test
  void proposalAndJudgementWorkTogether() {
    // 제안 → 한 주 뒤 판정까지 이어지는 흐름입니다.
    // 지난주 완주율 55% → 첫 번째 제안은 "완주율 65% 넘기기"
    WeeklyGoal goal = WeeklyGoal.candidates(facts(55, 20, 10, 90)).get(0);
    assertThat(goal.title()).isEqualTo("완주율 65% 넘기기");

    // 이번 주 완주율이 68% 였다면 달성
    assertThat(goal.progress(facts(68, 15, 10, 90)).status()).isEqualTo(Status.ACHIEVED);
  }
}
