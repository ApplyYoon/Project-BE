package com.ithopeschool.projectbe.domain.restday;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 휴식 처리 한도 · 기한 계산기입니다. (최종 기획서 「4.5 상담」 휴식 처리, 이슈 #225) 값만 계산하는 순수 함수입니다.
//
//   한도: 한 달(달력의 1일 ~ 말일)에 4번까지
//   기한: 오늘과 그 전 6일 (오늘 포함 7일). 미리 "내일 쉴게" 는 없습니다. 오늘은 새벽 4시 기준(DayRule)입니다.
//
//   isAllowedDate(10-12, 오늘 10-12) → true     isAllowedDate(10-13, 오늘 10-12) → false
//   isAllowedDate(10-06, 오늘 10-12) → true     isAllowedDate(10-05, 오늘 10-12) → false  (8일째)
//   daysLeft(이번 달 3번) → 1                    daysLeft(이번 달 4번) → 0
//
// 3일 연속 쉬었거나 그 달 한도를 다 쓰면 회복 모드 · 기분 저하 점검으로 넘깁니다. (needsRecoveryCheck, 이슈 #232)
public final class RestDayRule {

  // 한 달에 휴식으로 처리할 수 있는 날 수. 숫자는 출발값입니다. 바꿀 때는 FE(#407)의 상수와 같이 고칩니다.
  public static final int MONTHLY_LIMIT = 4;

  // 며칠 전까지 거슬러 휴식으로 처리할 수 있는지 (오늘 포함). FE(#407)의 REST_BACK_DAYS 와 같은 숫자입니다.
  public static final int BACK_DAYS = 7;

  // 며칠 연속 쉬면 회복 모드 · 기분 저하 점검으로 넘길지. FE 의 REST_STREAK_FOR_RECOVERY 와 같습니다. (이슈 #232)
  public static final int STREAK_FOR_RECOVERY = 3;

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private RestDayRule() {}

  // 휴식으로 처리할 수 있는 날짜인지 (오늘부터 6일 전까지면 true)
  public static boolean isAllowedDate(LocalDate date, LocalDate today) {
    return !date.isAfter(today) && !date.isBefore(today.minusDays(BACK_DAYS - 1));
  }

  // 이번 달에 더 휴식 처리할 수 있는 날 수
  public static int daysLeft(long usedThisMonth) {
    return (int) Math.max(0, MONTHLY_LIMIT - usedThisMonth);
  }

  // 그 날짜까지 연속으로 쉰 날 수 (그 날짜 포함). FE 의 getRestStreak 과 같습니다.
  //
  //   쉰 날 10/15 · 10/16 · 10/17, 기준 10/17  →  3
  //   쉰 날 10/15 · 10/17, 기준 10/17          →  1
  //   기준 날짜에 안 쉼                         →  0
  public static int streak(List<LocalDate> restDates, LocalDate date) {
    Set<LocalDate> dates = new HashSet<>(restDates);
    int streak = 0;
    while (dates.contains(date.minusDays(streak))) {
      streak++;
    }
    return streak;
  }

  // 휴식을 처리한 뒤 회복 모드 · 기분 저하 점검으로 넘길지. FE 의 needsRecoveryCheck 와 같습니다. (이슈 #232)
  // 3일 연속 쉬었거나, 그 날짜가 든 달의 한도(4번)를 다 썼으면 true 입니다.
  //
  //   10/15 · 10/16 · 10/17 을 쉼, 기준 10/17      →  true   (3일 연속)
  //   10/02 · 10/09 · 10/12 · 10/17 을 쉼, 기준 10/17 →  true   (10월 한도를 다 씀)
  //   10/15 · 10/17 을 쉼, 기준 10/17              →  false
  public static boolean needsRecoveryCheck(List<LocalDate> restDates, LocalDate date) {
    long usedThisMonth = 0;
    for (LocalDate restDate : restDates) {
      if (restDate.getYear() == date.getYear() && restDate.getMonth() == date.getMonth()) {
        usedThisMonth++;
      }
    }
    return streak(restDates, date) >= STREAK_FOR_RECOVERY || daysLeft(usedThisMonth) == 0;
  }
}
