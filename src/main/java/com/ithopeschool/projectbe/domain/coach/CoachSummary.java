package com.ithopeschool.projectbe.domain.coach;

import com.ithopeschool.projectbe.domain.coach.dto.WeeklyCoachRequest;
import com.ithopeschool.projectbe.domain.stats.StudyFacts;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactValue;
import java.util.ArrayList;
import java.util.List;

// 코치 문단의 "기본 문장" 과 그 근거 숫자를 만듭니다. (이슈 #87)
// 숫자는 코드가(StudyFacts), 말은 AI 가 맡습니다. AI 에게는 여기서 고른 숫자만 줍니다.
// AI 가 없거나, AI 가 쓴 문단을 버렸을 때(CoachParagraphCheck)는 이 기본 문장을 보여줍니다. 그래서 문단은 항상 나옵니다.
//
// 정해 둔 규칙
//   0. 이번 주에 목표가 있는 타이머가 5번 미만이면 문단을 만들지 않습니다. (null)
//   1. 계획 대비 실제를 계산할 수 있으면 첫 문장으로 말합니다. 지난주 값도 있고 다르면 얼마나 오르내렸는지 덧붙입니다.
//   2. 완주율을 "몇 번 중 몇 번" 과 함께 말합니다.
//   3. 눈에 띄는 요일: 타이머가 2번 이상 있는 요일 중 완주율이 가장 낮고, 전체보다 20%p 이상 낮은 요일 하나.
//      그런 요일이 없으면 이 문장은 넣지 않습니다. 가장 낮은 요일이 여럿이면 일요일부터 세어 먼저 나오는 요일입니다.
//   4. 마지막 문장(권하는 말)은 위에서부터 처음 맞는 것 하나입니다.
//        중간에 멈춘 타이머가 2번 이상  →  멈춘 지점을 말하고 "목표를 조금 짧게" 를 권합니다
//        평균 시작 지연이 15분 넘음     →  지연을 말하고 "시작 시각에 알림" 을 권합니다
//        그 밖                          →  "이만큼만 이어 가자" 고 합니다
//   문장에 쓴 숫자는 근거(evidence)에 넣습니다. 평가하는 말("잘했어", "부족해")은 쓰지 않습니다.
//
// 예시 (입력 → 결과)
//   계획 대비 64%(계획 12개), 완주율 55%(20번 중 11번), 화요일 20%(5번), 멈춘 지점 62%(9번) / 지난주 계획 대비 56%
//     →  이번 주는 계획의 64% 를 했어. 지난주보다 8%p 올랐어.
//        목표를 정한 타이머 20번 중 11번을 끝까지 했어. (완주율 55%)
//        눈에 띄는 건 화요일이야. 화요일 완주율은 20% 로 전체(55%)보다 낮아.
//        중간에 멈춘 타이머는 목표의 62% 쯤에서 멈췄어. 다음엔 목표를 조금 짧게 잡아 보면 어때?
//   위와 같은데 화요일 완주율이 40%  →  전체보다 15%p 낮아서(20%p 미만) 화요일 문장은 빠집니다
//   계획 대비 90%, 완주율 100%(6번 중 6번), 시작 지연 23분(계획 4개), 멈춘 타이머 없음
//     →  … 계획한 시각보다 평균 23분 늦게 시작했어. 시작 시각에 알림을 맞춰 두면 어때?
//   위와 같은데 시작 지연 5분        →  … 지금 흐름이 좋아. 다음 주도 이만큼만 이어 가 보자.
//   목표가 있는 타이머가 4번뿐       →  null
//   계획이 없던 주(계획 대비 null)   →  첫 문장 없이 완주율 문장부터 시작합니다
//
// ⚠️ 기준 숫자 4개(5번 · 2번 · 20%p · 15분)는 FE 에서 정한 값을 그대로 옮긴 것입니다.
// FE 의 getCoachSummary (src/lib/weeklyCoach.ts) 와 같은 규칙·같은 문장입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 문단이 어긋나지 않습니다.
//
// sentences: 문단을 이루는 문장들. 순서대로 이어 붙여 보여줍니다.
// evidence: 문장에 쓰인 숫자들. AI 에게 건네는 숫자이고, 화면의 "근거 숫자 보기" 에도 그대로 보여줍니다.
//   (모양은 FE 가 보내던 WeeklyCoachRequest.Number 와 같아서 CoachPrompt · CoachParagraphCheck 에 그대로 넘길 수
// 있습니다)
public record CoachSummary(List<String> sentences, List<WeeklyCoachRequest.Number> evidence) {

  // 목표가 있는 타이머가 이만큼은 있어야 문단을 만듭니다. 이보다 적으면 "기록이 더 쌓이면 이야기해 줄게" 를 보여줍니다.
  public static final int MIN_SAMPLES_FOR_COACH = 5;

  // 요일 하나를 "눈에 띈다" 고 말하려면, 그 요일에 목표가 있는 타이머가 이만큼은 있어야 합니다.
  public static final int MIN_WEEKDAY_SAMPLES = 2;

  // 그 요일의 완주율이 전체보다 이만큼(%p) 이상 낮아야 "눈에 띈다" 고 말합니다.
  public static final int WEEKDAY_GAP = 20;

  // 평균 시작 지연이 이 값(분)을 넘으면 "시작이 늦다" 는 이야기를 합니다.
  public static final int LATE_START_MINUTES = 15;

  // 멈춘 지점을 말하려면 중간에 멈춘 타이머가 이만큼은 있어야 합니다.
  private static final int MIN_STOPPED_SAMPLES = 2;

  // 주간 리포트에서 기간을 부르는 말
  public static final PeriodWords WEEK_WORDS = new PeriodWords("이번 주", "지난주", "다음 주");

  // 월간 리포트(4주)에서 기간을 부르는 말
  public static final PeriodWords MONTH_WORDS = new PeriodWords("이 4주", "그 전 4주", "다음 달");

  // 0번째 = 일요일 … 6번째 = 토요일. StudyFacts.completionByWeekday 와 같은 순서입니다.
  private static final List<String> WEEKDAY_NAMES =
      List.of("일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일");

  // 문장에서 기간을 부르는 말입니다. 주간과 월간이 같은 규칙을 쓰되 이 말만 다릅니다.
  // current: 지금 보는 기간 (예: "이번 주") / previous: 비교하는 그 전 기간 (예: "지난주") / next: 다음 기간 (예: "다음 주")
  public record PeriodWords(String current, String previous, String next) {}

  // 주간 리포트의 기본 문장을 만듭니다. 기록이 모자라면 null 입니다.
  public static CoachSummary of(StudyFacts thisWeek, StudyFacts lastWeek) {
    return of(thisWeek, lastWeek, WEEK_WORDS);
  }

  // 이번 기간과 그 전 기간의 숫자로 기본 문장을 만듭니다. 기록이 모자라면 null 입니다.
  //
  // thisWeek: 지금 보는 기간의 숫자 / lastWeek: 비교하는 그 전 기간의 숫자 (WeeklyFacts 가 둘을 같이 계산합니다)
  // words: 기간을 부르는 말. 월간 리포트는 MONTH_WORDS 를 넘깁니다. (그러면 thisWeek 는 "이 4주" 의 숫자입니다)
  public static CoachSummary of(StudyFacts thisWeek, StudyFacts lastWeek, PeriodWords words) {
    FactValue completion = thisWeek.completionRate();
    // 규칙 0
    if (completion.value() == null || completion.sampleCount() < MIN_SAMPLES_FOR_COACH) {
      return null;
    }

    List<String> sentences = new ArrayList<>();
    List<WeeklyCoachRequest.Number> evidence = new ArrayList<>();

    // 규칙 1: 계획 대비 실제
    FactValue planRate = thisWeek.planRate();
    if (planRate.value() != null) {
      sentences.add(words.current() + "는 계획의 " + planRate.value() + "% 를 했어.");
      evidence.add(
          new WeeklyCoachRequest.Number(
              "계획 대비 실제", planRate.value() + "%", "계획 " + planRate.sampleCount() + "개"));

      Integer lastPlanRate = lastWeek.planRate().value();
      // Integer 끼리는 == 가 아니라 equals 로 비교해야 값이 같은지 알 수 있습니다.
      if (lastPlanRate != null && !lastPlanRate.equals(planRate.value())) {
        int gap = planRate.value() - lastPlanRate;
        sentences.add(
            words.previous() + "보다 " + Math.abs(gap) + "%p " + (gap > 0 ? "올랐어" : "내렸어") + ".");
        evidence.add(
            new WeeklyCoachRequest.Number(
                words.previous() + " 계획 대비 실제",
                lastPlanRate + "%",
                "계획 " + lastWeek.planRate().sampleCount() + "개"));
      }
    }

    // 규칙 2: 완주율
    sentences.add(
        "목표를 정한 타이머 "
            + completion.sampleCount()
            + "번 중 "
            + thisWeek.completedCount()
            + "번을 끝까지 했어. (완주율 "
            + completion.value()
            + "%)");
    evidence.add(
        new WeeklyCoachRequest.Number(
            "완주율", completion.value() + "%", completion.sampleCount() + "번"));

    // 규칙 3: 눈에 띄는 요일 (완주율이 가장 낮은 요일)
    int weakWeekday = -1;
    int weakRate = 101;
    List<FactValue> byWeekday = thisWeek.completionByWeekday();
    for (int weekday = 0; weekday < byWeekday.size(); weekday++) {
      FactValue fact = byWeekday.get(weekday);
      if (fact.value() == null || fact.sampleCount() < MIN_WEEKDAY_SAMPLES) {
        continue;
      }
      // 더 낮을 때만 바꾸므로, 완주율이 같으면 먼저 나온 요일이 남습니다.
      if (fact.value() < weakRate) {
        weakRate = fact.value();
        weakWeekday = weekday;
      }
    }
    if (weakWeekday != -1 && completion.value() - weakRate >= WEEKDAY_GAP) {
      String name = WEEKDAY_NAMES.get(weakWeekday);
      sentences.add(
          "눈에 띄는 건 "
              + name
              + "이야. "
              + name
              + " 완주율은 "
              + weakRate
              + "% 로 전체("
              + completion.value()
              + "%)보다 낮아.");
      evidence.add(
          new WeeklyCoachRequest.Number(
              name + " 완주율", weakRate + "%", byWeekday.get(weakWeekday).sampleCount() + "번"));
    }

    // 규칙 4: 권하는 말 (처음 맞는 것 하나)
    FactValue stopPoint = thisWeek.stopPoint();
    FactValue delay = thisWeek.startDelayMinutes();
    if (stopPoint.value() != null && stopPoint.sampleCount() >= MIN_STOPPED_SAMPLES) {
      sentences.add("중간에 멈춘 타이머는 목표의 " + stopPoint.value() + "% 쯤에서 멈췄어. 다음엔 목표를 조금 짧게 잡아 보면 어때?");
      evidence.add(
          new WeeklyCoachRequest.Number(
              "멈추는 지점", "목표의 " + stopPoint.value() + "%", stopPoint.sampleCount() + "번"));
    } else if (delay.value() != null && delay.value() > LATE_START_MINUTES) {
      sentences.add("계획한 시각보다 평균 " + delay.value() + "분 늦게 시작했어. 시작 시각에 알림을 맞춰 두면 어때?");
      evidence.add(
          new WeeklyCoachRequest.Number(
              "시작 지연", "평균 " + delay.value() + "분", "계획 " + delay.sampleCount() + "개"));
    } else {
      sentences.add("지금 흐름이 좋아. " + words.next() + "도 이만큼만 이어 가 보자.");
    }

    return new CoachSummary(sentences, evidence);
  }
}
