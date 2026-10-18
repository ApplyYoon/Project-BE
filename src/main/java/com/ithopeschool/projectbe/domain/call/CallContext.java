package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.exam.ExamDday;
import java.util.ArrayList;
import java.util.List;

// AI 통화 · 상담 채팅 대본 끝에 붙이는 "[기록에서 꺼낸 것]" 칸을 만듭니다. (2026-10-18 "AI 채팅이 데이터를 최대한 쓰게")
// 학생 카드(CallCard)에는 없던 기록 — 시험 · 목표 · "스스로 말한 것과 다른 기록" · 실제로 못 채운 이유 — 을
// AI 가 대화에서 꺼낼 수 있게 합니다.
// 값만 계산하는 도구라서 객체를 만들 일이 없습니다. DB 에서 꺼내는 일은 CallContextLoader 가 합니다.
// (가입 성향 검사는 상담 채팅의 [학생 성향] 칸이 따로 넣습니다. CallScript.psychSection)
//
// 정해 둔 규칙
//   1. 숫자(남은 날, 점수, 몇 번 중 몇 번)는 코드가 정리하고, AI 는 적힌 것만 말합니다. (CallScript 와 같은 원칙)
//   2. 꺼낼 것이 하나도 없으면 빈 글자입니다. 대본이 그대로입니다.
//   3. 학생이 직접 쓴 글은 목표 제목 · 걸림돌 · 방법만, 앞 40자까지만 넣습니다. (줄바꿈은 띄어쓰기로)
//   4. 위로하는 통화에서는 이 칸을 붙이지 않습니다. 부르는 쪽(CallService)이 정합니다.
//   5. 아침형/저녁형 답과 기록은 "답과 반대 시간대가 20%p 이상 높을 때" 만 넣습니다. 두 시간대 모두 카드가 4번 이상이어야 합니다.
//      (기획서의 "인지 왜곡 교정": 스스로 말한 것과 실제가 다를 때 학생이 알아차리게 묻습니다)
//   6. 못 채운 이유는 2번 이상 나온 것만, 많은 것부터 2개까지 넣습니다.
public final class CallContext {

  // 학생이 쓴 글을 넣을 때의 최대 글자 수 (규칙 3)
  static final int MAX_TEXT_LENGTH = 40;
  // 시간대 하나에 있어야 하는 카드 수 (규칙 5). 적으면 우연일 수 있어서 말하지 않습니다.
  static final int MIN_BAND_CARDS = 4;
  // 아침과 저녁의 다 채운 비율이 이만큼(%p) 이상 차이 나야 말합니다. (규칙 5)
  static final int MIN_GAP_POINTS = 20;
  // 못 채운 이유를 넣으려면 나와야 하는 횟수와, 넣는 최대 개수 (규칙 6)
  static final int MIN_CAUSE_COUNT = 2;
  static final int MAX_CAUSES = 2;

  // 다음 시험. name: 시험 이름 / daysUntil: 남은 날 (오늘이면 0)
  public record NextExam(String name, int daysUntil) {}

  // 지난 시험의 과목 하나. subject: 과목 이름 / expected: 시험 전에 적은 예상 점수 / actual: 실제 점수
  public record ScoreLine(String subject, int expected, int actual) {}

  // 실제 점수를 적은 가장 최근 시험. scores: 실제 점수를 적은 과목들
  public record PastExam(String name, List<ScoreLine> scores) {}

  // 진행 중인 목표 하나. level: "지금 5등급"(점수형) 또는 "전체 300쪽"(완주형). 없으면 null
  // obstacle · plan: 목표를 만들 때 스스로 적은 걸림돌과 방법. 없으면 null
  public record GoalLine(String title, int daysUntil, String level, String obstacle, String plan) {}

  // 가입 때 답한 아침형/저녁형과 최근 2주 카드 기록. (규칙 5)
  // saidNight: 저녁형이라고 답했으면 true, 아침형이면 false
  // morningDone / morningTotal: 아침(04:00 ~ 11:59 시작) 카드 중 체크리스트까지 다 채운 날 / 있던 날 (휴식한 날은 뺌)
  // nightDone / nightTotal: 저녁(18:00 ~ 03:59 시작) 카드 중 체크리스트까지 다 채운 날 / 있던 날
  // ("했다" 만 보면 끝까지 못 한 날도 한 날이 되어서, 기획서 5.1 의 계획 축(체크리스트 달성률)으로 셉니다)
  public record RhythmGap(
      boolean saidNight, int morningDone, int morningTotal, int nightDone, int nightTotal) {}

  // 못 채운 이유 하나와 최근 2주에 나온 횟수 (예: "피곤·수면", 6)
  public record CauseCount(String label, int count) {}

  // 대본에 넣을 기록 전부. 없는 것은 null 이나 빈 목록입니다.
  public record Facts(
      NextExam nextExam,
      PastExam pastExam,
      List<GoalLine> goals,
      RhythmGap rhythmGap,
      List<CauseCount> causes) {

    public static final Facts NONE = new Facts(null, null, List.of());

    // 시험 · 목표만 있을 때
    public Facts(NextExam nextExam, PastExam pastExam, List<GoalLine> goals) {
      this(nextExam, pastExam, goals, null, List.of());
    }
  }

  private CallContext() {}

  // "[기록에서 꺼낸 것]" 칸 전체. 앞에 빈 줄 두 개를 붙여서, 대본 끝에 그대로 이어 붙이면 됩니다.
  // 꺼낼 것이 하나도 없으면 빈 글자입니다. (규칙 2)
  public static String section(Facts facts) {
    List<String> body = new ArrayList<>();
    body.addAll(examLines(facts.nextExam(), facts.pastExam()));
    body.addAll(goalLines(facts.goals()));
    body.addAll(rhythmLines(facts.rhythmGap()));
    body.addAll(causeLines(facts.causes()));
    if (body.isEmpty()) {
      return "";
    }
    List<String> lines = new ArrayList<>();
    lines.add("");
    lines.add("");
    lines.add("[기록에서 꺼낸 것]");
    lines.add("- 아래는 이 학생의 기록을 코드가 정리한 사실이야. 적힌 것만 말하고 숫자를 지어내지 마.");
    lines.add("- 한 번에 하나만, 오늘 할 일과 이어서 자연스럽게 꺼내. 목록을 읽어 주듯 늘어놓지 마.");
    lines.addAll(body);
    return String.join("\n", lines);
  }

  // 시험 줄: 다음 시험의 남은 날, 지난 시험의 예상 → 실제 점수
  static List<String> examLines(NextExam nextExam, PastExam pastExam) {
    List<String> lines = new ArrayList<>();
    if (nextExam != null) {
      lines.add(
          "- 다음 시험: "
              + nextExam.name()
              + " ("
              + ExamDday.text(nextExam.daysUntil())
              + "). 남은 날만 말하고 겁주지 마.");
    }
    if (pastExam != null && !pastExam.scores().isEmpty()) {
      List<String> scores = new ArrayList<>();
      for (ScoreLine score : pastExam.scores()) {
        scores.add(score.subject() + " " + score.expected() + " → " + score.actual() + "점");
      }
      lines.add("- 지난 시험 " + pastExam.name() + " (예상 → 실제): " + String.join(", ", scores));
      lines.add("  - 점수로 다른 사람과 비교하거나 깎아내리지 마. 예상과 실제의 차이는 \"다음에 무엇을 바꿀지\" 로만 이어 줘.");
    }
    return lines;
  }

  // 목표 줄: 제목 · 남은 날 · 지금 수준, 그리고 스스로 적은 걸림돌과 방법
  static List<String> goalLines(List<GoalLine> goals) {
    List<String> lines = new ArrayList<>();
    for (GoalLine goal : goals) {
      String line = "- 목표: 「" + cut(goal.title()) + "」 (" + ExamDday.text(goal.daysUntil());
      if (goal.level() != null) {
        line += ", " + cut(goal.level());
      }
      lines.add(line + ")");
      if (goal.obstacle() != null) {
        lines.add("  - 스스로 적은 걸림돌: 「" + cut(goal.obstacle()) + "」");
      }
      if (goal.plan() != null) {
        lines.add("  - 그때 하기로 한 방법: 「" + cut(goal.plan()) + "」");
      }
    }
    if (!lines.isEmpty()) {
      lines.add("- 목표는 오늘 할 일과 이어 줘. 걸림돌이 오늘 또 나오면, 스스로 정한 방법을 떠올리게 해.");
    }
    return lines;
  }

  // 아침형/저녁형 답과 다른 기록 줄 (규칙 5). 답과 같거나, 차이가 작거나, 카드가 적으면 빈 목록입니다.
  //   저녁형이라고 답함 · 아침 10번 중 9번(90%) · 저녁 12번 중 6번(50%) → 두 줄
  //   저녁형이라고 답함 · 아침 50% · 저녁 60%                         → 없음 (답과 같은 쪽이 높음)
  static List<String> rhythmLines(RhythmGap gap) {
    if (gap == null || gap.morningTotal() < MIN_BAND_CARDS || gap.nightTotal() < MIN_BAND_CARDS) {
      return List.of();
    }
    int morningRate = Math.round(gap.morningDone() * 100f / gap.morningTotal());
    int nightRate = Math.round(gap.nightDone() * 100f / gap.nightTotal());
    // 답과 반대 시간대가 얼마나 더 높은지
    int against = gap.saidNight() ? morningRate - nightRate : nightRate - morningRate;
    if (against < MIN_GAP_POINTS) {
      return List.of();
    }
    String said = gap.saidNight() ? "저녁형" : "아침형";
    String better = gap.saidNight() ? "아침" : "저녁";
    String worse = gap.saidNight() ? "저녁" : "아침";
    return List.of(
        "- 가입 때 스스로 '"
            + said
            + "' 이라고 답했어. 그런데 최근 2주 기록에서 체크리스트까지 다 채운 날은 아침 카드 "
            + gap.morningTotal()
            + "번 중 "
            + gap.morningDone()
            + "번("
            + morningRate
            + "%), 저녁 카드 "
            + gap.nightTotal()
            + "번 중 "
            + gap.nightDone()
            + "번("
            + nightRate
            + "%)이야.",
        "  - 단정하지 말고 \"기록을 보니 "
            + better
            + "이 더 잘 되는 것 같은데, 어때?\" 처럼 학생이 스스로 알아차리게 물어. 맞다고 하면 "
            + worse
            + "에 하던 카드 하나를 "
            + better
            + "으로 옮겨 보자고 제안해.");
  }

  // 실제로 못 채운 이유 줄 (규칙 6). 2번 이상 나온 것만, 많은 것부터 2개까지
  static List<String> causeLines(List<CauseCount> causes) {
    List<String> picked = new ArrayList<>();
    for (CauseCount cause : causes) {
      if (cause.count() >= MIN_CAUSE_COUNT && picked.size() < MAX_CAUSES) {
        picked.add(cause.label() + " " + cause.count() + "번");
      }
    }
    if (picked.isEmpty()) {
      return List.of();
    }
    return List.of(
        "- 최근 2주 카드를 못 채웠을 때 고른 이유: " + String.join(", ", picked),
        "  - 탓하지 말고, 이 이유가 오늘 또 나오면 할 수 있는 작은 대안 하나를 같이 정해.");
  }

  // 학생이 쓴 글을 대본에 넣을 모양으로 다듬습니다. 줄바꿈은 띄어쓰기로, 40자가 넘으면 자르고 "…" 를 붙입니다. (규칙 3)
  static String cut(String text) {
    String oneLine = text.replaceAll("\\s+", " ").trim();
    if (oneLine.length() <= MAX_TEXT_LENGTH) {
      return oneLine;
    }
    return oneLine.substring(0, MAX_TEXT_LENGTH) + "…";
  }
}
