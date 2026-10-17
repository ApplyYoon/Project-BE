package com.ithopeschool.projectbe.domain.thinkingtest;

import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.bias.Weakness;
import com.ithopeschool.projectbe.domain.call.CallSummary;
import com.ithopeschool.projectbe.domain.care.MissReason;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// "내가 생각한 나" 와 "실제 기록" 을 견줍니다. (이슈 #35 · #40, 선생님 요청 "내 생각과 현실의 차이")
//
//   생각 : 생각 습관 테스트에서 많이 해당한 습관 + 내가 꼽은 약점 칩
//   현실 : 최근 통화에서 실제로 보인 습관 + 실제로 계획을 못 채운 이유 (call_records)
//
// ■ 약점 ↔ 못 채운 이유 (짝은 Weakness 에 적혀 있습니다. 게임·폰·유튜브 → 폰·SNS 등)
//   이유마다 아래 셋 중 하나로 판정합니다. 어디에도 안 맞으면 목록에 넣지 않습니다.
//     MATCHED  꼽았고, 실제로도 1번 이상 나옴          → "생각이 맞았어"
//     SMALLER  꼽았는데, 실제로는 한 번도 안 나옴      → "생각보다 작았어"
//     BIGGER   안 꼽았는데, 실제로 2번 이상 나옴       → "생각보다 컸어"
//   아픔·기타는 견주지 않습니다. 정말 어쩔 수 없던 일이거나 무엇인지 알 수 없어서입니다.
//   짝이 없는 약점(집중력 · 기분·스트레스)은 "통화 기록으로는 확인할 수 없는 약점" 으로 따로 돌려줍니다.
//
// ■ 테스트의 습관 ↔ 통화에서 보인 습관 — 같은 세 가지 판정입니다. (테스트에서 "많이 해당" = 평균 5 초과)
//
// ■ 통화가 3번보다 적으면 enoughData 가 false 입니다. 판정은 하지만 "더 지켜봐야 해" 를 먼저 말합니다.
//
// 숫자와 판정은 코드가 하고, 문장(sentences)도 정해 둔 틀로 만듭니다. AI 는 이 문장을 보고 통화에서 묻기만 합니다.
public record ThinkingGap(
    int days,
    int callCount,
    boolean enoughData,
    List<ReasonGap> reasons,
    List<HabitGap> habits,
    List<Weakness> unverifiable,
    List<String> sentences) {

  // 판정이 믿을 만하려면 필요한 최소 통화 수
  public static final int MIN_CALLS = 3;
  // "생각보다 컸어" 로 보려면 실제로 나온 최소 횟수 (한 번은 우연일 수 있어서)
  public static final int BIGGER_MIN_COUNT = 2;

  public enum Verdict {
    MATCHED,
    SMALLER,
    BIGGER
  }

  // 통화 한 번에서 견주는 데 쓰는 값. 마무리했고 위기 표현이 없던 통화만 넘깁니다.
  // foundBias: 대답에서 보인 습관 / missReason: 못 채운 이유. 없으면 둘 다 null
  public record CallFact(LocalDate date, CognitiveBias foundBias, MissReason missReason) {}

  // 못 채운 이유 하나의 판정. thoughtBy: 이 이유와 짝인, 내가 꼽은 약점들 / actualCount: 실제로 나온 횟수
  public record ReasonGap(
      MissReason reason, List<Weakness> thoughtBy, int actualCount, Verdict verdict) {}

  // 습관 하나의 판정. thought: 테스트에서 많이 해당했는지 / foundCount: 통화에서 보인 횟수
  public record HabitGap(CognitiveBias bias, boolean thought, int foundCount, Verdict verdict) {}

  // 견주는 이유들 (아픔 · 기타는 뺍니다). ExamThinkingGap 도 같이 씁니다.
  static final List<MissReason> COMPARED_REASONS =
      List.of(
          MissReason.PHONE,
          MissReason.TIRED,
          MissReason.UNWILLING,
          MissReason.EVENT,
          MissReason.TOO_MUCH);

  // 견줍니다.
  // thoughtHabits: 테스트에서 많이 해당한 습관들 (StudyStyle.topHabitsOf) / thoughtWeaknesses: 내가 꼽은 약점들
  // calls: 기간 안의 통화들 (마무리했고 위기 표현이 없던 것) / days: 며칠 동안인지 (문장에 씁니다)
  public static ThinkingGap of(
      Collection<CognitiveBias> thoughtHabits,
      Collection<Weakness> thoughtWeaknesses,
      List<CallFact> calls,
      int days) {
    // 이유마다 통화에서 나온 횟수
    Map<MissReason, Integer> reasonCounts = new EnumMap<>(MissReason.class);
    for (CallFact call : calls) {
      if (call.missReason() != null) {
        reasonCounts.put(call.missReason(), reasonCounts.getOrDefault(call.missReason(), 0) + 1);
      }
    }
    List<ReasonGap> reasons = reasonGapsOf(thoughtWeaknesses, reasonCounts);

    List<HabitGap> habits = new ArrayList<>();
    for (CognitiveBias bias : CognitiveBias.values()) {
      int count = 0;
      for (CallFact call : calls) {
        if (call.foundBias() == bias) {
          count++;
        }
      }
      boolean thought = thoughtHabits.contains(bias);
      Verdict verdict = verdictOf(thought, count);
      if (verdict != null) {
        habits.add(new HabitGap(bias, thought, count, verdict));
      }
    }

    boolean enoughData = calls.size() >= MIN_CALLS;
    return new ThinkingGap(
        days,
        calls.size(),
        enoughData,
        reasons,
        habits,
        unverifiableOf(thoughtWeaknesses),
        sentencesOf(enoughData, calls.size(), days, reasons, habits));
  }

  // 못 채운 이유마다 판정합니다. 판정이 없는 이유는 목록에 넣지 않습니다. (ExamThinkingGap 도 같은 규칙으로 씁니다)
  // actualCounts: 이유 → 실제로 나온 횟수. 없는 이유는 0번으로 봅니다.
  static List<ReasonGap> reasonGapsOf(
      Collection<Weakness> thoughtWeaknesses, Map<MissReason, Integer> actualCounts) {
    List<ReasonGap> reasons = new ArrayList<>();
    for (MissReason reason : COMPARED_REASONS) {
      List<Weakness> thoughtBy = new ArrayList<>();
      for (Weakness weakness : Weakness.values()) {
        if (thoughtWeaknesses.contains(weakness) && weakness.getMissReason() == reason) {
          thoughtBy.add(weakness);
        }
      }
      int count = actualCounts.getOrDefault(reason, 0);
      Verdict verdict = verdictOf(!thoughtBy.isEmpty(), count);
      if (verdict != null) {
        reasons.add(new ReasonGap(reason, thoughtBy, count, verdict));
      }
    }
    return reasons;
  }

  // 짝이 없어서 기록으로는 확인할 수 없는, 내가 꼽은 약점들 (집중력 · 기분·스트레스)
  static List<Weakness> unverifiableOf(Collection<Weakness> thoughtWeaknesses) {
    List<Weakness> unverifiable = new ArrayList<>();
    for (Weakness weakness : Weakness.values()) {
      if (thoughtWeaknesses.contains(weakness) && weakness.getMissReason() == null) {
        unverifiable.add(weakness);
      }
    }
    return unverifiable;
  }

  // 판정 규칙 (위 주석의 표). 어디에도 안 맞으면 null
  static Verdict verdictOf(boolean thought, int count) {
    if (thought && count >= 1) {
      return Verdict.MATCHED;
    }
    if (thought) {
      return Verdict.SMALLER;
    }
    if (count >= BIGGER_MIN_COUNT) {
      return Verdict.BIGGER;
    }
    return null;
  }

  // 학생에게 보여 주는 문장들. "생각보다 컸어" → "생각보다 작았어" → "맞았어" 순서입니다. (새로 알게 되는 것부터)
  static List<String> sentencesOf(
      boolean enoughData, int callCount, int days, List<ReasonGap> reasons, List<HabitGap> habits) {
    List<String> sentences = new ArrayList<>();
    if (!enoughData) {
      sentences.add("아직 통화가 " + callCount + "번뿐이라 더 지켜봐야 해. 통화가 쌓일수록 정확해져.");
    }
    for (Verdict verdict : List.of(Verdict.BIGGER, Verdict.SMALLER, Verdict.MATCHED)) {
      for (ReasonGap gap : reasons) {
        if (gap.verdict() == verdict) {
          sentences.add(reasonSentence(gap, days));
        }
      }
      for (HabitGap gap : habits) {
        if (gap.verdict() == verdict) {
          sentences.add(habitSentence(gap));
        }
      }
    }
    if (enoughData && reasons.isEmpty() && habits.isEmpty()) {
      sentences.add("아직은 내가 생각한 나와 실제 기록이 크게 다르지 않아.");
    }
    return sentences;
  }

  //   BIGGER  → "꼽지 않았던 '계획 과다' 쪽이 실제로는 못 채운 이유로 3번 나왔어. 생각보다 큰 원인일 수 있어."
  //   SMALLER → "내가 꼽은 약점 '게임', '유튜브·영상' 쪽은 최근 14일 동안 실제로 못 채운 이유로 나오지 않았어."
  //   MATCHED → "내가 꼽은 약점 '잠·늦잠' 쪽은 실제로도 '피곤·수면' 때문에 못 채운 날이 2번 있었어. 생각이 맞았어."
  static String reasonSentence(ReasonGap gap, int days) {
    String reason = "'" + CallSummary.missReasonLabel(gap.reason()) + "'";
    return switch (gap.verdict()) {
      case BIGGER ->
          "꼽지 않았던 " + reason + " 쪽이 실제로는 못 채운 이유로 " + gap.actualCount() + "번 나왔어. 생각보다 큰 원인일 수 있어.";
      case SMALLER ->
          "내가 꼽은 약점 "
              + weaknessLabels(gap.thoughtBy())
              + " 쪽은 최근 "
              + days
              + "일 동안 실제로 못 채운 이유로 나오지 않았어.";
      case MATCHED ->
          "내가 꼽은 약점 "
              + weaknessLabels(gap.thoughtBy())
              + " 쪽은 실제로도 "
              + reason
              + " 때문에 못 채운 날이 "
              + gap.actualCount()
              + "번 있었어. 생각이 맞았어.";
    };
  }

  //   BIGGER  → "테스트에서는 높지 않았던 '미래의 나 믿기' 쪽 생각이 통화에서 2번 보였어."
  //   SMALLER → "테스트에서 높게 나온 '계획 오류' 쪽 생각은 통화에서 아직 보이지 않았어."
  //   MATCHED → "테스트에서 높게 나온 '계획 오류' 쪽 생각이 통화에서도 3번 보였어."
  static String habitSentence(HabitGap gap) {
    String habit = "'" + gap.bias().getLabel() + "'";
    return switch (gap.verdict()) {
      case BIGGER -> "테스트에서는 높지 않았던 " + habit + " 쪽 생각이 통화에서 " + gap.foundCount() + "번 보였어.";
      case SMALLER -> "테스트에서 높게 나온 " + habit + " 쪽 생각은 통화에서 아직 보이지 않았어.";
      case MATCHED -> "테스트에서 높게 나온 " + habit + " 쪽 생각이 통화에서도 " + gap.foundCount() + "번 보였어.";
    };
  }

  //   [GAME, VIDEO]  →  "'게임', '유튜브·영상'"
  static String weaknessLabels(List<Weakness> weaknesses) {
    List<String> labels = new ArrayList<>();
    for (Weakness weakness : weaknesses) {
      labels.add("'" + weakness.getLabel() + "'");
    }
    return String.join(", ", labels);
  }

  // "생각보다 컸어" · "생각보다 작았어" 인 것만 문장으로. AI 통화에서 이야기할 거리입니다. (맞았던 것은 뺍니다)
  public List<String> differenceSentences() {
    List<String> sentences = new ArrayList<>();
    for (Verdict verdict : List.of(Verdict.BIGGER, Verdict.SMALLER)) {
      for (ReasonGap gap : reasons) {
        if (gap.verdict() == verdict) {
          sentences.add(reasonSentence(gap, days));
        }
      }
      for (HabitGap gap : habits) {
        if (gap.verdict() == verdict) {
          sentences.add(habitSentence(gap));
        }
      }
    }
    return sentences;
  }
}
