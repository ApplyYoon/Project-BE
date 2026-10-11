package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.call.dto.CallSessionRequest;
import com.ithopeschool.projectbe.domain.plan.AchievementRate;
import java.util.ArrayList;
import java.util.List;

// AI 통화의 "대본"(AI 에게 줄 지침)을 만드는 곳입니다. 값만 계산하는 도구라서 객체를 만들 일이 없습니다. (이슈 #28)
//
// 통화는 학생이 쌓은 기록을 보고 달라져야 합니다.
//   - 말투   : 오늘의 관리 강도와 최근 기분으로 정합니다.            (toneRule)
//   - 질문   : 어제 계획을 채웠는지, 못 채운 이유가 무엇인지로 정합니다. (yesterdayStep, todayStep)
//   - 사실   : 숫자는 코드가 정리해서 건네고, AI 는 그 숫자만 말합니다.   (facts)
//
// 원칙: 계산은 코드가 합니다. AI 가 직접 계산하면 틀리거나 지어낼 수 있습니다. (기획서 v2 3장)
//
// ⚠️ 소리는 학생의 브라우저와 OpenAI 사이를 직접 오가서, 서버는 AI 가 실제로 한 말을 검사할 수 없습니다.
//    그래서 지켜야 할 것을 이 지침에 분명하게 적는 것이 유일한 안전장치입니다. 미성년자와 하는 통화입니다. (CLAUDE.md 4장)
public final class CallScript {

  // 기분이 이 값 이하이면(1 많이 처짐, 2 처짐) 관리 강도와 상관없이 위로하는 말투로 바꿉니다.
  static final int LOW_MOOD = 2;
  // 평균 시작 지연이 이 값(분)을 넘으면 "시작 시각" 을 통화에서 다룹니다. (FE 의 주간 코치 규칙과 같은 기준)
  static final int LATE_START_MINUTES = 15;

  private CallScript() {}

  // 통화 한 번의 지침 전체를 만듭니다. card 가 null 이면 기록이 없는 사람으로 보고 만듭니다.
  public static String instructions(int careLevel, int maxHarshness, CallSessionRequest.Card card) {
    List<String> lines = new ArrayList<>();
    lines.add("너는 고등학생에게 하루에 한 번 짧게 전화하는 공부 코치야. 한국어 반말로 말해.");
    lines.add("한 번에 한두 문장만 말하고, 학생이 대답할 때까지 기다려. 통화 전체는 3분 안에 끝내.");
    lines.add("");
    lines.add("[꼭 지킬 것]");
    lines.add("- 아래 [오늘의 사실] 에 적힌 숫자만 말해. 숫자를 새로 만들거나 계산해서 바꾸지 마.");
    lines.add("- 외모·성적·능력을 깎아내리지 마. 다른 사람과 비교하지 마. 한 일(행동)만 이야기해.");
    lines.add(
        "- 학생이 죽고 싶다, 사라지고 싶다, 자기를 해치고 싶다는 말을 하면 공부 이야기를 바로 멈춰."
            + " 꾸짖거나 가볍게 넘기지 말고 먼저 들어 줘. 그리고 믿을 수 있는 어른에게 꼭 이야기하고,"
            + " 앱 설정의 '힘들 때 도움받기' 를 눌러 보라고 말해 줘.");
    lines.add("- 공부와 상관없는 부탁(숙제를 대신 풀기, 다른 주제의 긴 이야기)은 짧게 사양하고 통화 순서로 돌아와.");
    lines.add("- 네가 AI 라는 것을 숨기지 마. 사람인 척하지 마.");
    lines.add("");
    lines.add("[말투]");
    lines.add(toneRule(careLevel, maxHarshness, card));
    lines.add("");
    lines.add("[오늘의 사실]");
    lines.addAll(facts(card));
    lines.add("");
    lines.add("[통화 순서]");
    lines.add("1. 짧게 인사하고, " + openingStep(card));
    lines.add("2. " + yesterdayStep(card));
    lines.add("3. " + todayStep(card));
    lines.add("4. 오늘의 약속을 같이 정해. '만약 ~하면 → ~한다' 한 문장으로. 학생이 정한 문장을 그대로 다시 말해 주고 통화를 끝내.");
    return String.join("\n", lines);
  }

  // ---- 말투 ----

  // 말투 규칙 한 줄. 기분이 낮으면 무엇보다 먼저 위로하는 말투로 바꿉니다. (안전이 다른 설정보다 먼저입니다)
  //   기분이 처짐 이하 · 관리 강도 0(회복)   -> 위로만
  //   관리 강도 1(당근)                     -> 구체적으로 칭찬
  //   관리 강도 2(넛지), 또는 독설 수위 2 이하 -> 담백하게 사실만
  //   관리 강도 3·4(채찍) + 독설 수위 3 이상   -> 솔직하게 짚되 사람은 깎아내리지 않기
  static String toneRule(int careLevel, int maxHarshness, CallSessionRequest.Card card) {
    if (isMoodLow(card) || careLevel <= 0) {
      return "위로하고 응원하는 말만 해. 아쉬운 점을 지적하지 마. 오늘은 10분만 해도 충분하다고 말해 줘.";
    }
    if (careLevel == 1) {
      return "잘한 점을 숫자와 함께 구체적으로 칭찬해. 아쉬운 점은 한 가지만, 부드럽게 말해.";
    }
    if (careLevel == 2 || maxHarshness <= 2) {
      return "담백하게 사실만 말해. 칭찬도 지적도 길게 하지 마. 꾸짖는 말은 쓰지 마.";
    }
    return "아쉬운 점을 숫자와 함께 돌려 말하지 말고 짚어. 그래도 사람을 깎아내리지 말고 한 일만 말해."
        + " 비꼬거나 겁주지 마. 세기는 5단계 중 "
        + maxHarshness
        + "단계까지만.";
  }

  // 가장 최근 기분이 낮은지(처짐 이하) 알려줍니다. 기분 기록이 없으면 false 입니다.
  static boolean isMoodLow(CallSessionRequest.Card card) {
    if (card == null || card.recentMoods() == null || card.recentMoods().isEmpty()) {
      return false;
    }
    int latest = card.recentMoods().get(card.recentMoods().size() - 1);
    return latest <= LOW_MOOD;
  }

  // ---- 오늘의 사실 ----

  // AI 에게 건넬 사실들을 한 줄씩 만듭니다. 값이 없는(null) 항목은 줄을 만들지 않습니다.
  static List<String> facts(CallSessionRequest.Card card) {
    List<String> lines = new ArrayList<>();
    if (card == null) {
      lines.add("- 아직 기록이 없어. 처음 통화하는 것처럼 대해.");
      return lines;
    }

    Integer rate = yesterdayRate(card);
    if (card.yesterdayStudyMinutes() != null) {
      String line = "- 어제 순공시간: " + formatMinutes(card.yesterdayStudyMinutes());
      if (rate != null) {
        line += " (계획 " + formatMinutes(card.yesterdayPlanMinutes()) + " 의 " + rate + "%)";
      } else {
        line += " (어제는 계획이 없었어)";
      }
      lines.add(line);
    }
    if (card.yesterdayMissReason() != null) {
      lines.add("- 어제 계획을 못 채운 이유로 학생이 고른 것: " + missReasonLabel(card.yesterdayMissReason()));
    }
    if (card.weekPlanRate() != null) {
      lines.add("- 이번 주 계획 대비 실제: " + card.weekPlanRate() + "%");
    }
    if (card.weekCompletionRate() != null) {
      lines.add("- 이번 주 완주율(목표를 정한 타이머를 끝까지 한 비율): " + card.weekCompletionRate() + "%");
    }
    if (card.weekStartDelayMinutes() != null) {
      lines.add("- 이번 주 평균 시작 지연: 계획한 시각보다 " + card.weekStartDelayMinutes() + "분 늦게 시작");
    }
    if (card.todayPlanCount() != null) {
      if (card.todayPlanCount() == 0) {
        lines.add("- 오늘 계획: 아직 없음");
      } else {
        String line = "- 오늘 계획: " + card.todayPlanCount() + "개";
        if (card.todayPlanMinutes() != null) {
          line += ", 합쳐서 " + formatMinutes(card.todayPlanMinutes());
        }
        if (card.todayFirstStart() != null) {
          line += ", 첫 계획은 " + card.todayFirstStart();
        }
        lines.add(line);
      }
    }
    if (card.recentMoods() != null && !card.recentMoods().isEmpty()) {
      List<String> moods = new ArrayList<>();
      for (int mood : card.recentMoods()) {
        moods.add(moodLabel(mood));
      }
      lines.add("- 최근 기분(오래된 날부터): " + String.join(" → ", moods));
    }
    if (card.streakDays() != null && card.streakDays() > 0) {
      lines.add("- 연속으로 공부한 날: " + card.streakDays() + "일");
    }

    if (lines.isEmpty()) {
      lines.add("- 아직 기록이 없어. 처음 통화하는 것처럼 대해.");
    }
    return lines;
  }

  // 어제의 달성률(%). 팀의 달성률 식(AchievementRate)으로 서버가 직접 계산합니다. 계산할 수 없으면 null 입니다.
  static Integer yesterdayRate(CallSessionRequest.Card card) {
    if (card == null
        || card.yesterdayStudyMinutes() == null
        || card.yesterdayPlanMinutes() == null) {
      return null;
    }
    return AchievementRate.percentOf(card.yesterdayStudyMinutes(), card.yesterdayPlanMinutes());
  }

  // ---- 통화 순서 ----

  // 1번: 인사 다음에 할 말. 어제 숫자가 있으면 그 숫자로 시작합니다.
  static String openingStep(CallSessionRequest.Card card) {
    if (card == null || card.yesterdayStudyMinutes() == null) {
      return "오늘 컨디션이 어떤지 물어봐.";
    }
    Integer rate = yesterdayRate(card);
    if (rate == null) {
      return "어제 한 것을 한 문장으로 말해 줘. 예: \"어제 순공 "
          + formatMinutes(card.yesterdayStudyMinutes())
          + " 했네.\"";
    }
    return "어제 한 것을 한 문장으로 말해 줘. 예: \"어제 순공 "
        + formatMinutes(card.yesterdayStudyMinutes())
        + ", 계획의 "
        + rate
        + "%야.\"";
  }

  // 2번: 어제에 대해 물을 것. 계획을 채웠는지, 못 채운 이유가 무엇인지에 따라 질문이 달라집니다.
  static String yesterdayStep(CallSessionRequest.Card card) {
    Integer rate = yesterdayRate(card);
    if (rate == null) {
      return "어제는 계획이 없었거나 기록이 없어. 그 점을 꾸짖지 말고, 요즘 공부가 어떤지 한 가지만 물어봐.";
    }
    if (rate >= 100) {
      return "어제 계획을 다 채웠어. 무엇이 도움이 됐는지 물어보고, 그 방법을 오늘도 써 보자고 해.";
    }
    if (card.yesterdayMissReason() == null) {
      return "어제 계획을 다 채우지 못했어. 무엇이 가장 걸렸는지 한 가지만 물어봐.";
    }
    return "어제 계획을 다 채우지 못했고, 이유는 '"
        + missReasonLabel(card.yesterdayMissReason())
        + "' 라고 했어. "
        + missReasonQuestion(card.yesterdayMissReason());
  }

  // 3번: 오늘에 대해 물을 것. 오늘 계획이 있는지, 요즘 시작이 늦는지에 따라 달라집니다.
  static String todayStep(CallSessionRequest.Card card) {
    if (card == null || card.todayPlanCount() == null || card.todayPlanCount() == 0) {
      return "오늘 계획이 아직 없어. 가장 먼저 할 것 하나와 몇 분 할지를 같이 정해.";
    }
    String step = "오늘은 얼마나 할 것 같은지 물어봐.";
    boolean startsLate =
        card.weekStartDelayMinutes() != null && card.weekStartDelayMinutes() > LATE_START_MINUTES;
    if (startsLate) {
      step += " 요즘 시작이 늦는 편이니, 첫 계획을 제시간에 시작하려면 무엇이 필요한지도 물어봐.";
    }
    return step;
  }

  // 못 채운 이유마다 다른 질문을 합니다. (FE 의 MissReason: 피곤·약속·폰·계획 과다·하기 싫음·아픔·기타)
  static String missReasonQuestion(String missReason) {
    return switch (missReason) {
      case "phone" -> "공부할 때 폰을 어디에 두면 덜 보게 될지 물어봐.";
      case "tired" -> "어제 몇 시에 잤는지 묻고, 오늘은 목표를 조금 줄여도 된다고 말해 줘.";
      case "event" -> "약속이 있는 날에는 계획을 어떻게 잡으면 좋을지 물어봐.";
      case "tooMuch" -> "계획이 많았다고 했으니, 오늘 계획에서 무엇을 줄일지 물어봐.";
      case "unwilling" -> "하기 싫었던 순간이 언제였는지 묻고, 5분만 해 보는 것을 권해.";
      case "sick" -> "몸은 괜찮은지 먼저 묻고, 오늘은 쉬어도 된다고 말해 줘.";
      default -> "무엇 때문이었는지 한 가지만 물어봐.";
    };
  }

  // ---- 글자로 바꾸기 ----

  // FE 의 못 채운 이유 분류값을 사람이 읽는 말로 바꿉니다. (FE src/lib/missReasons.ts 와 같은 말)
  static String missReasonLabel(String missReason) {
    return switch (missReason) {
      case "tired" -> "피곤";
      case "event" -> "약속·행사";
      case "phone" -> "폰·SNS";
      case "tooMuch" -> "계획 과다";
      case "unwilling" -> "하기 싫음";
      case "sick" -> "아픔";
      default -> "기타";
    };
  }

  // 기분 점수(1 ~ 5)를 말로 바꿉니다. (FE 의 체크인 화면과 같은 말)
  static String moodLabel(int mood) {
    return switch (mood) {
      case 1 -> "많이 처짐";
      case 2 -> "처짐";
      case 3 -> "보통";
      case 4 -> "좋음";
      default -> "아주 좋음";
    };
  }

  // 분을 "3시간 10분" 모양으로 바꿉니다.   190 -> "3시간 10분" / 120 -> "2시간" / 45 -> "45분" / 0 -> "0분"
  static String formatMinutes(int minutes) {
    int hours = minutes / 60;
    int rest = minutes % 60;
    if (hours == 0) {
      return rest + "분";
    }
    if (rest == 0) {
      return hours + "시간";
    }
    return hours + "시간 " + rest + "분";
  }
}
