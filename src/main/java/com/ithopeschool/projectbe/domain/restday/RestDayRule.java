package com.ithopeschool.projectbe.domain.restday;

import java.time.LocalDate;

// 휴식 처리 한도 · 기한 계산기입니다. (최종 기획서 「4.5 상담」 휴식 처리, 이슈 #225) 값만 계산하는 순수 함수입니다.
//
//   한도: 한 달(달력의 1일 ~ 말일)에 4번까지
//   기한: 지난 날짜와 오늘만 (미리 "내일 쉴게" 는 없음). 오늘은 새벽 4시 기준(DayRule)입니다.
//
//   isAllowedDate(10-12, 오늘 10-12) → true     isAllowedDate(10-13, 오늘 10-12) → false
//   daysLeft(이번 달 3번) → 1                    daysLeft(이번 달 4번) → 0
public final class RestDayRule {

  // 한 달에 휴식으로 처리할 수 있는 날 수. 숫자는 출발값입니다. 바꿀 때는 FE(#407)의 상수와 같이 고칩니다.
  public static final int MONTHLY_LIMIT = 4;

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private RestDayRule() {}

  // 휴식으로 처리할 수 있는 날짜인지 (오늘이거나 지난 날이면 true)
  public static boolean isAllowedDate(LocalDate date, LocalDate today) {
    return !date.isAfter(today);
  }

  // 이번 달에 더 휴식 처리할 수 있는 날 수
  public static int daysLeft(long usedThisMonth) {
    return (int) Math.max(0, MONTHLY_LIMIT - usedThisMonth);
  }
}
