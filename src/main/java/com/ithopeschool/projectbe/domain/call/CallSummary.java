package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.summary.DaySummary;
import com.ithopeschool.projectbe.domain.summary.DaySummary.DayPlan;
import com.ithopeschool.projectbe.domain.summary.DaySummary.DaySession;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// "지난 통화 요약" 카드 한 장입니다. (이슈 #29 의 요약 카드)
// 통화에서 나온 값은 그날 체크인에 들어 있습니다. 거기에 어제 공부 기록으로 만든 "어제 요약" 한 줄을 붙이면 카드가 됩니다.
// 녹음과 대화 원문은 남기지 않고, 이 요약만 남깁니다. (CLAUDE.md 4장)
//
// 정해 둔 규칙
//   1. 카드는 체크인한 날마다 한 장이고, 최근 날짜가 맨 위에 옵니다.
//   2. 어제 요약 한 줄(headline)은 체크인한 날의 "전날" 기록으로 만듭니다.
//        계획과 공부 기록이 모두 없음   →  "어제는 계획도 공부 기록도 없어요"
//        계획은 없고 공부만 함          →  "어제 순공 40분 (세워 둔 계획은 없었어요)"
//        계획이 있음                    →  "어제 순공 3시간 10분, 계획의 80%"
//      계획은 있는데 목표가 0분이라 달성률을 모르는 날은 "계획 없이 공부한 날" 처럼 적습니다.
//      순공이 0분이면 "1분 미만" 이라고 적습니다.
//   3. 오늘의 약속은 "만약 ~ → ~" 한 문장입니다. 둘 중 하나라도 비어 있으면 약속이 아니라서 null 입니다.
//   4. 못 채운 이유는 버튼의 글자로 바꿉니다. "기타" 에 적은 한 줄이 있으면 괄호로 붙입니다.  "기타 (학원 보강)"
//   5. AI 한마디와 그날의 관리 강도는 그날 평가가 있을 때만 채웁니다. 없으면 null 입니다.
//
// ⚠️ 약속 문장·"기타" 에 적은 글·AI 한마디는 본인에게만 돌려주고 로그에 찍지 않습니다.
//
// FE 의 buildCallSummaries (src/lib/callSummary.ts) 와 같은 규칙·같은 문장입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 카드가 어긋나지 않습니다.
//
// date: 통화(체크인)한 날짜
// headline: 어제 요약 한 줄 / promise: 오늘의 약속 한 문장. 정하지 않았으면 null
// missReason: 어제 계획을 못 채운 이유 글자. 고르지 않았으면 null
// expectedMinutes: 오늘 예상 순공시간(분)
// aiComment: AI 가 남긴 한마디. 없으면 null / careLevel: 그날의 관리 강도. 없으면 null
public record CallSummary(
    LocalDate date,
    String headline,
    String promise,
    String missReason,
    int expectedMinutes,
    String aiComment,
    Integer careLevel) {

  private static final int MINUTES_PER_HOUR = 60;

  // 체크인 하나에서 카드에 쓰는 값만 모은 것입니다. 날짜마다 하나씩 넘깁니다.
  // date: 체크인한 날 / expectedMinutes: 오늘 예상 순공시간(분)
  // missReason: 어제 못 채운 이유. 안 골랐으면 null / missReasonNote: "기타" 를 골랐을 때 적은 한 줄. 없으면 null
  // promiseIf, promiseThen: 오늘의 약속 "만약 ~하면" 과 "~한다". 안 적었으면 null
  // (체크인 테이블은 아직 없어서, 엔티티가 아니라 이 계산에 필요한 값만 받습니다)
  public record CheckInItem(
      LocalDate date,
      int expectedMinutes,
      MissReason missReason,
      String missReasonNote,
      String promiseIf,
      String promiseThen) {}

  // 하루가 끝난 뒤 AI 가 내린 평가에서 카드에 쓰는 값만 모은 것입니다.
  // date: 평가한 날 / summary: AI 한마디 / careLevel: 그날의 관리 강도(0 ~ 4)
  public record EvaluationItem(LocalDate date, String summary, int careLevel) {}

  // 체크인들로 통화 요약 목록을 만듭니다. 최근 것이 맨 위에 옵니다. (규칙 1)
  //
  // checkIns: 체크인들. 순서가 뒤섞여 있어도 됩니다.
  // plans, sessions: 계획과 공부 기록. 체크인한 날들의 "전날" 것이 들어 있어야 합니다.
  //   노는 계획과 노는 기록은 부르는 쪽에서 빼고 넘깁니다.
  // evaluations: 날짜별 평가. 아직 없으면 빈 목록을 넘깁니다.
  public static List<CallSummary> listOf(
      List<CheckInItem> checkIns,
      List<DayPlan> plans,
      List<DaySession> sessions,
      List<EvaluationItem> evaluations) {
    List<CheckInItem> sorted = new ArrayList<>(checkIns);
    sorted.sort(Comparator.comparing(CheckInItem::date).reversed());

    List<CallSummary> summaries = new ArrayList<>();
    for (CheckInItem checkIn : sorted) {
      EvaluationItem evaluation = evaluationOf(checkIn.date(), evaluations);
      // 한마디가 빈칸뿐이면 없는 것으로 봅니다.
      String aiComment =
          evaluation != null && !evaluation.summary().isBlank() ? evaluation.summary() : null;
      summaries.add(
          new CallSummary(
              checkIn.date(),
              headlineOf(checkIn.date().minusDays(1), plans, sessions),
              promiseOf(checkIn.promiseIf(), checkIn.promiseThen()),
              missReasonTextOf(checkIn.missReason(), checkIn.missReasonNote()),
              checkIn.expectedMinutes(),
              aiComment,
              evaluation == null ? null : evaluation.careLevel()));
    }
    return summaries;
  }

  // 어제 요약 한 줄을 만듭니다. yesterday 는 체크인한 날의 전날입니다. (규칙 2)
  public static String headlineOf(
      LocalDate yesterday, List<DayPlan> plans, List<DaySession> sessions) {
    boolean hasPlan = false;
    for (DayPlan plan : plans) {
      if (plan.date().equals(yesterday)) {
        hasPlan = true;
        break;
      }
    }
    DaySummary summary = DaySummary.of(yesterday, plans, sessions);

    if (!hasPlan && summary.studyMinutes() == 0) {
      return "어제는 계획도 공부 기록도 없어요";
    }
    // 계획이 없거나, 있어도 목표 시간이 0 이라 달성률을 계산할 수 없는 날(null)입니다.
    if (!hasPlan || summary.achievementRate() == null) {
      return "어제 순공 " + minutesText(summary.studyMinutes()) + " (세워 둔 계획은 없었어요)";
    }
    return "어제 순공 "
        + minutesText(summary.studyMinutes())
        + ", 계획의 "
        + summary.achievementRate()
        + "%";
  }

  // 오늘의 약속을 한 문장으로 만듭니다. 둘 중 하나라도 비어 있으면 null 입니다. (규칙 3)
  //
  //   ("학원에서 돌아오면", "영어 단어 20개")  →  "만약 학원에서 돌아오면 → 영어 단어 20개"
  public static String promiseOf(String promiseIf, String promiseThen) {
    String ifText = promiseIf == null ? "" : promiseIf.strip();
    String thenText = promiseThen == null ? "" : promiseThen.strip();
    if (ifText.isEmpty() || thenText.isEmpty()) {
      return null;
    }
    return "만약 " + ifText + " → " + thenText;
  }

  // 못 채운 이유를 글자로 바꿉니다. 안 골랐으면 null 입니다. (규칙 4)
  //
  //   (PHONE, null)       →  "폰·SNS"
  //   (ETC, "학원 보강")  →  "기타 (학원 보강)"
  //   (TIRED, "어제 늦게 잠")  →  "피곤·수면"   (적은 글은 "기타" 일 때만 붙입니다)
  public static String missReasonTextOf(MissReason missReason, String note) {
    if (missReason == null) {
      return null;
    }
    String label = missReasonLabel(missReason);
    String trimmedNote = note == null ? "" : note.strip();
    return missReason == MissReason.ETC && !trimmedNote.isEmpty()
        ? label + " (" + trimmedNote + ")"
        : label;
  }

  // 못 채운 이유의 글자입니다. 체크인의 이유 버튼에 적힌 글자와 같습니다.
  public static String missReasonLabel(MissReason missReason) {
    return switch (missReason) {
      case TIRED -> "피곤·수면";
      case EVENT -> "약속·행사";
      case PHONE -> "폰·SNS";
      case TOO_MUCH -> "계획 과다";
      case UNWILLING -> "하기 싫음";
      case SICK -> "아픔";
      case ETC -> "기타";
    };
  }

  // 그 날짜의 평가를 찾습니다. 없으면 null 입니다.
  private static EvaluationItem evaluationOf(LocalDate date, List<EvaluationItem> evaluations) {
    for (EvaluationItem evaluation : evaluations) {
      if (evaluation.date().equals(date)) {
        return evaluation;
      }
    }
    return null;
  }

  // 분을 "2시간 14분" · "40분" · "2시간" 처럼 바꿉니다. 0분이면 "1분 미만" 입니다.
  private static String minutesText(int totalMinutes) {
    if (totalMinutes <= 0) {
      return "1분 미만";
    }
    int hours = totalMinutes / MINUTES_PER_HOUR;
    int minutes = totalMinutes % MINUTES_PER_HOUR;
    if (hours == 0) {
      return minutes + "분";
    }
    if (minutes == 0) {
      return hours + "시간";
    }
    return hours + "시간 " + minutes + "분";
  }
}
