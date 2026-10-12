package com.ithopeschool.projectbe.domain.insight;

import com.ithopeschool.projectbe.domain.summary.DaySummary;
import com.ithopeschool.projectbe.domain.summary.DaySummary.DaySession;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

// 인사이트의 "근거" 를 만듭니다. (이슈 #41 "모든 후보에 근거 날짜가 붙는다", "5일 미만이면 가설로만 나간다")
// 결론만 말하면 믿기 어렵습니다. 결론이 나온 날짜마다 그날의 순공시간과 컨디션을 보여줘서, 근거가 눈에 보이게 합니다.
//
// 정해 둔 규칙
//   1. 근거는 날짜마다 한 줄입니다. 같은 날짜가 여러 번 들어와도 한 줄이고, 최근 날짜가 맨 위에 옵니다.
//   2. 그날 순공시간은 DaySummary.of 가 계산합니다. 공부 기록이 없으면 0분입니다.
//   3. 컨디션은 그날 체크인에 적은 값(1 ~ 5)입니다. 체크인을 안 했으면 null 입니다. (0 이나 3 으로 채우지 않습니다)
//   4. 비교하는 조건(예: 오전에 공부한 날 / 밤에 공부한 날)이 각각 5일 이상 쌓이기 전이면 "가설" 입니다.
//      하나라도 5일 미만이면 가설입니다. 며칠 안 되는 기록으로 "너는 오전형이야" 라고 단정하지 않습니다.
//
// 예시
//   날짜 [10/03, 10/05, 10/03]  →  [10/05 한 줄, 10/03 한 줄]
//   오전 6일 · 밤 4일로 비교    →  가설 (밤이 5일 미만)
//   오전 6일 · 밤 5일로 비교    →  가설 아님
//
// FE 의 buildEvidenceRows (src/lib/insightEvidence.ts) 와 같은 규칙입니다. 가설의 기준(5일)은 FE
// 타입(Insight.isHypothesis)의 설명과 같습니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 근거가 어긋나지 않습니다.
public final class InsightEvidence {

  // 비교하는 조건마다 이만큼의 날이 쌓여야 "가설" 딱지를 뗍니다.
  public static final int MIN_DAYS_PER_CONDITION = 5;

  // 근거 한 줄입니다.
  // date: 날짜 / studyMinutes: 그날 순공시간(분). 공부 기록이 없으면 0
  // condition: 그날 체크인에 적은 컨디션(1 ~ 5). 체크인을 안 했으면 null
  public record Row(LocalDate date, int studyMinutes, Integer condition) {}

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private InsightEvidence() {}

  // 근거 날짜들로 한 줄씩 만듭니다. (규칙 1 ~ 3)
  //
  // dates: 결론의 근거가 된 날짜들. 순서가 뒤섞여 있어도, 같은 날짜가 여러 번 있어도 됩니다.
  // sessions: 공부 기록. 다른 날짜가 섞여 있어도 됩니다. 노는 기록은 부르는 쪽에서 빼고 넘깁니다.
  // conditionByDate: 날짜별 컨디션 (날짜 → 1 ~ 5). 체크인을 안 한 날은 들어 있지 않습니다.
  public static List<Row> rowsOf(
      List<LocalDate> dates, List<DaySession> sessions, Map<LocalDate, Integer> conditionByDate) {
    // TreeSet 은 같은 값을 한 번만 담고, 정해 준 순서(여기서는 최근 날짜가 앞)로 줄을 세워 줍니다.
    TreeSet<LocalDate> uniqueDates = new TreeSet<>(Comparator.reverseOrder());
    uniqueDates.addAll(dates);

    List<Row> rows = new ArrayList<>();
    for (LocalDate date : uniqueDates) {
      // 계획은 순공시간 계산에 필요 없어서 빈 목록을 넘깁니다.
      int studyMinutes = DaySummary.of(date, List.of(), sessions).studyMinutes();
      rows.add(new Row(date, studyMinutes, conditionByDate.get(date)));
    }
    return rows;
  }

  // 이 인사이트를 "가설" 로만 내보내야 하면 true 입니다. (규칙 4)
  //
  // dayCounts: 비교하는 조건마다 쌓인 날 수. 예: 오전 6일 · 밤 4일이면 [6, 4]
  //   비교할 조건이 하나도 없으면(빈 목록) 근거가 없는 것이라 가설입니다.
  public static boolean isHypothesis(List<Integer> dayCounts) {
    if (dayCounts.isEmpty()) {
      return true;
    }
    for (int dayCount : dayCounts) {
      if (dayCount < MIN_DAYS_PER_CONDITION) {
        return true;
      }
    }
    return false;
  }
}
