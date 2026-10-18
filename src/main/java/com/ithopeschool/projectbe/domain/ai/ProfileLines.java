package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.care.ToneGuide;
import com.ithopeschool.projectbe.domain.psychtest.PsychScale;
import com.ithopeschool.projectbe.domain.psychtest.PsychScore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

// 심리검사 결과로 "성향 요약 3줄" 을 만듭니다. (이슈 #237 표 9번, 기획서 4.1 "결과 화면: 성향 요약 3줄")
// 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
//
// 원칙: 고르는 것은 코드, 말만 AI.
//   어느 척도 3개를 말할지는 코드가 고르고(pick), AI 는 척도마다 한 줄씩 "이런 편일 수 있어" 를 씁니다.
//
// ■ 척도 고르기 (pick): 가운데(4)에서 가장 멀리 떨어진 척도 3개. 같으면 척도 순서(기획서 6.1 표 순서).
//   가장 높거나 낮은 성향이 그 학생을 가장 잘 드러내서입니다.
//   예: 시작 저항 6.0 · 계획 낙관 2.0 · 주의 분산 5.0 · 나머지 4.0  →  시작 저항(2) · 계획 낙관(2) · 주의 분산(1)
//
// ■ 말투: 사람에게 이름(○○형)을 붙이지 않고, 2주 동안 기록으로 확인할 "가설" 로 씁니다. (생각 습관 테스트 결과와 같은 원칙)
// ⚠️ 이름·학교 같은 개인정보는 넣지 않습니다. 척도 이름 · 점수 · 높낮이만 넣습니다.
public final class ProfileLines {

  // 줄 수
  static final int LINE_COUNT = 3;
  // 한 줄의 최대 글자 수. 결과 화면에 한 줄로 보이게 짧게 잡았습니다.
  public static final int MAX_LINE_LENGTH = 60;
  // 숫자를 지어내지 않게, 줄에는 숫자를 쓰지 않습니다. (점수는 화면이 따로 보여 줍니다)
  private static final Pattern DIGIT = Pattern.compile("\\d");

  // 고른 척도 하나와 그 점수
  public record Pick(PsychScale scale, double score) {}

  // 심리검사의 답이 믿기 어려울 때(거의 다 같은 답 · 1-2-1-2 되풀이) AI 를 부르지 않고 쓰는 3줄입니다. (2026-10-18)
  // 성향을 말하지 않고, 다시 해 보거나 기록으로 같이 보자고만 합니다. (PsychAnswerCheck)
  public static final List<String> UNSURE_LINES =
      List.of(
          "답이 거의 다 같아서 아직 성향을 말하기 어려워", "시간 날 때 한 번 더 해 보면 더 잘 맞춰 볼게", "공부 기록이 쌓이면 기록으로도 같이 볼게");

  // GPT 가 돌려주는 JSON 모양 그대로입니다.   { "lines": ["…", "…", "…"] }
  public record Answer(List<String> lines) {}

  private ProfileLines() {}

  // 위의 규칙으로 척도 3개를 고릅니다. scores 는 척도 순서대로입니다.
  public static List<Pick> pick(Map<PsychScale, Double> scores) {
    List<Pick> all = new ArrayList<>();
    for (PsychScale scale : PsychScale.values()) {
      all.add(new Pick(scale, scores.getOrDefault(scale, 4.0)));
    }
    // 같으면 원래 순서를 지킵니다. (List.sort 는 같은 값의 순서를 바꾸지 않습니다)
    all.sort(Comparator.comparingDouble((Pick p) -> Math.abs(p.score() - 4.0)).reversed());
    return all.subList(0, LINE_COUNT);
  }

  // GPT 에게 보낼 질문 하나
  public static AiQuestion question(List<Pick> picks) {
    return new AiQuestion(
        "profile_lines",
        instructions(),
        input(picks),
        AiSchema.object(Map.of("lines", AiSchema.stringList())));
  }

  // GPT 가 지켜야 하는 규칙입니다.
  static String instructions() {
    List<String> lines = new ArrayList<>();
    lines.add("너는 고등학생이 공부 앱에서 처음 한 성향 검사 결과를 세 줄로 정리해 주는 도우미야.");
    lines.add("1. 입력의 척도마다 한 줄씩, 같은 순서로 lines 에 적어. 개수와 순서를 바꾸지 마.");
    lines.add("2. \"~하는 편일 수 있어\" 처럼 앞으로 2주 기록으로 확인할 가설로 써. 단정하지 마.");
    lines.add("3. 사람에게 이름(○○형, ○○한 사람)을 붙이지 마. 공부할 때 보이는 행동으로만 말해.");
    lines.add("4. 높으면 그 행동이 자주 나올 수 있다는 뜻, 낮으면 덜 나올 수 있다는 뜻이야. 좋고 나쁨으로 말하지 마.");
    lines.add("5. 숫자는 쓰지 마. 한 줄은 " + MAX_LINE_LENGTH + "자까지.");
    lines.add("6. " + ToneGuide.NO_PUT_DOWN_RULE);
    return String.join("\n", lines);
  }

  // GPT 에게 주는 입력입니다.
  //   척도 (이 순서대로 한 줄씩):
  //   1. 시작 저항 — 높음 (마음먹고 시작하기까지 꾸물거림)
  //   2. 계획 낙관 — 낮음 (계획을 과하게 세움)
  static String input(List<Pick> picks) {
    StringBuilder input = new StringBuilder("척도 (이 순서대로 한 줄씩):");
    for (int i = 0; i < picks.size(); i++) {
      Pick pick = picks.get(i);
      input
          .append("\n")
          .append(i + 1)
          .append(". ")
          .append(pick.scale().label())
          .append(" — ")
          .append(PsychScore.levelOf(pick.score()).label())
          .append(" (")
          .append(descriptionOf(pick.scale()))
          .append(")");
    }
    return input.toString();
  }

  // GPT 의 답을 검사합니다. 하나라도 쓸 수 없으면 null 이고, FE 는 기본 문장을 씁니다.
  //   줄 수가 3이어야 하고, 줄마다 앞뒤 공백을 지운 뒤 비었거나 60자를 넘거나 숫자 · 깎아내리는 말이 있으면 안 됩니다.
  public static List<String> from(Answer answer) {
    if (answer == null || answer.lines() == null || answer.lines().size() != LINE_COUNT) {
      return null;
    }
    List<String> lines = new ArrayList<>();
    for (String raw : answer.lines()) {
      String line = raw == null ? "" : raw.trim();
      if (line.isEmpty()
          || line.length() > MAX_LINE_LENGTH
          || DIGIT.matcher(line).find()
          || ToneGuide.hasBannedExpression(line)) {
        return null;
      }
      lines.add(line);
    }
    return lines;
  }

  // 척도가 무엇을 재는지 한 줄 (FE psychScales 의 description 과 같음)
  private static String descriptionOf(PsychScale scale) {
    return switch (scale) {
      case START_RESISTANCE -> "마음먹고 시작하기까지 꾸물거림";
      case DISTRACTION -> "공부 중 폰·딴생각에 흔들림";
      case PLAN_OPTIMISM -> "계획을 과하게 세움";
      case RECORD_BURDEN -> "입력 자체를 귀찮아함";
      case FAIL_SENSITIVITY -> "한 번 무너지면 포기·자책";
      case EXTERNAL_ATTRIBUTION -> "원인을 밖에서 찾음";
      case CARE_PREFERENCE -> "따끔한 관리를 원함";
    };
  }
}
