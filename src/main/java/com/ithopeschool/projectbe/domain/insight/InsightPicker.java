package com.ithopeschool.projectbe.domain.insight;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 인사이트 후보들에 점수를 매겨 보여 줄 1~2개를 고릅니다. (이슈 #41, docs/INSIGHT-PICK.md)
// 값만 계산하는 도구라서 객체를 만들 일이 없습니다. 후보를 만드는 일(분석 #36 ~ #40)과 저장하는 일은 부르는 쪽이 합니다.
//
// ■ 점수 (각 기준 0 ~ 100점, 담당 학생이 정함)
//   총점 = 의외성 × 0.4 + 확실성 × 0.3 + 실행 가능성 × 0.2 + 새로움 × 0.1   (마지막에 한 번만 반올림)
//   가중치를 이렇게 정한 이유: "놀라우면 기억에 남고, 틀리면 믿음을 잃으니" 의외성과 확실성을 크게 봤다.
//
//   의외성      온보딩 때 스스로 말한 것과 다르면 100 · 모르면(답이 없음) 50 · 같으면 0
//   확실성      기록 일수 50점 + 차이 크기 50점
//                 - 기록 일수: 비교하는 조건 중 적은 쪽의 일수. 14일이면 50점 만점 (7일이면 25점)
//                 - 차이 크기: 두 조건의 차이(%). 50% 이상이면 50점 만점 (20% 면 20점)
//   실행 가능성  다음에 해 볼 행동 하나가 붙어 있으면 100 · 없으면 0
//   새로움      한 번도 말하지 않았으면 100 · 마지막으로 말한 지 4주(28일) 이상이면 50 · 4주 안에 말했으면 0
//
// ■ 반응 버튼 (지난번에 같은 종류의 인사이트에 누른 반응)
//   몰랐는데 맞아  →  총점 +10 (100 을 넘지 않음)
//   이미 알던 거야 →  그대로
//   틀렸어        →  고르지 않음
//
// ■ 그 밖의 규칙
//   - 근거 날짜가 하나도 없는 후보는 고르지 않습니다. 모든 인사이트에는 근거 날짜가 붙어야 합니다.
//   - 비교 조건 중 하나라도 5일 미만이면 "가설" 로만 나갑니다. (InsightEvidence.isHypothesis) 고르는 데서 빼지는 않습니다.
//   - 총점이 높은 순서로 최대 2개를 고릅니다. 총점이 같으면 확실성이 높은 쪽, 그것도 같으면 종류 이름 순서입니다.
//   - "같은 종류" 는 후보의 key 로 구별합니다. (예: "morning-vs-night")
public final class InsightPicker {

  // 가중치 (합 100)
  static final int WEIGHT_SURPRISE = 40;
  static final int WEIGHT_CERTAINTY = 30;
  static final int WEIGHT_ACTION = 20;
  static final int WEIGHT_NOVELTY = 10;

  // 확실성: 적은 쪽 기록 일수가 이만큼이면 일수 점수 만점, 차이가 이만큼(%)이면 차이 점수 만점
  static final int FULL_CERTAINTY_DAYS = 14;
  static final int FULL_CERTAINTY_DIFFERENCE_PERCENT = 50;

  // 새로움: 이 기간(일) 안에 말한 적이 있으면 0점
  static final int RECENTLY_SHOWN_DAYS = 28;

  // "몰랐는데 맞아" 를 누른 종류에 더하는 점수
  static final int SURPRISED_BONUS = 10;

  // 한 번에 고르는 인사이트 수
  public static final int MAX_PICKS = 2;

  private InsightPicker() {}

  // 온보딩 때 스스로 말한 것과 이 발견을 견준 결과입니다.
  public enum SelfView {
    // 스스로 말한 것과 다름 (예: 저녁형이라고 했는데 오전에 더 잘 됨)
    DIFFERENT,
    // 온보딩 답이 없어서 견줄 수 없음
    UNKNOWN,
    // 스스로 말한 것과 같음
    SAME
  }

  // 인사이트 후보 하나. 분석(#36 ~ #40)이 규칙으로 만들어서 넘깁니다.
  // key: 인사이트의 종류. 같은 발견이면 같은 key 입니다 (예: "morning-vs-night")
  // sentence: 카드에 보여 줄 한 문장
  // evidenceDates: 이 결론의 근거가 된 날짜들
  // dayCounts: 비교하는 조건마다 쌓인 일수 (예: 오전 6일, 밤 4일 → [6, 4])
  // differencePercent: 두 조건의 차이(%). 예: 오전 평균 120분 · 밤 평균 80분 → 50
  // selfView: 온보딩 때 스스로 말한 것과 견준 결과
  // action: 다음에 해 볼 행동 하나. 없으면 null
  public record Candidate(
      String key,
      String sentence,
      List<LocalDate> evidenceDates,
      List<Integer> dayCounts,
      int differencePercent,
      SelfView selfView,
      String action) {}

  // 지난번까지 이 종류의 인사이트를 보여 준 기록입니다. 보여 준 적이 없는 종류는 넘기지 않습니다.
  // key: 인사이트의 종류 / lastShownDate: 마지막으로 보여 준 날 / lastResponse: 마지막에 누른 반응. 안 눌렀으면 null
  public record History(String key, LocalDate lastShownDate, InsightResponse lastResponse) {}

  // 고른 인사이트와 그 점수입니다. 점수는 왜 골랐는지 확인할 수 있게 같이 돌려줍니다.
  public record Picked(
      Candidate candidate,
      int surprise,
      int certainty,
      int actionability,
      int novelty,
      int total,
      boolean isHypothesis) {}

  // 후보들 중에서 보여 줄 인사이트를 최대 2개 고릅니다.
  // today: 오늘 날짜 (하루의 기준 새벽 4시. DayRule.dateOf 로 구한 값)
  public static List<Picked> pick(
      List<Candidate> candidates, List<History> histories, LocalDate today) {
    Map<String, History> historyByKey = new HashMap<>();
    for (History history : histories) {
      historyByKey.put(history.key(), history);
    }

    List<Picked> scored = new ArrayList<>();
    for (Candidate candidate : candidates) {
      History history = historyByKey.get(candidate.key());
      // 지난번에 "틀렸어" 를 누른 종류는 고르지 않습니다.
      if (history != null && history.lastResponse() == InsightResponse.WRONG) {
        continue;
      }
      // 근거 날짜가 없는 후보는 고르지 않습니다.
      if (candidate.evidenceDates() == null || candidate.evidenceDates().isEmpty()) {
        continue;
      }
      scored.add(score(candidate, history, today));
    }

    scored.sort(
        Comparator.comparingInt(Picked::total)
            .reversed()
            .thenComparing(Comparator.comparingInt(Picked::certainty).reversed())
            .thenComparing(picked -> picked.candidate().key()));
    return scored.size() > MAX_PICKS ? List.copyOf(scored.subList(0, MAX_PICKS)) : scored;
  }

  // 후보 하나의 점수를 매깁니다.
  static Picked score(Candidate candidate, History history, LocalDate today) {
    // 일수가 비어 있으면(null) 쌓인 날이 없는 것으로 봅니다. 그러면 확실성은 낮고 가설입니다.
    List<Integer> dayCounts = candidate.dayCounts() == null ? List.of() : candidate.dayCounts();
    int surprise = surpriseOf(candidate.selfView());
    int certainty = certaintyOf(dayCounts, candidate.differencePercent());
    int actionability = isBlank(candidate.action()) ? 0 : 100;
    int novelty = noveltyOf(history, today);

    // 기준 점수 × 가중치를 모두 더하고, 마지막에 한 번만 반올림합니다.
    double weighted =
        (surprise * WEIGHT_SURPRISE
                + certainty * WEIGHT_CERTAINTY
                + actionability * WEIGHT_ACTION
                + novelty * WEIGHT_NOVELTY)
            / 100.0;
    int total = (int) Math.round(weighted);
    if (history != null && history.lastResponse() == InsightResponse.SURPRISED) {
      total = Math.min(100, total + SURPRISED_BONUS);
    }

    return new Picked(
        candidate,
        surprise,
        certainty,
        actionability,
        novelty,
        total,
        InsightEvidence.isHypothesis(dayCounts));
  }

  // 의외성: 다름 100 · 모름 50 · 같음 0
  static int surpriseOf(SelfView selfView) {
    if (selfView == SelfView.DIFFERENT) {
      return 100;
    }
    if (selfView == SelfView.SAME) {
      return 0;
    }
    return 50;
  }

  // 확실성: 기록 일수 50점 + 차이 크기 50점. 마지막에 한 번만 반올림합니다.
  //
  //   적은 쪽 14일 · 차이 50%  →  50 + 50 = 100
  //   적은 쪽  7일 · 차이 20%  →  25 + 20 = 45
  //   적은 쪽  4일 · 차이 80%  →  14.28… + 50 = 64   (차이는 커도 기록이 적으면 덜 믿음)
  static int certaintyOf(List<Integer> dayCounts, int differencePercent) {
    int fewestDays = 0;
    if (!dayCounts.isEmpty()) {
      fewestDays = Integer.MAX_VALUE;
      for (int days : dayCounts) {
        fewestDays = Math.min(fewestDays, days);
      }
    }
    double dayPart =
        50.0 * Math.min(Math.max(fewestDays, 0), FULL_CERTAINTY_DAYS) / FULL_CERTAINTY_DAYS;
    double differencePart =
        50.0
            * Math.min(Math.max(differencePercent, 0), FULL_CERTAINTY_DIFFERENCE_PERCENT)
            / FULL_CERTAINTY_DIFFERENCE_PERCENT;
    return (int) Math.round(dayPart + differencePart);
  }

  // 새로움: 처음 100 · 마지막으로 말한 지 28일 이상 50 · 28일 안 0
  static int noveltyOf(History history, LocalDate today) {
    if (history == null || history.lastShownDate() == null) {
      return 100;
    }
    long daysSince = ChronoUnit.DAYS.between(history.lastShownDate(), today);
    return daysSince >= RECENTLY_SHOWN_DAYS ? 50 : 0;
  }

  private static boolean isBlank(String text) {
    return text == null || text.isBlank();
  }
}
