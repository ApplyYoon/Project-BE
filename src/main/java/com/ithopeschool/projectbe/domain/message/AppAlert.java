package com.ithopeschool.projectbe.domain.message;

import com.ithopeschool.projectbe.domain.goal.WeeklyGoal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 앱 알림의 문구와 말투를 정합니다. (이슈 #31)
// AI 를 부르지 않고, 여기에 정해 둔 문장에 숫자를 끼워서 만듭니다. (비용 0. 숫자는 코드가 계산한 것만 씁니다)
// "지금 보낼지" 는 EventMessage.pick 이 정하고, 이 파일은 "어떤 말투로, 어떤 문장을" 만 정합니다.
//
// 문구 규칙
//   - 외모·성적·능력을 깎아내리는 말, 다른 사람과 비교하는 말은 쓰지 않습니다. 지적은 행동에만 합니다.
//   - 문장마다 말투(careLevel)와 독설 수위(harshness)를 정직하게 답니다.
//   - 모든 종류에 회복(0) 말투 문장이 하나 이상 있어야 합니다.
//     한도를 0 으로 낮춘 사람과, 안전 브레이크가 켜진 사람에게도 보낼 문장이 필요하기 때문입니다.
//
// FE 의 appAlertSentences · getAppAlertCareLevel · getAppAlertText (src/lib/appAlert.ts) 와 같은 규칙·같은
// 문장입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 앱 안의 알림과 서버가 보내는 알림의 말이 어긋나지 않습니다.
public final class AppAlert {

  // 정해 둔 문장 하나입니다.
  // careLevel: 이 문장의 말투 (0 회복 / 1 당근 / 2 넛지)
  // harshness: 독설 수위 (1 부드럽게 / 2 담백하게 / 3 따끔하게). 설정의 "독설 허용 수위" 와 같은 기준입니다.
  // text: 문장. {time} 처럼 중괄호로 감싼 자리에 값이 끼워집니다.
  public record Sentence(int careLevel, int harshness, String text) {}

  // 주간 목표 알림에 쓸 숫자 이름과 단위입니다. 예: 완주율, %
  public record GoalLabel(String label, String unit) {}

  // 종류별 문장입니다. 중괄호 자리에 들어가는 값:
  //   PLAN_LATE       {time} 계획한 시작 시각 "19:00" · {plan} 과목과 할 일 "국어 비문학 3지문"
  //   TIMER_DONE      {subject} 과목 이름 · {minutes} 약속한 목표 시간(분)
  //   GOAL_DONE       {target} 오늘 계획의 목표 시간 "4시간"
  //   MISSED_3_DAYS   (없음)
  //   WEEK_GOAL_…     {label} 무슨 숫자인지 "완주율" · {before} 지난주에 제안할 때 본 숫자 "55%"
  //                   {after} 그 주의 결과 "66%" · {target} 목표 숫자 "65%"
  private static final Map<AppAlertType, List<Sentence>> SENTENCES =
      Map.of(
          AppAlertType.PLAN_LATE,
          List.of(
              new Sentence(2, 1, "{time} {plan}, 시작할 시간이야"),
              new Sentence(2, 2, "{time} {plan}, 아직 시작 전이야. 타이머부터 켜자."),
              new Sentence(1, 1, "{time} {plan} 할 시간이야. 지금 켜면 계획대로야."),
              new Sentence(0, 1, "{time} {plan}, 준비되면 10분만 해볼까?")),
          AppAlertType.TIMER_DONE,
          List.of(
              new Sentence(1, 1, "{subject} {minutes}분 완주!"),
              new Sentence(0, 1, "{subject} {minutes}분을 채웠어. 잠깐 쉬어도 돼.")),
          AppAlertType.GOAL_DONE,
          List.of(
              new Sentence(1, 1, "오늘 계획 {target}, 다 해냈어"),
              new Sentence(0, 1, "오늘 계획 {target}을 다 채웠어. 이제 푹 쉬어.")),
          AppAlertType.MISSED_3_DAYS,
          List.of(
              new Sentence(0, 1, "요즘 힘들지. 오늘은 10분만 해볼까?"),
              new Sentence(0, 1, "괜찮아, 천천히 다시 시작하자. 오늘은 한 가지만 해보자.")),
          AppAlertType.WEEK_GOAL_ACHIEVED,
          List.of(
              new Sentence(1, 1, "지난주 목표 달성! {label} {before} → {after}"),
              new Sentence(0, 1, "지난주 목표를 채웠어. {label} {before} → {after}. 이번 주는 편하게 가도 돼.")),
          AppAlertType.WEEK_GOAL_MISSED,
          List.of(
              new Sentence(2, 1, "지난주 {label} {before} → {after} (목표 {target}). 이번 주에 다시 해보자."),
              new Sentence(2, 2, "지난주 목표는 못 채웠어. {label} {before} → {after} (목표 {target})"),
              new Sentence(1, 1, "지난주 {label} {before} → {after}. 목표 {target}까지 이번 주에 한 번 더 해보자."),
              new Sentence(0, 1, "지난주 {label} {before} → {after}. 이번 주는 가볍게 다시 시작하자.")));

  // "{time}" 처럼 중괄호로 감싼 이름을 찾습니다. \w+ 는 영어 글자·숫자·밑줄이 한 개 이상 이어진 것입니다.
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)\\}");

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private AppAlert() {}

  // 그 종류에 정해 둔 문장들을 돌려줍니다.
  public static List<Sentence> sentencesOf(AppAlertType type) {
    return SENTENCES.get(type);
  }

  // 이 알림을 어느 말투(관리 강도)로 보낼지 정합니다.
  //
  //   1) 안전 브레이크가 켜져 있으면 무엇이든 회복(0) 말투입니다.
  //   2) 아니면 그 종류의 기본 말투를 쓰되, 사용자가 정한 관리 강도 최대 한도를 넘지 않습니다.
  //
  //   (PLAN_LATE, 한도 4, 브레이크 꺼짐)  →  2   (넛지 그대로)
  //   (PLAN_LATE, 한도 1, 브레이크 꺼짐)  →  1   (한도가 당근이면 당근 말투로)
  //   (PLAN_LATE, 한도 0, 브레이크 꺼짐)  →  0
  //   (PLAN_LATE, 한도 4, 브레이크 켜짐)  →  0   (안전 브레이크)
  //   (GOAL_DONE, 한도 4, 브레이크 꺼짐)  →  1   (기본 말투보다 세지지는 않습니다)
  public static int careLevelOf(AppAlertType type, int maxCareLevel, boolean isSafetyBrakeOn) {
    if (isSafetyBrakeOn) {
      return 0;
    }
    return Math.min(type.getCareLevel(), maxCareLevel);
  }

  // 알림 문장을 만듭니다. 여러 문장이 남으면 그중 하나를 아무거나 고릅니다.
  public static String textOf(
      AppAlertType type, int careLevel, int maxHarshness, Map<String, String> values) {
    return textOf(type, careLevel, maxHarshness, values, Math.random());
  }

  // 알림 문장을 만듭니다.
  //
  //   1) careLevel 보다 센 말투의 문장은 뺍니다. 남은 것 중 가장 가까운(가장 높은) 말투의 문장만 남깁니다.
  //   2) 그중 허용 수위(maxHarshness)보다 센 문장은 뺍니다. 다 빠지면 그중 가장 순한 문장을 씁니다.
  //   3) 남은 문장 중 하나를 고르고, 중괄호 자리에 values 를 끼웁니다. 값이 없는 자리는 빈 글자가 됩니다.
  //
  // random: 0 이상 1 미만의 수. 남은 문장 중 몇 번째를 쓸지 정합니다. 0 이면 항상 첫 번째 문장입니다.
  //         (테스트에서 결과를 고정하려고 받습니다. 평소에는 위의 textOf 를 부르면 됩니다)
  //
  // 예시 (values = time "19:00", plan "국어 비문학 3지문", random = 0)
  //   (PLAN_LATE, 말투 2, 수위 3)  →  "19:00 국어 비문학 3지문, 시작할 시간이야"
  //                                   (random 이 0.9 면 "19:00 국어 비문학 3지문, 아직 시작 전이야. 타이머부터 켜자.")
  //   (PLAN_LATE, 말투 2, 수위 1)  →  "19:00 국어 비문학 3지문, 시작할 시간이야"   (수위 2 문장은 나오지 않습니다)
  //   (PLAN_LATE, 말투 1, 수위 3)  →  "19:00 국어 비문학 3지문 할 시간이야. 지금 켜면 계획대로야."
  //   (PLAN_LATE, 말투 0, 수위 3)  →  "19:00 국어 비문학 3지문, 준비되면 10분만 해볼까?"
  // 예시 (values = subject "국어", minutes "50")
  //   (TIMER_DONE, 말투 1, 수위 3)  →  "국어 50분 완주!"
  //   (TIMER_DONE, 말투 0, 수위 3)  →  "국어 50분을 채웠어. 잠깐 쉬어도 돼."
  public static String textOf(
      AppAlertType type,
      int careLevel,
      int maxHarshness,
      Map<String, String> values,
      double random) {
    if (careLevel < 0) {
      throw new IllegalArgumentException("말투(careLevel)는 0 이상이어야 해요. 받은 값: " + careLevel);
    }
    if (random < 0 || random >= 1) {
      throw new IllegalArgumentException("random 은 0 이상 1 미만이어야 해요. 받은 값: " + random);
    }
    List<Sentence> sentences = SENTENCES.get(type);

    // 1) 말투: careLevel 이하 중 가장 높은 단계
    int usedLevel = 0;
    for (Sentence sentence : sentences) {
      if (sentence.careLevel() <= careLevel && sentence.careLevel() > usedLevel) {
        usedLevel = sentence.careLevel();
      }
    }
    List<Sentence> sameLevel = new ArrayList<>();
    for (Sentence sentence : sentences) {
      if (sentence.careLevel() == usedLevel) {
        sameLevel.add(sentence);
      }
    }

    // 2) 수위: 허용 수위 이하. 하나도 없으면 가장 순한 것
    List<Sentence> candidates = new ArrayList<>();
    for (Sentence sentence : sameLevel) {
      if (sentence.harshness() <= maxHarshness) {
        candidates.add(sentence);
      }
    }
    if (candidates.isEmpty()) {
      int lowest = Integer.MAX_VALUE;
      for (Sentence sentence : sameLevel) {
        lowest = Math.min(lowest, sentence.harshness());
      }
      for (Sentence sentence : sameLevel) {
        if (sentence.harshness() == lowest) {
          candidates.add(sentence);
        }
      }
    }

    // 3) 하나 고르고 값 끼우기
    Sentence picked = candidates.get((int) Math.floor(random * candidates.size()));
    Matcher matcher = PLACEHOLDER.matcher(picked.text());
    // 찾은 자리마다 그 이름의 값으로 바꿉니다. (quoteReplacement 는 값에 든 $ 같은 글자를 그대로 넣기 위한 것입니다)
    return matcher.replaceAll(
        found -> Matcher.quoteReplacement(values.getOrDefault(found.group(1), "")));
  }

  // 주간 목표의 종류마다 알림에 쓸 숫자 이름과 단위를 돌려줍니다.
  public static GoalLabel weekGoalLabelOf(WeeklyGoal.Kind kind) {
    return switch (kind) {
      case COMPLETION, KEEP -> new GoalLabel("완주율", "%");
      case PLAN_RATE -> new GoalLabel("계획 대비 실제", "%");
      case START_DELAY -> new GoalLabel("시작 지연", "분");
    };
  }

  // 지난주 목표 결과 알림(WEEK_GOAL_ACHIEVED · WEEK_GOAL_MISSED)의 중괄호 자리에 넣을 값을 만듭니다.
  //
  //   "완주율 65% 넘기기"(지난주 55%), 그 주의 결과 66
  //     →  label "완주율", before "55%", after "66%", target "65%"
  //
  // goal: 지난주에 고른 목표 / current: 그 주의 결과 숫자 (WeeklyGoal.progress 의 current)
  public static Map<String, String> weekGoalValues(WeeklyGoal goal, int current) {
    GoalLabel goalLabel = weekGoalLabelOf(goal.kind());
    return Map.of(
        "label", goalLabel.label(),
        "before", goal.baseline() + goalLabel.unit(),
        "after", current + goalLabel.unit(),
        "target", goal.target() + goalLabel.unit());
  }
}
