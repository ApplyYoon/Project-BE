package com.ithopeschool.projectbe.domain.safety;

import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

// "시험 기간" 검사기입니다. (이슈 #58, 기획서 v2 5장 「안전 브레이크」)
// 시험 직전 3일부터 직후 2일까지는 가장 예민한 때라서, 이 기간에는 채찍을 낮춥니다.
//
//   오늘이 시험 3일 전  →  true
//   오늘이 시험 당일    →  true
//   오늘이 시험 2일 후  →  true
//   오늘이 시험 4일 전  →  false
//   오늘이 시험 3일 후  →  false
//
// FE 의 isNearExam (src/lib/safetyBrake.ts) 과 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 두 화면의 판단이 어긋나지 않습니다.
public final class ExamPeriod {

  // 시험 며칠 전부터 시험 기간으로 보는지
  public static final int DAYS_BEFORE = 3;

  // 시험 며칠 뒤까지 시험 기간으로 보는지
  public static final int DAYS_AFTER = 2;

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private ExamPeriod() {}

  // 지금(now)이 시험 기간이면 true 를 돌려줍니다.
  // "오늘" 은 하루의 기준(새벽 4시, DayRule)으로 정합니다. 새벽 2시는 아직 어제입니다.
  public static boolean isNearExam(Instant now, List<LocalDate> examDates) {
    return isNearExam(DayRule.dateOf(now), examDates);
  }

  // today 가 시험 기간(직전 3일 ~ 직후 2일)에 들어가면 true 를 돌려줍니다.
  // 시험이 여러 개면 하나라도 걸리면 true 이고, 시험이 없으면 false 입니다.
  public static boolean isNearExam(LocalDate today, List<LocalDate> examDates) {
    for (LocalDate examDate : examDates) {
      // 오늘부터 시험까지 며칠 남았는지 셉니다. (오늘이 시험이면 0, 내일이면 1, 어제였으면 -1)
      long daysUntilExam = ChronoUnit.DAYS.between(today, examDate);
      if (daysUntilExam <= DAYS_BEFORE && daysUntilExam >= -DAYS_AFTER) {
        return true;
      }
    }
    return false;
  }
}
