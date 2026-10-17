package com.ithopeschool.projectbe.domain.restday;

import java.time.LocalDate;

// 휴식 처리 한도 · 기한 계산기입니다. (최종 기획서 「4.5 상담」 휴식 처리, 이슈 #225) 값만 계산하는 순수 함수입니다.
//
//   한도: 한 달(달력의 1일 ~ 말일)에 4번까지
//   기한: 오늘과 그 전 6일 (오늘 포함 7일). 미리 "내일 쉴게" 는 없습니다. 오늘은 새벽 4시 기준(DayRule)입니다.
//
//   isAllowedDate(10-12, 오늘 10-12) → true     isAllowedDate(10-13, 오늘 10-12) → false
//   isAllowedDate(10-06, 오늘 10-12) → true     isAllowedDate(10-05, 오늘 10-12) → false  (8일째)
//   daysLeft(이번 달 3번) → 1                    daysLeft(이번 달 4번) → 0
public final class RestDayRule {

  // 한 달에 휴식으로 처리할 수 있는 날 수. 숫자는 출발값입니다. 바꿀 때는 FE(#407)의 상수와 같이 고칩니다.
  public static final int MONTHLY_LIMIT = 4;

  // 며칠 전까지 거슬러 휴식으로 처리할 수 있는지 (오늘 포함). FE(#407)의 REST_BACK_DAYS 와 같은 숫자입니다.
  public static final int BACK_DAYS = 7;

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
}
