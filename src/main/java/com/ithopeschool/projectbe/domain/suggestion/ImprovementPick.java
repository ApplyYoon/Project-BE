package com.ithopeschool.projectbe.domain.suggestion;

import com.ithopeschool.projectbe.domain.call.CallSummary;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.onboarding.FailureCause;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// 개선안 3개 제안(이슈 #51)에서 "무엇을 고를지" 를 정합니다. 고르는 것은 코드, 다듬는 것만 GPT 입니다.
// 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
//
// ■ 원인 고르기 (causeOf)
//   1. 최근 30일 실패 원인(#39)에서 '기타' · '아픔' 을 뺀 날이 합쳐 MIN_RECENT_DAYS(3일) 이상이면,
//      그중 날 수가 가장 많은 원인입니다. (근거 RECENT)
//      아픔은 전략이 없고(쉬고 목표를 낮추는 게 먼저), 기타는 무슨 원인인지 몰라서 뺍니다.
//   2. 아니면 온보딩의 "계획이 주로 언제 무너졌나"(#21)로 대신합니다. (근거 ONBOARDING, 이슈 "데이터가 적으면")
//        폰 → 폰·SNS / 계획을 너무 많이 → 계획 과다 / 학원이 늦게 끝난 날 → 피곤·수면 / 주말 · 시험 끝나고 → 기타(생활 리듬)
//      여러 개 골랐으면 ONBOARDING_ORDER 순서로 앞의 것입니다. (원인이 분명한 것부터)
//   3. 둘 다 없으면 null 입니다. FE 는 "기록이 쌓이면 개선안을 보여 줄게" 를 보여 줍니다.
//
// ■ 전략 고르기 (strategiesOf): 그 원인의 쉬움 · 보통 · 강함에서 하나씩. (카탈로그에 강도마다 2개씩 있습니다)
//   몇 번째를 고를지는 주마다 바꿉니다: (그해의 몇째 날 − 1) ÷ 7 을 개수로 나눈 나머지. 같은 주에는 늘 같은 3개입니다.
//   ⚠️ "최근 효과가 없던 전략(#54)은 빼고 고른다" 는 아직 못 합니다. 고른 개선안과 효과 판정을 저장하는 곳(#52)이 없어서입니다.
public final class ImprovementPick {

  // 최근 기록으로 원인을 고르려면 이만큼의 날이 있어야 합니다. (예상 실패 원인 선지 #237 과 같은 기준)
  static final int MIN_RECENT_DAYS = 3;
  // 요일을 "주로 ~요일" 로 말하려면 그 요일에 이만큼은 있어야 합니다.
  static final int MIN_WEEKDAY_DAYS = 2;

  // 온보딩 답에서 원인을 고르는 순서 (원인이 분명한 것부터)
  static final List<FailureCause> ONBOARDING_ORDER =
      List.of(
          FailureCause.PHONE,
          FailureCause.TOO_MUCH_PLAN,
          FailureCause.LATE_ACADEMY,
          FailureCause.WEEKEND,
          FailureCause.AFTER_EXAM);

  private static final List<String> WEEKDAYS = List.of("월", "화", "수", "목", "금", "토", "일");

  // 무엇을 근거로 원인을 골랐는지
  public enum Basis {
    // 최근 30일 실패 원인
    RECENT,
    // 온보딩의 과거 실패 경험
    ONBOARDING
  }

  // 최근 30일 원인 하나. weekdayCounts: 요일별 날 수 (0번째 = 월요일 … 6번째 = 일요일)
  public record RecentReason(MissReason reason, int dayCount, List<Integer> weekdayCounts) {}

  // 고른 원인과 근거, GPT 에게 줄 "학생의 상황" 한 줄
  public record Pick(MissReason cause, Basis basis, String situation) {}

  private ImprovementPick() {}

  // 위의 원인 고르기 규칙입니다. recent 는 날 수가 많은 것부터 옵니다. 고를 것이 없으면 null 입니다.
  public static Pick causeOf(List<RecentReason> recent, Set<FailureCause> onboardingCauses) {
    // 규칙 1
    int totalDays = 0;
    RecentReason top = null;
    for (RecentReason item : recent) {
      if (item.reason() == MissReason.ETC || item.reason() == MissReason.SICK) {
        continue;
      }
      totalDays += item.dayCount();
      if (top == null || item.dayCount() > top.dayCount()) {
        top = item;
      }
    }
    if (top != null && totalDays >= MIN_RECENT_DAYS) {
      return new Pick(top.reason(), Basis.RECENT, recentSituation(top));
    }

    // 규칙 2
    for (FailureCause cause : ONBOARDING_ORDER) {
      if (onboardingCauses.contains(cause)) {
        return new Pick(
            missReasonOf(cause),
            Basis.ONBOARDING,
            "처음 설문에서 '" + onboardingLabel(cause) + "' 계획이 무너진다고 했음");
      }
    }

    // 규칙 3
    return null;
  }

  // 위의 전략 고르기 규칙입니다. 쉬움 · 보통 · 강함 순서로 3개입니다.
  public static List<Strategy> strategiesOf(MissReason cause, LocalDate today) {
    int week = (today.getDayOfYear() - 1) / 7;
    List<Strategy> picked = new ArrayList<>();
    for (SuggestionLevel level : SuggestionLevel.values()) {
      List<Strategy> candidates = StrategyCatalog.of(cause, level);
      if (!candidates.isEmpty()) {
        picked.add(candidates.get(week % candidates.size()));
      }
    }
    return picked;
  }

  // 온보딩 답을 실패 원인으로 바꿉니다. (위 규칙 2 의 짝)
  static MissReason missReasonOf(FailureCause cause) {
    return switch (cause) {
      case PHONE -> MissReason.PHONE;
      case TOO_MUCH_PLAN -> MissReason.TOO_MUCH;
      case LATE_ACADEMY -> MissReason.TIRED;
      case WEEKEND, AFTER_EXAM -> MissReason.ETC;
    };
  }

  // "최근 30일 중 '폰·SNS' 때문에 못 채운 날이 5일, 주로 화요일"
  // 요일은 한 요일이 2일 이상이고 다른 요일보다 많을 때만 붙입니다.
  static String recentSituation(RecentReason reason) {
    String text =
        "최근 30일 중 '"
            + CallSummary.missReasonLabel(reason.reason())
            + "' 때문에 못 채운 날이 "
            + reason.dayCount()
            + "일";
    String weekday = mainWeekdayOf(reason.weekdayCounts());
    return weekday == null ? text : text + ", 주로 " + weekday + "요일";
  }

  // 가장 많은 요일 하나. 2일보다 적거나 같은 수의 요일이 둘 이상이면 null 입니다.
  private static String mainWeekdayOf(List<Integer> weekdayCounts) {
    if (weekdayCounts == null || weekdayCounts.size() != WEEKDAYS.size()) {
      return null;
    }
    int best = -1;
    boolean tie = false;
    for (int i = 0; i < weekdayCounts.size(); i++) {
      if (best < 0 || weekdayCounts.get(i) > weekdayCounts.get(best)) {
        best = i;
        tie = false;
      } else if (weekdayCounts.get(i).equals(weekdayCounts.get(best))) {
        tie = true;
      }
    }
    if (tie || weekdayCounts.get(best) < MIN_WEEKDAY_DAYS) {
      return null;
    }
    return WEEKDAYS.get(best);
  }

  // 온보딩 답의 글자 (FE 설문 보기와 같은 뜻)
  private static String onboardingLabel(FailureCause cause) {
    return switch (cause) {
      case PHONE -> "폰을 보다가";
      case TOO_MUCH_PLAN -> "계획을 너무 많이 세워서";
      case LATE_ACADEMY -> "학원이 늦게 끝난 날";
      case WEEKEND -> "주말에";
      case AFTER_EXAM -> "시험이 끝나고";
    };
  }
}
