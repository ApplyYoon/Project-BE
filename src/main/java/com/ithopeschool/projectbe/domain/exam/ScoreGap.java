package com.ithopeschool.projectbe.domain.exam;

import com.ithopeschool.projectbe.domain.session.StudyTime;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 시험의 "예상 점수" 와 "실제 점수" 의 차이를 계산합니다. (이슈 #40 분석 ⑤ 인식-실제 갭 중 점수 부분)
// 시험 전에 적어 둔 예상 점수와 시험 후의 실제 점수를 비교해서, 스스로를 높게 보는지 낮게 보는지 알려 줍니다.
// 차이는 언제나 "실제 − 예상" 입니다. 음수면 예상보다 낮게 나온 것(높게 예상한 것)입니다.
//
//   예상 85, 실제 80   →  -5
//   예상 80, 실제 83   →  +3
//   차이가 -5, -8, -2, -8 인 네 과목   →  평균 -5.75  →  -6   ("예상보다 평균 6점 낮았어")
//
// FE 의 getScoreDiff · getAverageDiff · getLargestGapScores · getResultHeadline ·
// getStudyMinutesBeforeExam
// (src/lib/exams.ts) 과 같은 규칙·같은 문장입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 숫자가 어긋나지 않습니다.
public final class ScoreGap {

  // "시험 전 며칠 동안 공부한 시간" 을 볼 때 거슬러 올라가는 일수 (시험 전 2주)
  public static final int STUDY_LOOKBACK_DAYS = 14;

  // 한 과목의 점수입니다.
  // subjectId: 과목 번호 / expectedScore: 시험 전에 적은 예상 점수
  // actualScore: 시험 후의 실제 점수. 아직 안 적었으면 null
  // (시험 테이블은 아직 없고 설계는 학생이 합니다. 그래서 엔티티가 아니라 필요한 값만 받습니다)
  public record SubjectScore(Long subjectId, int expectedScore, Integer actualScore) {}

  // 공부 기록 하나에서 계산에 쓰는 값만 모은 것입니다.
  // startedAt: 시작한 시각 / studyMillis: 순공시간(밀리초). StudyTime.studyMillis 의 결과를 넣습니다.
  public record StudyRecord(Instant startedAt, long studyMillis) {}

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private ScoreGap() {}

  // 한 과목의 "실제 − 예상" 점수 차이를 돌려줍니다. 실제 점수가 아직 없으면 null 입니다.
  //
  //   예상 85, 실제 80  →  -5   /   예상 80, 실제 83  →  3
  public static Integer diffOf(SubjectScore score) {
    if (score.actualScore() == null) {
      return null;
    }
    return score.actualScore() - score.expectedScore();
  }

  // 실제 점수가 있는 과목들의 평균 차이를 정수로 돌려줍니다. 하나도 없으면 null 입니다.
  // 소수는 0 에서 먼 쪽으로 반올림합니다. (양수·음수를 똑같이 다루려는 규칙)
  //
  //   차이 -5, -8, -2, -8  →  평균 -5.75  →  -6
  //   차이 -2, -3          →  평균 -2.5   →  -3   (그냥 반올림하면 -2 가 됩니다)
  public static Integer averageDiff(List<SubjectScore> scores) {
    int sum = 0;
    int count = 0;
    for (SubjectScore score : scores) {
      Integer diff = diffOf(score);
      if (diff != null) {
        sum += diff;
        count++;
      }
    }
    if (count == 0) {
      return null;
    }

    double average = (double) sum / count;
    // 크기만 반올림한 뒤에 부호를 다시 붙입니다. (-2.5 → 2.5 → 3 → -3)
    long rounded = Math.round(Math.abs(average));
    return (int) (average < 0 ? -rounded : rounded);
  }

  // 점수 차이를 부호와 함께 보여줍니다.
  //
  //   3  →  "+3"   /   -5  →  "-5"   /   0  →  "0"
  public static String formatDiff(int diff) {
    return diff > 0 ? "+" + diff : String.valueOf(diff);
  }

  // 평균 차이로 요약 문장을 만듭니다. isPolite 가 true 면 존댓말, false 면 반말입니다.
  //
  //   -6           →  "예상보다 평균 6점 낮았어"
  //   -6, 존댓말   →  "예상보다 평균 6점 낮았어요"
  //   0            →  "예상과 같았어"
  public static String headline(int averageDiff, boolean isPolite) {
    String ending = isPolite ? "어요" : "어";
    if (averageDiff == 0) {
      return "예상과 같았" + ending;
    }
    int amount = Math.abs(averageDiff);
    return averageDiff > 0
        ? "예상보다 평균 " + amount + "점 높았" + ending
        : "예상보다 평균 " + amount + "점 낮았" + ending;
  }

  // 예상과 실제의 차이가 큰 과목을 큰 순서대로 count 개 돌려줍니다.
  // 차이가 0 인 과목과 실제 점수가 없는 과목은 뺍니다.
  // 크기만 보고(부호 무시), 크기가 같으면 원래 순서를 지킵니다.
  public static List<SubjectScore> largestGaps(List<SubjectScore> scores, int count) {
    List<SubjectScore> withGap = new ArrayList<>();
    for (SubjectScore score : scores) {
      Integer diff = diffOf(score);
      if (diff != null && diff != 0) {
        withGap.add(score);
      }
    }

    // 차이의 크기가 큰 것이 앞으로 오게 줄 세웁니다. 크기가 같은 것끼리는 원래 순서가 그대로입니다.
    withGap.sort(
        Comparator.comparingInt((SubjectScore score) -> Math.abs(diffOf(score))).reversed());
    return List.copyOf(withGap.subList(0, Math.min(count, withGap.size())));
  }

  // 시험 전 2주(시험 14일 전 ~ 하루 전) 동안 공부한 시간(분)입니다.
  // 기록은 "시작한 날"(새벽 4시 기준) 로 세고, 시간은 모두 더한 뒤 마지막에 한 번만 반올림합니다.
  // 시험 당일의 공부는 "시험 전" 이 아니라서 뺍니다.
  //
  // records: "한 과목" 의 공부 기록들. 다른 과목의 기록과 노는 기록은 부르는 쪽에서 빼고 넘깁니다.
  // examDate: 시험 날짜
  public static int studyMinutesBeforeExam(List<StudyRecord> records, LocalDate examDate) {
    LocalDate fromDate = examDate.minusDays(STUDY_LOOKBACK_DAYS);
    LocalDate toDate = examDate.minusDays(1);

    long totalMillis = 0;
    for (StudyRecord record : records) {
      LocalDate startedDate = DayRule.dateOf(record.startedAt());
      if (!startedDate.isBefore(fromDate) && !startedDate.isAfter(toDate)) {
        totalMillis += record.studyMillis();
      }
    }
    return StudyTime.toMinutes(totalMillis);
  }
}
