package com.ithopeschool.projectbe.domain.goal;

import java.util.ArrayList;
import java.util.List;

// "이번 주 목표" 한 줄을 제안하고, 달성했는지 판정합니다. (이슈 #88)
// 큰 목표(시험)를 이번 주에 닿을 수 있는 숫자 하나로 쪼갭니다. 한 주에 목표는 하나입니다.
// 달성했는지는 타이머·계획 기록에서 나온 숫자로 자동으로 정해집니다. "지켰어?" 를 묻지 않습니다.
// 제안도 판정도 AI 를 부르지 않고 코드로만 합니다.
//
// 목표의 종류
//   COMPLETION   완주율 ○% 넘기기
//   START_DELAY  시작 지연 ○분 안으로
//   PLAN_RATE    계획의 ○% 채우기
//   KEEP         지난주만큼 (완주율을 지난주 이상으로)
//
// FE 의 src/lib/weeklyGoal.ts 와 같은 규칙·같은 문장입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 목표가 어긋나지 않습니다.
//
// kind: 목표 종류 / target: 목표 숫자 / baseline: 제안할 때 본 지난주 숫자
// (target 과 baseline 은 START_DELAY 면 분, 나머지는 % 입니다)
public record WeeklyGoal(Kind kind, int target, int baseline) {

  // 목표의 종류입니다.
  public enum Kind {
    COMPLETION,
    START_DELAY,
    PLAN_RATE,
    KEEP
  }

  // 목표의 지금 상태입니다.
  public enum Status {
    // 달성
    ACHIEVED,
    // 미달
    MISSED,
    // 기록이 없어서 알 수 없음
    UNKNOWN
  }

  // 지난주에 "목표가 있는 타이머" 가 이만큼은 있어야 제안합니다. 이보다 적으면 숫자를 믿기 어렵습니다.
  public static final int MIN_SAMPLES_TO_SUGGEST = 5;

  // 완주율이 이 값(%) 미만이면 완주율 목표를 제안합니다.
  public static final int COMPLETION_RATE_LIMIT = 60;

  // 평균 시작 지연이 이 값(분)을 넘으면 시작 지연 목표를 제안합니다.
  public static final int START_DELAY_LIMIT_MINUTES = 15;

  // 계획 대비 실제가 이 값(%) 미만이면 계획 채우기 목표를 제안합니다.
  public static final int PLAN_RATE_LIMIT = 80;

  // 비율 목표는 지난주보다 이만큼(%p) 높게 잡습니다.
  public static final int RATE_STEP = 10;

  // 시작 지연 목표는 지난주보다 이만큼(분) 짧게 잡습니다.
  public static final int START_DELAY_STEP_MINUTES = 5;

  // 한 주의 기록에서 나온 숫자 중, 주간 목표가 쓰는 것만 모은 묶음입니다. (FE 의 Facts 중 일부)
  // 계산할 수 없는 숫자는 0 이 아니라 null 입니다. ("0%" 와 "모름" 은 다릅니다)
  //
  // completionRate: 완주율(%). 목표가 있는 타이머 중 목표를 채운 비율
  // completionSampleCount: 완주율을 계산한 "목표가 있는 타이머" 의 개수
  // startDelayMinutes: 평균 시작 지연(분). 계획한 시작 시각보다 얼마나 늦게 시작했는지
  // planRate: 계획 대비 실제(%). 실제 순공 ÷ 계획한 시간
  public record WeekFacts(
      Integer completionRate,
      int completionSampleCount,
      Integer startDelayMinutes,
      Integer planRate) {}

  // 목표의 지금 상태를 담습니다.
  // current: 지금 숫자. 아직 계산할 수 없으면 null / status: 달성·미달·알 수 없음 / percent: 진행 막대 길이(0~100)
  public record Progress(Integer current, Status status, int percent) {}

  // 지난주 숫자를 보고 이번 주 목표 후보를 만듭니다. 맨 앞이 가장 먼저 제안할 목표이고,
  // "다른 걸로" 를 누르면 다음 후보로 넘어갑니다. 제안할 수 없으면 빈 목록입니다.
  //
  // 규칙 (위에서부터 맞는 것을 순서대로 후보에 넣습니다)
  //   0. 지난주에 목표가 있는 타이머가 5번 미만이면 제안하지 않습니다. (빈 목록)
  //   1. 완주율이 60% 미만           →  완주율 (지난주 + 10)% 넘기기
  //   2. 평균 시작 지연이 15분 넘음   →  시작 지연 (지난주 − 5)분 안으로
  //   3. 계획 대비 실제가 80% 미만    →  계획의 (지난주 + 10)% 채우기
  //   4. 마지막 후보는 항상 "지난주만큼" 입니다. 1 ~ 3 이 하나도 안 맞으면 이것만 남습니다.
  //   비율 목표는 100% 를, 시작 지연 목표는 0분을 넘어가지 않습니다.
  //
  // 예시 (입력 → 결과)
  //   완주율 55%(20번), 지연 10분, 계획 대비 90%   →  [완주율 65% 넘기기, 지난주만큼(55%)]
  //   완주율 70%(10번), 지연 23분, 계획 대비 90%   →  [시작 지연 18분 안으로, 지난주만큼(70%)]
  //   완주율 70%(10번), 지연 5분, 계획 대비 64%    →  [계획의 74% 채우기, 지난주만큼(70%)]
  //   완주율 80%(10번), 지연 5분, 계획 대비 95%    →  [지난주만큼(80%)]
  //   완주율 40%(10번), 지연 30분, 계획 대비 50%
  //     →  [완주율 50% 넘기기, 시작 지연 25분 안으로, 계획의 60% 채우기, 지난주만큼(40%)]
  //   목표가 있는 타이머가 4번뿐                   →  []
  //   완주율 70%(10번), 지연 모름(null), 계획 없음(null)
  //     →  [지난주만큼(70%)]   (모르는 숫자로는 목표를 만들지 않습니다)
  public static List<WeeklyGoal> candidates(WeekFacts lastWeek) {
    Integer completion = lastWeek.completionRate();
    // 규칙 0. 완주율을 모르는 경우(null)도 여기서 걸러집니다.
    if (completion == null || lastWeek.completionSampleCount() < MIN_SAMPLES_TO_SUGGEST) {
      return List.of();
    }

    List<WeeklyGoal> candidates = new ArrayList<>();

    if (completion < COMPLETION_RATE_LIMIT) {
      candidates.add(
          new WeeklyGoal(Kind.COMPLETION, Math.min(completion + RATE_STEP, 100), completion));
    }

    Integer delay = lastWeek.startDelayMinutes();
    if (delay != null && delay > START_DELAY_LIMIT_MINUTES) {
      candidates.add(
          new WeeklyGoal(Kind.START_DELAY, Math.max(delay - START_DELAY_STEP_MINUTES, 0), delay));
    }

    Integer planRate = lastWeek.planRate();
    if (planRate != null && planRate < PLAN_RATE_LIMIT) {
      candidates.add(new WeeklyGoal(Kind.PLAN_RATE, Math.min(planRate + RATE_STEP, 100), planRate));
    }

    candidates.add(new WeeklyGoal(Kind.KEEP, completion, completion));
    return candidates;
  }

  // 목표를 한 줄로 보여줄 글자입니다.
  //
  //   완주율 목표 65        →  "완주율 65% 넘기기"
  //   시작 지연 목표 18     →  "시작 지연 18분 안으로"
  //   계획 채우기 목표 74   →  "계획의 74% 채우기"
  //   지난주만큼 80         →  "이번 주도 지난주만큼 (완주율 80%)"
  public String title() {
    return switch (kind) {
      case COMPLETION -> "완주율 " + target + "% 넘기기";
      case START_DELAY -> "시작 지연 " + target + "분 안으로";
      case PLAN_RATE -> "계획의 " + target + "% 채우기";
      case KEEP -> "이번 주도 지난주만큼 (완주율 " + target + "%)";
    };
  }

  // 왜 이 목표를 제안하는지 한 줄로 알려 줍니다. 지난주 숫자를 그대로 보여줍니다.
  //
  //   완주율 목표 65, 지난주 55  →  "지난주 완주율은 55% 였어. 10%p 만 올려 보자."
  public String reason() {
    return switch (kind) {
      case COMPLETION -> "지난주 완주율은 " + baseline + "% 였어. " + (target - baseline) + "%p 만 올려 보자.";
      case START_DELAY -> "지난주엔 평균 " + baseline + "분 늦게 시작했어. " + (baseline - target) + "분만 당겨 보자.";
      case PLAN_RATE -> "지난주엔 계획의 " + baseline + "% 를 했어. " + (target - baseline) + "%p 만 더 채워 보자.";
      case KEEP -> "지난주 숫자가 괜찮았어. 이번 주도 그만큼만 하면 돼.";
    };
  }

  // 그 주의 숫자로 목표의 지금 상태를 판정합니다. 주가 끝난 뒤에 부르면 그 주의 최종 결과입니다.
  // 같은 숫자를 넣으면 몇 번을 불러도 결과가 같습니다.
  //
  // 규칙
  //   - 완주율·지난주만큼 : 완주율이 목표 이상이면 달성
  //   - 계획 채우기       : 계획 대비 실제가 목표 이상이면 달성
  //   - 시작 지연         : 평균 시작 지연이 목표 이하이면 달성 (작을수록 좋은 숫자)
  //   - 그 숫자를 계산할 수 없으면(null) 달성도 미달도 아닌 "알 수 없음" 입니다.
  //   - 막대 길이: 비율 목표는 (지금 ÷ 목표), 시작 지연은 (목표 ÷ 지금). 달성했으면 100 입니다.
  //
  // 예시
  //   "완주율 65% 넘기기", 지금 완주율 60%      →  지금 60, 미달, 막대 92
  //   "완주율 65% 넘기기", 지금 완주율 70%      →  지금 70, 달성, 막대 100
  //   "시작 지연 18분 안으로", 지금 24분        →  지금 24, 미달, 막대 75
  //   "시작 지연 18분 안으로", 지금 10분        →  지금 10, 달성, 막대 100
  //   "계획의 74% 채우기", 계획이 없음(null)    →  지금 null, 알 수 없음, 막대 0
  public Progress progress(WeekFacts facts) {
    Integer current =
        switch (kind) {
          case START_DELAY -> facts.startDelayMinutes();
          case PLAN_RATE -> facts.planRate();
          case COMPLETION, KEEP -> facts.completionRate();
        };

    if (current == null) {
      return new Progress(null, Status.UNKNOWN, 0);
    }

    // 시작 지연만 "작을수록 좋은" 숫자입니다.
    boolean isAchieved = kind == Kind.START_DELAY ? current <= target : current >= target;
    if (isAchieved) {
      return new Progress(current, Status.ACHIEVED, 100);
    }

    // 정수끼리 나누면 소수점이 버려져서, 한쪽을 소수(double)로 바꿔서 나눕니다.
    // FE 와 같은 순서(먼저 나누고 100 을 곱한 뒤 소수점 버림)로 계산합니다.
    double ratio = kind == Kind.START_DELAY ? (double) target / current : (double) current / target;
    return new Progress(current, Status.MISSED, (int) Math.floor(ratio * 100));
  }
}
