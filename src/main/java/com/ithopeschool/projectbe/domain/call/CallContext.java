package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.exam.ExamDday;
import com.ithopeschool.projectbe.domain.psychtest.PsychScale;
import com.ithopeschool.projectbe.domain.psychtest.PsychScore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// AI 통화 · 상담 채팅 대본 끝에 붙이는 "[기록에서 꺼낸 것]" 칸을 만듭니다. (2026-10-18 "AI 채팅이 데이터를 최대한 쓰게")
// 학생 카드(CallCard)에는 없던 기록 — 시험 · 목표 · 가입 심리검사 — 을 AI 가 대화에서 꺼낼 수 있게 합니다.
// 값만 계산하는 도구라서 객체를 만들 일이 없습니다. DB 에서 꺼내는 일은 CallContextLoader 가 합니다.
//
// 정해 둔 규칙
//   1. 숫자(남은 날, 점수)는 코드가 정리하고, AI 는 적힌 것만 말합니다. (CallScript 와 같은 원칙)
//   2. 꺼낼 것이 하나도 없으면 빈 글자입니다. 대본이 그대로입니다.
//   3. 학생이 직접 쓴 글은 목표 제목 · 걸림돌 · 방법만, 앞 40자까지만 넣습니다. (줄바꿈은 띄어쓰기로)
//   4. 심리검사는 "높음" 인 성향만 넣습니다. 관리 선호는 말투 규칙(관리 강도)이 따로 정해서 넣지 않습니다.
//   5. 위로하는 통화에서는 이 칸을 붙이지 않습니다. 부르는 쪽(CallService)이 정합니다.
public final class CallContext {

  // 학생이 쓴 글을 넣을 때의 최대 글자 수 (규칙 3)
  static final int MAX_TEXT_LENGTH = 40;

  // 다음 시험. name: 시험 이름 / daysUntil: 남은 날 (오늘이면 0)
  public record NextExam(String name, int daysUntil) {}

  // 지난 시험의 과목 하나. subject: 과목 이름 / expected: 시험 전에 적은 예상 점수 / actual: 실제 점수
  public record ScoreLine(String subject, int expected, int actual) {}

  // 실제 점수를 적은 가장 최근 시험. scores: 실제 점수를 적은 과목들
  public record PastExam(String name, List<ScoreLine> scores) {}

  // 진행 중인 목표 하나. level: "지금 5등급"(점수형) 또는 "전체 300쪽"(완주형). 없으면 null
  // obstacle · plan: 목표를 만들 때 스스로 적은 걸림돌과 방법. 없으면 null
  public record GoalLine(String title, int daysUntil, String level, String obstacle, String plan) {}

  // 대본에 넣을 기록 전부. 없는 것은 null 이나 빈 목록입니다.
  // psychScores: 가입 심리검사의 척도별 점수 (PsychScore.scoresOf). 검사를 안 했으면 빈 맵
  public record Facts(
      NextExam nextExam,
      PastExam pastExam,
      List<GoalLine> goals,
      Map<PsychScale, Double> psychScores) {

    public static final Facts NONE = new Facts(null, null, List.of(), Map.of());
  }

  private CallContext() {}

  // "[기록에서 꺼낸 것]" 칸 전체. 앞에 빈 줄 두 개를 붙여서, 대본 끝에 그대로 이어 붙이면 됩니다.
  // 꺼낼 것이 하나도 없으면 빈 글자입니다. (규칙 2)
  public static String section(Facts facts) {
    List<String> body = new ArrayList<>();
    body.addAll(examLines(facts.nextExam(), facts.pastExam()));
    body.addAll(goalLines(facts.goals()));
    body.addAll(psychLines(facts.psychScores()));
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

  // 심리검사 줄: "높음" 인 성향마다 AI 가 어떻게 도울지 한 줄씩 (규칙 4)
  static List<String> psychLines(Map<PsychScale, Double> psychScores) {
    List<String> lines = new ArrayList<>();
    for (PsychScale scale : PsychScale.values()) {
      Double score = psychScores.get(scale);
      String help = helpFor(scale);
      if (score == null || help == null || PsychScore.levelOf(score) != PsychScore.Level.HIGH) {
        continue;
      }
      lines.add("  - " + scale.label() + ": " + help);
    }
    if (lines.isEmpty()) {
      return lines;
    }
    lines.add(0, "- 가입 심리검사에서 높게 나온 성향이야. 성격이 아니라 지금의 공부 습관이니, 단정하지 말고 이렇게 도와:");
    return lines;
  }

  // 성향마다 AI 가 돕는 방법. 관리 선호는 말투 규칙이 따로 정해서 null 입니다. (규칙 4)
  private static String helpFor(PsychScale scale) {
    return switch (scale) {
      case START_RESISTANCE -> "시작이 어려운 편이야. 큰 목표 대신 \"5분만\", \"한 문제만\" 처럼 시작을 작게 제안해.";
      case DISTRACTION -> "폰·딴생각에 흔들리는 편이야. 의지보다 폰을 다른 곳에 두는 것 같은 환경 바꾸기를 먼저 제안해.";
      case PLAN_OPTIMISM -> "계획을 크게 세우는 편이야. 양을 늘리자고 하지 말고, 줄여서 끝까지 하는 쪽을 권해.";
      case RECORD_BURDEN -> "기록을 귀찮아하는 편이야. 길게 적으라고 하지 말고 한 번 누르면 되는 것만 부탁해.";
      case FAIL_SENSITIVITY -> "한 번 무너지면 자책하는 편이야. 못 한 날을 '실패' 라고 부르지 말고 다음 한 걸음만 말해.";
      case EXTERNAL_ATTRIBUTION -> "원인을 밖에서 찾는 편이야. 탓하지 말고 \"그중 내가 바꿀 수 있는 것 하나\" 를 물어.";
      case CARE_PREFERENCE -> null;
    };
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
