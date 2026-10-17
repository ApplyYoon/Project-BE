package com.ithopeschool.projectbe.domain.safety;

import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

// "시험 기간" 검사기입니다. (이슈 #58 · #232, 최종 기획서 「4.5 안전 브레이크」)
// 시험 직전 3일부터 직후 2일까지는 가장 예민한 때라서, 이 기간에는 관리 강도가 2(넛지)를 넘지 않습니다.
// 회복 모드(0)로 내리지는 않습니다. 시험 공부는 계속 챙기되, 채찍(3~4단계)만 쓰지 않습니다.
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

  // 시험 기간의 관리 강도 상한 (2 = 넛지). FE 의 EXAM_STAGE_CAP (src/lib/careStage.ts) 과 같습니다. (이슈 #232)
  // 예전(2026-10-11)에는 시험 기간을 안전 브레이크로 보고 0(회복)으로 내렸지만, 최종 기획서에서 상한 2 로 바뀌었습니다.
  public static final int CARE_LEVEL_CAP = 2;

  // 시험 기간이라 관리 강도를 낮췄을 때, 학생에게 "왜" 를 알려 주는 한 줄입니다. (이슈 #58 "낮춘 이유를 말할 문장")
  // 그날은 CareDecision.reason 대신 이 문장을 보여 주면 됩니다. 혼내지 않고, 독촉을 쉬는 이유만 담담하게 말합니다.
  public static final String REASON = "시험 기간이라 독촉은 쉬어갈게. 컨디션 챙기면서 할 수 있는 만큼만 하자.";

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

  // 시험 기간이면 관리 강도를 2(CARE_LEVEL_CAP)까지로 낮추고, 아니면 그대로 돌려줍니다.
  // 2 보다 낮은 단계는 올리지 않습니다.
  //
  //   3, 시험 기간      →  2
  //   1, 시험 기간      →  1
  //   3, 시험 기간 아님  →  3
  public static int capCareLevel(int careLevel, boolean isExamPeriod) {
    return isExamPeriod ? Math.min(careLevel, CARE_LEVEL_CAP) : careLevel;
  }

  // 오늘이 시험 기간이면 이유 문장(REASON)을, 아니면 null 을 돌려줍니다.
  // 기간이 끝나면 null 이 되어서, 관리 강도와 이유 문장 모두 원래대로 돌아갑니다.
  //
  //   오늘 10/15, 시험 10/17  →  "시험 기간이라 독촉은 쉬어갈게. …"
  //   오늘 10/20, 시험 10/17  →  null  (시험 3일 후, 기간이 끝남)
  public static String reasonFor(LocalDate today, List<LocalDate> examDates) {
    return isNearExam(today, examDates) ? REASON : null;
  }
}
