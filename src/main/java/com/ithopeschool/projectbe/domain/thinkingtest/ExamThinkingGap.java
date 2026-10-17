package com.ithopeschool.projectbe.domain.thinkingtest;

import com.ithopeschool.projectbe.domain.bias.Weakness;
import com.ithopeschool.projectbe.domain.call.CallSummary;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.exam.ScoreGap;
import com.ithopeschool.projectbe.domain.stats.StudyFacts;
import com.ithopeschool.projectbe.domain.thinkingtest.ThinkingGap.ReasonGap;
import com.ithopeschool.projectbe.domain.thinkingtest.ThinkingGap.Verdict;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 시험 결과 화면의 "내 생각 vs 시험 전 2주의 현실" 입니다. (이슈 #35 · #40, 선생님 요청 "시험 망친 뒤 내 생각과 현실의 차이")
//
//   생각 : 생각 습관 테스트에서 꼽은 약점 칩 + "계획할 때" 축 (낙관 · 균형 · 현실)
//   현실 : 시험 14일 전 ~ 하루 전 (ScoreGap 의 "시험 전 2주 순공" 과 같은 기간)
//          ① 그만두거나 못 채운 이유 — 타이머를 목표 전에 멈추며 고른 이유 + 통화에서 말한 "어제 못 채운 이유"
//          ② 계획 대비 실제 — 계획한 시간 중 실제로 공부한 비율 (StudyFacts, 주간 리포트와 같은 식)
//
// ■ ① 은 ThinkingGap 과 같은 판정(MATCHED · SMALLER · BIGGER)인데, 횟수 대신 "그 이유가 나온 날 수" 로 셉니다.
//   같은 날 타이머에서도 '폰·SNS', 통화에서도 '폰·SNS' 라고 했으면 하루로 칩니다. (한 번의 일을 두 번 세지 않게)
//   통화의 이유는 "어제" 계획을 못 채운 이유라서, 부르는 쪽에서 통화한 날의 하루 전 날짜로 넘깁니다.
//
// ■ ② 계획 대비 실제가 80% 보다 낮으면 "계획보다 덜 했다" 로 봅니다. (이 기능에서 정한 기준입니다)
//     테스트에서 낙관 + 80% 미만   → MATCHED  "생각이 맞았어"
//     테스트에서 낙관 + 80% 이상   → SMALLER  "생각보다 계획을 잘 지켰어"
//     낙관이 아님     + 80% 미만   → BIGGER   "계획을 생각보다 크게 잡았을 수 있어"
//     낙관이 아님     + 80% 이상   → 판정 없음
//   기간의 공부 계획이 5개보다 적으면 견주지 않습니다. (주간 리포트가 숫자를 쓰는 표본 기준과 같은 5)
//
// ■ 이유가 나온 날이 3일보다 적으면 enoughData 가 false 이고, "더 지켜봐야 해" 를 먼저 말합니다.
//
// 숫자·판정·문장 모두 코드가 만듭니다. AI 는 쓰지 않습니다.
// 문장은 한 일(행동)과 숫자만 말하고, 사람을 평가하는 말은 쓰지 않습니다. (CLAUDE.md 4장)
public record ExamThinkingGap(
    LocalDate examDate,
    LocalDate fromDate,
    LocalDate toDate,
    int reasonDays,
    boolean enoughData,
    List<ReasonGap> reasons,
    PlanGap plan,
    List<Weakness> unverifiable,
    List<String> sentences) {

  // 시험 며칠 전부터 보는지 (시험 전 2주)
  public static final int LOOKBACK_DAYS = ScoreGap.STUDY_LOOKBACK_DAYS;
  // 이유 쪽 판정이 믿을 만하려면 필요한 최소 날 수
  public static final int MIN_REASON_DAYS = 3;
  // 계획 쪽을 견주려면 필요한 최소 공부 계획 수
  public static final int MIN_PLANS = 5;
  // 계획 대비 실제(%)가 이 값보다 낮으면 "계획보다 덜 했다" 로 봅니다
  public static final int UNDER_PLAN_BELOW = 80;

  // 그만두거나 못 채운 이유 하나. date: 그 일이 있던 날 / reason: 이유
  public record ReasonFact(LocalDate date, MissReason reason) {}

  // 계획 쪽 결과.
  // style: 테스트의 "계획할 때" 축 결과 (낙관 · 균형 · 현실)
  // planRate: 계획 대비 실제(%). 계획이 모자라 견주지 않았으면 null
  // planCount: 기간의 공부 계획 수 / verdict: 판정. 견주지 않았거나 판정이 없으면 null
  public record PlanGap(String style, Integer planRate, int planCount, Verdict verdict) {}

  // 시험 전 2주의 첫날 (시험 14일 전).   시험 2026-10-20  →  2026-10-06
  public static LocalDate fromDateOf(LocalDate examDate) {
    return examDate.minusDays(LOOKBACK_DAYS);
  }

  // 시험 전 2주의 마지막 날 (시험 하루 전). 시험 날의 공부는 "시험 전" 이 아니라서 뺍니다.
  //   시험 2026-10-20  →  2026-10-19
  public static LocalDate toDateOf(LocalDate examDate) {
    return examDate.minusDays(1);
  }

  // 견줍니다.
  // thoughtWeaknesses: 테스트에서 꼽은 약점들
  // planning: 테스트의 "계획할 때" 축 결과 (StudyStyle.stylesOf 의 첫 번째)
  // facts: 그만두거나 못 채운 이유들. 기간 밖의 날짜가 섞여 있어도 됩니다. 기간 안의 것만 셉니다.
  // planRate: 기간의 계획 대비 실제 (StudyFacts.planRate, 노는 계획·노는 기록은 뺀 것)
  public static ExamThinkingGap of(
      LocalDate examDate,
      Collection<Weakness> thoughtWeaknesses,
      StudyStyle.AxisResult planning,
      List<ReasonFact> facts,
      StudyFacts.FactValue planRate) {
    LocalDate from = fromDateOf(examDate);
    LocalDate to = toDateOf(examDate);

    // 이유마다 "그 이유가 나온 날들". Set 이라 같은 날은 한 번만 들어갑니다.
    Map<MissReason, Set<LocalDate>> daysByReason = new EnumMap<>(MissReason.class);
    // 견주는 이유가 하나라도 나온 날들
    Set<LocalDate> allReasonDays = new HashSet<>();
    for (ReasonFact fact : facts) {
      boolean inPeriod = !fact.date().isBefore(from) && !fact.date().isAfter(to);
      // 아픔·기타는 견주지 않아서 날 수에도 넣지 않습니다. (ThinkingGap 과 같은 이유)
      if (!inPeriod || !ThinkingGap.COMPARED_REASONS.contains(fact.reason())) {
        continue;
      }
      Set<LocalDate> days = daysByReason.get(fact.reason());
      if (days == null) {
        days = new HashSet<>();
        daysByReason.put(fact.reason(), days);
      }
      days.add(fact.date());
      allReasonDays.add(fact.date());
    }
    Map<MissReason, Integer> dayCounts = new EnumMap<>(MissReason.class);
    for (Map.Entry<MissReason, Set<LocalDate>> entry : daysByReason.entrySet()) {
      dayCounts.put(entry.getKey(), entry.getValue().size());
    }

    List<ReasonGap> reasons = ThinkingGap.reasonGapsOf(thoughtWeaknesses, dayCounts);
    PlanGap plan = planGapOf(planning, planRate);
    int reasonDays = allReasonDays.size();
    boolean enoughData = reasonDays >= MIN_REASON_DAYS;
    return new ExamThinkingGap(
        examDate,
        from,
        to,
        reasonDays,
        enoughData,
        reasons,
        plan,
        ThinkingGap.unverifiableOf(thoughtWeaknesses),
        sentencesOf(enoughData, reasonDays, reasons, plan));
  }

  // 계획 쪽 판정 (위 주석 ② 의 표)
  static PlanGap planGapOf(StudyStyle.AxisResult planning, StudyFacts.FactValue planRate) {
    String style = planning.label();
    if (planRate.value() == null || planRate.sampleCount() < MIN_PLANS) {
      return new PlanGap(style, null, planRate.sampleCount(), null);
    }
    boolean thought = style.equals(planning.axis().highLabel());
    boolean underPlan = planRate.value() < UNDER_PLAN_BELOW;
    Verdict verdict = null;
    if (thought) {
      verdict = underPlan ? Verdict.MATCHED : Verdict.SMALLER;
    } else if (underPlan) {
      verdict = Verdict.BIGGER;
    }
    return new PlanGap(style, planRate.value(), planRate.sampleCount(), verdict);
  }

  // 학생에게 보여 주는 문장들. 모자란 자료 안내를 먼저, 그다음 "컸어" → "작았어" → "맞았어" 순서입니다.
  static List<String> sentencesOf(
      boolean enoughData, int reasonDays, List<ReasonGap> reasons, PlanGap plan) {
    List<String> sentences = new ArrayList<>();
    if (!enoughData) {
      sentences.add("시험 전 2주에 그만두거나 못 채운 이유가 남은 날이 " + reasonDays + "일뿐이라, 이유 쪽은 더 지켜봐야 해.");
    }
    if (plan.planRate() == null) {
      sentences.add(
          plan.planCount() == 0
              ? "시험 전 2주에는 공부 계획이 없어서 계획 쪽은 견주지 않았어."
              : "시험 전 2주의 공부 계획이 " + plan.planCount() + "개뿐이라 계획 쪽은 견주지 않았어.");
    }
    for (Verdict verdict : List.of(Verdict.BIGGER, Verdict.SMALLER, Verdict.MATCHED)) {
      for (ReasonGap gap : reasons) {
        if (gap.verdict() == verdict) {
          sentences.add(reasonSentence(gap));
        }
      }
      if (plan.verdict() == verdict) {
        sentences.add(planSentence(plan));
      }
    }
    if (enoughData && reasons.isEmpty() && plan.verdict() == null) {
      sentences.add("시험 전 2주의 기록은 내가 생각한 나와 크게 다르지 않았어.");
    }
    return sentences;
  }

  //   BIGGER  → "꼽지 않았던 '계획 과다' 쪽이 시험 전 2주 중 3일, 그만두거나 못 채운 이유로 나왔어. 생각보다 큰 원인일 수 있어."
  //   SMALLER → "내가 꼽은 약점 '게임', '유튜브·영상' 쪽은 시험 전 2주 동안 그만두거나 못 채운 이유로 나오지 않았어."
  //   MATCHED → "내가 꼽은 약점 '잠·늦잠' 쪽은 시험 전 2주에도 '피곤·수면' 때문에 그만두거나 못 채운 날이 2일 있었어. 생각이 맞았어."
  static String reasonSentence(ReasonGap gap) {
    String reason = "'" + CallSummary.missReasonLabel(gap.reason()) + "'";
    return switch (gap.verdict()) {
      case BIGGER ->
          "꼽지 않았던 "
              + reason
              + " 쪽이 시험 전 2주 중 "
              + gap.actualCount()
              + "일, 그만두거나 못 채운 이유로 나왔어. 생각보다 큰 원인일 수 있어.";
      case SMALLER ->
          "내가 꼽은 약점 "
              + ThinkingGap.weaknessLabels(gap.thoughtBy())
              + " 쪽은 시험 전 2주 동안 그만두거나 못 채운 이유로 나오지 않았어.";
      case MATCHED ->
          "내가 꼽은 약점 "
              + ThinkingGap.weaknessLabels(gap.thoughtBy())
              + " 쪽은 시험 전 2주에도 "
              + reason
              + " 때문에 그만두거나 못 채운 날이 "
              + gap.actualCount()
              + "일 있었어. 생각이 맞았어.";
    };
  }

  //   MATCHED → "테스트에서 계획할 때 '낙관' 쪽이었고, 시험 전 2주도 계획한 시간의 58%를 했어. 생각이 맞았어."
  //   SMALLER → "테스트에서 계획할 때 '낙관' 쪽이었지만, 시험 전 2주는 계획한 시간의 92%를 했어. 생각보다 계획을 잘 지켰어."
  //   BIGGER  → "테스트에서 계획할 때 '균형' 쪽이었는데, 시험 전 2주는 계획한 시간의 58%를 했어. 계획을 생각보다 크게 잡았을 수 있어."
  static String planSentence(PlanGap plan) {
    String style = "테스트에서 계획할 때 '" + plan.style() + "' 쪽이었";
    String rate = "계획한 시간의 " + plan.planRate() + "%를 했어.";
    return switch (plan.verdict()) {
      case MATCHED -> style + "고, 시험 전 2주도 " + rate + " 생각이 맞았어.";
      case SMALLER -> style + "지만, 시험 전 2주는 " + rate + " 생각보다 계획을 잘 지켰어.";
      case BIGGER -> style + "는데, 시험 전 2주는 " + rate + " 계획을 생각보다 크게 잡았을 수 있어.";
    };
  }
}
