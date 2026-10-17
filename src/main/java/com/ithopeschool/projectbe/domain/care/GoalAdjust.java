package com.ithopeschool.projectbe.domain.care;

import com.ithopeschool.projectbe.domain.risk.RiskLevel;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

// 오늘의 대응(CareDecision.decide, 이슈 #55)을 보고 "하루 목표를 바꿔 볼까요?" 하고 제안할 내용을 만듭니다.
// (이슈 #63, 기획서 v2 4장 「분기: 당근 / 채찍 / 목표 조정」)
// 계속 성공하면 목표를 올리고, 무너지면 낮춰야 꾸준히 이어 갈 수 있습니다.
//
//   당근 + 목표 상향(CARROT_RAISE)  →  지금 목표 × 1.1 (반올림)          예) 240분 → 264분
//   목표 하향(GOAL_DOWN)            →  최근 실제 평균 × 1.1 (반올림)     예) 평균 100분, 지금 목표 240분 → 110분
//   그 밖의 대응(채찍·당근만 등)     →  제안 없음 (null)
//
// "목표" 는 오늘 공부 계획들의 목표 시간(분) 합계입니다. (StudyPlan.targetMinutes)
// 여기서는 제안만 만듭니다. 사용자가 수락해야 목표가 바뀝니다. 수락하면 scalePlanMinutes 로 계획마다 나눠 줍니다.
//
// FE 의 proposeGoalAdjust · isDeclineSnoozed · scalePlanMinutes (src/lib/goalAdjust.ts),
// getRecentAverageMinutes (src/lib/goalAdjustInput.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 제안이 어긋나지 않습니다.
//
// kind: 올리기(RAISE)인지 낮추기(LOWER)인지 / fromMinutes: 지금 목표(분) / toMinutes: 제안하는 새 목표(분)
public record GoalAdjust(Kind kind, int fromMinutes, int toMinutes) {

  // 제안의 방향입니다.
  public enum Kind {
    // 목표 올리기
    RAISE,
    // 목표 낮추기
    LOWER
  }

  // 목표를 올릴 때 곱하는 값입니다. 1.1 = 10% 상향 (예: 240분 → 264분)
  public static final double RAISE_RATE = 1.1;

  // 목표를 낮출 때 "최근 실제 평균" 에 곱하는 값입니다. 1.1 = 평균의 110%
  public static final double RECOVERY_RATE = 1.1;

  // 제안을 거절하면 이 일수 동안은 다시 묻지 않습니다.
  public static final int DECLINE_SNOOZE_DAYS = 3;

  // 최근 평균을 낼 때 거슬러 올라가 볼 일수입니다. (오늘은 빼고 어제부터 셉니다)
  public static final int RECENT_AVERAGE_DAYS = 7;

  // 대응이 목표 조정이면 제안을 만들고, 아니면 null 을 돌려줍니다.
  //
  // action: 오늘의 대응 (CareDecision.decide 결과의 action)
  // currentMinutes: 지금 목표(분). 오늘 계획들의 목표 시간 합계
  // recentAverageMinutes: 최근 실제 평균(분). recentAverageMinutes 함수의 결과. 모르면 null
  //
  // 제안하지 않는 경우 (null)
  //   - 다른 대응(채찍·당근만 등)일 때
  //   - 지금 목표가 0분 이하일 때 (올리거나 낮출 기준이 없음)
  //   - 목표 하향인데 최근 평균을 모르거나 0 이하일 때 (근거 없이 낮추지 않음)
  //   - 목표 하향인데 계산한 값이 지금 목표보다 낮지 않을 때 ("낮추자" 며 올리는 일 방지)
  //   - 목표 상향인데 반올림하면 값이 그대로일 때 (4분 이하의 너무 작은 목표)
  public static GoalAdjust propose(
      CareAction action, int currentMinutes, Integer recentAverageMinutes) {
    if (currentMinutes <= 0) {
      return null;
    }

    if (action == CareAction.CARROT_RAISE) {
      int toMinutes = (int) Math.round(currentMinutes * RAISE_RATE);
      if (toMinutes <= currentMinutes) {
        return null;
      }
      return new GoalAdjust(Kind.RAISE, currentMinutes, toMinutes);
    }

    if (action == CareAction.GOAL_DOWN) {
      if (recentAverageMinutes == null || recentAverageMinutes <= 0) {
        return null;
      }
      int toMinutes = (int) Math.round(recentAverageMinutes * RECOVERY_RATE);
      if (toMinutes >= currentMinutes) {
        return null;
      }
      return new GoalAdjust(Kind.LOWER, currentMinutes, toMinutes);
    }

    return null;
  }

  // 오늘의 위험 예측(RiskRule, 이슈 #49)까지 보고 제안을 만듭니다.
  //
  // 무너지기 쉬운 날(주의 · 높음)에는 목표를 올리자고 제안하지 않습니다. (null)
  //   목표를 낮추는 제안은 그대로입니다. 이미 부담을 줄이는 쪽이라 위험한 날에 막을 이유가 없습니다.
  // 위험이 없는 날(NONE)은 위의 propose 와 똑같습니다.
  //
  // riskLevel: 오늘의 위험 수준. 모르면 RiskLevel.NONE
  public static GoalAdjust propose(
      CareAction action, int currentMinutes, Integer recentAverageMinutes, RiskLevel riskLevel) {
    if (riskLevel.isRisky() && action == CareAction.CARROT_RAISE) {
      return null;
    }
    return propose(action, currentMinutes, recentAverageMinutes);
  }

  // 어제부터 최근 7일 중, 실제로 공부한 날들의 하루 평균 순공시간(분)입니다. (반올림)
  // 공부한 날이 하루도 없으면 null 입니다. (평균을 낼 근거가 없으니 낮추자는 제안도 하지 않습니다)
  //
  //   어제 60분, 그저께 0분(안 함), 3일 전 100분  →  (60 + 100) ÷ 2 = 80
  //
  // 공부 안 한 날을 평균에 넣으면 "쉬는 날이 많아서" 목표가 너무 낮아지므로 뺍니다.
  //
  // studyMinutesFromYesterday: 날짜별 순공시간(분)을 "어제, 그저께, 3일 전 …" 순서로 담은 목록.
  //   7일보다 길게 넘겨도 앞의 7일만 봅니다.
  public static Integer recentAverageMinutes(List<Integer> studyMinutesFromYesterday) {
    int totalMinutes = 0;
    int studiedDays = 0;

    int days = Math.min(RECENT_AVERAGE_DAYS, studyMinutesFromYesterday.size());
    for (int i = 0; i < days; i++) {
      int studyMinutes = studyMinutesFromYesterday.get(i);
      if (studyMinutes > 0) {
        totalMinutes += studyMinutes;
        studiedDays++;
      }
    }

    if (studiedDays == 0) {
      return null;
    }
    return (int) Math.round((double) totalMinutes / studiedDays);
  }

  // 지금(now)이 거절한 뒤 "다시 묻지 않는 기간" 인지 알려줍니다.
  // "오늘" 은 하루의 기준(새벽 4시, DayRule)으로 정합니다. 새벽 2시는 아직 어제입니다.
  public static boolean isDeclineSnoozed(LocalDate declinedDate, Instant now) {
    return isDeclineSnoozed(declinedDate, DayRule.dateOf(now));
  }

  // 거절한 날부터 3일 동안은 true 입니다.
  //
  //   10일에 거절  →  10·11·12일은 true, 13일부터 false
  //   거절한 적 없음(null)  →  false
  //   거절한 날이 오늘보다 미래  →  true  (날짜가 꼬인 것이니 안전하게 "아직 쉬는 중" 으로 봅니다)
  public static boolean isDeclineSnoozed(LocalDate declinedDate, LocalDate today) {
    if (declinedDate == null) {
      return false;
    }
    // 거절한 날부터 오늘까지 며칠이 지났는지 셉니다. (같은 날이면 0, 다음 날이면 1)
    long passedDays = ChronoUnit.DAYS.between(declinedDate, today);
    return passedDays < DECLINE_SNOOZE_DAYS;
  }

  // 제안을 수락했을 때, 계획들의 목표 시간을 새 합계에 맞춰 같은 비율로 바꿉니다.
  //
  //   30분·60분(합계 90분)을 135분으로  →  45분·90분
  //
  //   - 계획마다 반올림하므로 합계가 1~2분 어긋날 수 있습니다.
  //   - 계획이 하나라도 0분이 되면 안 되므로 최소 1분으로 둡니다.
  //   - 원래 합계나 새 합계가 0 이하면 비율을 구할 수 없어서 그대로 돌려줍니다.
  //
  // planMinutes: 오늘 공부 계획들의 목표 시간(분). 돌려주는 목록도 같은 순서입니다.
  //   노는 계획이 생기면 공부 목표가 아니라서 여기에 넘기지 않습니다. (FE 는 kind 가 'play' 인 계획을 건너뜁니다)
  public static List<Integer> scalePlanMinutes(List<Integer> planMinutes, int newTotalMinutes) {
    int oldTotalMinutes = 0;
    for (int minutes : planMinutes) {
      oldTotalMinutes += minutes;
    }
    if (oldTotalMinutes <= 0 || newTotalMinutes <= 0) {
      return planMinutes;
    }

    // 정수끼리 나누면 소수점이 버려져서, 한쪽을 소수(double)로 바꿔서 나눕니다.
    double ratio = (double) newTotalMinutes / oldTotalMinutes;

    List<Integer> scaled = new ArrayList<>();
    for (int minutes : planMinutes) {
      scaled.add((int) Math.max(1, Math.round(minutes * ratio)));
    }
    return scaled;
  }
}
