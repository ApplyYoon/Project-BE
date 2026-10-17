package com.ithopeschool.projectbe.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.care.CareAction;
import com.ithopeschool.projectbe.domain.care.CareLevel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 시험 앞뒤로 관리 강도가 낮아졌다가, 기간이 끝나면 원래 한도로 돌아가는지 날짜별로 확인합니다. (이슈 #58 · #232)
//
// 규칙 (최종 기획서 「4.5 안전 브레이크」): 시험 기간은 관리 강도 상한이 2(넛지)입니다. (ExamPeriod.capCareLevel)
// 예전(2026-10-11)에는 0(회복)으로 내렸지만, 시험 공부는 계속 챙기되 채찍만 빼도록 바뀌었습니다.
// 기간이 끝나면 사용자의 한도(여기서는 3)가 다시 적용되고, 단계는 "하루에 한 칸씩" 다시 올라갑니다.
class ExamPeriodCareLevelTest {

  private static final LocalDate EXAM = LocalDate.of(2026, 10, 17);
  private static final int MAX_LEVEL = 3;

  @Test
  @DisplayName("시험 4일 전 3 → 기간(3일 전 ~ 2일 후) 동안 2 → 끝나면 하루 한 칸씩 3 까지 돌아간다")
  void capsAtTwoDuringThePeriodAndComesBackAfter() {
    // 매일 "채찍" 대응(한 칸 올림)이 나왔다고 가정합니다. 시험 4일 전(10/13)에는 이미 한도인 3 입니다.
    int level = MAX_LEVEL;
    List<Integer> levels = new ArrayList<>();
    for (LocalDate day = LocalDate.of(2026, 10, 13);
        !day.isAfter(LocalDate.of(2026, 10, 23));
        day = day.plusDays(1)) {
      boolean isExamPeriod = ExamPeriod.isNearExam(day, List.of(EXAM));
      level =
          ExamPeriod.capCareLevel(
              CareLevel.of(level, CareAction.WHIP, MAX_LEVEL, false), isExamPeriod);
      levels.add(level);
    }

    // 10/13 10/14 10/15 10/16 10/17 10/18 10/19 | 10/20 10/21 10/22 10/23
    //   3     2     2     2     2     2     2   |   3     3     3     3
    assertThat(levels).containsExactly(3, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3);
  }

  @Test
  @DisplayName("기간이 끝난 뒤에도 한도(3)를 넘지 않는다")
  void neverGoesAboveTheLimitAfterThePeriod() {
    int level = 0;
    for (int day = 0; day < 10; day++) {
      level = CareLevel.of(level, CareAction.WHIP, MAX_LEVEL, false);
    }
    assertThat(level).isEqualTo(MAX_LEVEL);
  }
}
