package com.ithopeschool.projectbe.domain.exam;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.exam.ScoreGap.StudyRecord;
import com.ithopeschool.projectbe.domain.exam.ScoreGap.SubjectScore;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// 예상 점수와 실제 점수의 차이 계산 규칙(이슈 #40)을 확인합니다.
// feExample 로 시작하는 테스트는 FE 함수(src/lib/exams.ts) 주석의 예시를 그대로 옮긴 것입니다.
class ScoreGapTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private static final long MINUTE = 60_000;

  // 실제 점수까지 적은 과목을 만듭니다.
  private static SubjectScore score(long subjectId, int expected, int actual) {
    return new SubjectScore(subjectId, expected, actual);
  }

  // 아직 실제 점수를 안 적은 과목을 만듭니다.
  private static SubjectScore notYet(long subjectId, int expected) {
    return new SubjectScore(subjectId, expected, null);
  }

  // 차이(실제 − 예상)가 주어진 값이 되도록 과목들을 만듭니다. 예상 점수는 모두 80점입니다.
  private static List<SubjectScore> scoresWithDiffs(int... diffs) {
    List<SubjectScore> scores = new ArrayList<>();
    for (int i = 0; i < diffs.length; i++) {
      scores.add(score(i + 1, 80, 80 + diffs[i]));
    }
    return scores;
  }

  // ─── diffOf: 한 과목의 차이 ───

  @Test
  void feExampleScoreDiff() {
    // FE 예) 예상 85, 실제 80 → -5   /   예상 80, 실제 83 → 3
    assertThat(ScoreGap.diffOf(score(1, 85, 80))).isEqualTo(-5);
    assertThat(ScoreGap.diffOf(score(1, 80, 83))).isEqualTo(3);
  }

  @Test
  void diffIsNullBeforeTheActualScoreIsEntered() {
    // 실제 점수가 아직 없으면 0 이 아니라 null 입니다. ("차이 없음" 과 "아직 모름" 은 다릅니다)
    assertThat(ScoreGap.diffOf(notYet(1, 85))).isNull();
    assertThat(ScoreGap.diffOf(score(1, 85, 85))).isEqualTo(0);
  }

  // ─── averageDiff: 평균 차이 ───

  @Test
  void feExampleAverageDiff() {
    // FE 예) 차이 -5, -8, -2, -8 → 평균 -5.75 → -6
    assertThat(ScoreGap.averageDiff(scoresWithDiffs(-5, -8, -2, -8))).isEqualTo(-6);
  }

  @Test
  void averageRoundsAwayFromZero() {
    // 소수는 0 에서 먼 쪽으로 반올림합니다. 양수와 음수가 똑같이 다뤄집니다.
    // 평균 +2.5 → +3, 평균 -2.5 → -3 (그냥 반올림하면 -2 가 되어 양수와 달라집니다)
    assertThat(ScoreGap.averageDiff(scoresWithDiffs(2, 3))).isEqualTo(3);
    assertThat(ScoreGap.averageDiff(scoresWithDiffs(-2, -3))).isEqualTo(-3);
    // 평균 +2.33… → +2, 평균 -2.33… → -2
    assertThat(ScoreGap.averageDiff(scoresWithDiffs(2, 2, 3))).isEqualTo(2);
    assertThat(ScoreGap.averageDiff(scoresWithDiffs(-2, -2, -3))).isEqualTo(-2);
  }

  @Test
  void averageSkipsSubjectsWithoutActualScore() {
    // 실제 점수가 없는 과목은 평균에서 뺍니다. (0 으로 넣지 않습니다)
    List<SubjectScore> scores = List.of(score(1, 80, 70), notYet(2, 90), score(3, 80, 74));

    // (-10 + -6) ÷ 2 = -8
    assertThat(ScoreGap.averageDiff(scores)).isEqualTo(-8);
  }

  @Test
  void averageIsNullWithoutAnyActualScore() {
    // 실제 점수가 하나도 없으면 null 입니다.
    assertThat(ScoreGap.averageDiff(List.of())).isNull();
    assertThat(ScoreGap.averageDiff(List.of(notYet(1, 80), notYet(2, 90)))).isNull();
  }

  @Test
  void smallAverageRoundsToZero() {
    // 높게 본 과목과 낮게 본 과목이 섞여서 평균이 0 에 가까우면 0 입니다. (+3, -2 → 평균 +0.5 → +1 / +1, -1 → 0)
    assertThat(ScoreGap.averageDiff(scoresWithDiffs(1, -1))).isEqualTo(0);
    assertThat(ScoreGap.averageDiff(scoresWithDiffs(3, -2))).isEqualTo(1);
    assertThat(ScoreGap.averageDiff(scoresWithDiffs(1, -1, -1))).isEqualTo(0);
  }

  // ─── formatDiff · headline: 보여줄 글자 ───

  @Test
  void feExampleFormatDiff() {
    // FE 예) 3 → '+3'   /   -5 → '-5'   /   0 → '0'
    assertThat(ScoreGap.formatDiff(3)).isEqualTo("+3");
    assertThat(ScoreGap.formatDiff(-5)).isEqualTo("-5");
    assertThat(ScoreGap.formatDiff(0)).isEqualTo("0");
  }

  @Test
  void feExampleHeadline() {
    // FE 예) -6 → '예상보다 평균 6점 낮았어'   /   -6, 존댓말 → '예상보다 평균 6점 낮았어요'   /   0 → '예상과 같았어'
    assertThat(ScoreGap.headline(-6, false)).isEqualTo("예상보다 평균 6점 낮았어");
    assertThat(ScoreGap.headline(-6, true)).isEqualTo("예상보다 평균 6점 낮았어요");
    assertThat(ScoreGap.headline(0, false)).isEqualTo("예상과 같았어");
  }

  @Test
  void headlineForHigherThanExpected() {
    // 예상보다 잘 나왔을 때와, 존댓말로 "같았어요" 인 경우입니다.
    assertThat(ScoreGap.headline(4, false)).isEqualTo("예상보다 평균 4점 높았어");
    assertThat(ScoreGap.headline(4, true)).isEqualTo("예상보다 평균 4점 높았어요");
    assertThat(ScoreGap.headline(0, true)).isEqualTo("예상과 같았어요");
  }

  // ─── largestGaps: 차이가 큰 과목 ───

  @Test
  void largestGapsAreSortedBySizeIgnoringSign() {
    // 부호는 보지 않고 크기만 봅니다. 차이 +3, -8, +5, -1 → 크기 8, 5 인 과목 둘
    List<SubjectScore> scores = scoresWithDiffs(3, -8, 5, -1);

    assertThat(ScoreGap.largestGaps(scores, 2)).containsExactly(scores.get(1), scores.get(2));
  }

  @Test
  void largestGapsSkipZeroAndMissingScores() {
    // 차이가 0 인 과목과 실제 점수가 없는 과목은 넣지 않습니다. 그래서 요청한 개수보다 적게 나올 수 있습니다.
    List<SubjectScore> scores = List.of(score(1, 80, 80), notYet(2, 90), score(3, 80, 77));

    assertThat(ScoreGap.largestGaps(scores, 2)).containsExactly(scores.get(2));
    assertThat(ScoreGap.largestGaps(List.of(score(1, 80, 80)), 2)).isEmpty();
  }

  @Test
  void sameSizeGapsKeepTheirOriginalOrder() {
    // 크기가 같으면 원래 순서를 지킵니다. 차이 -5, +5, -5 중 둘을 고르면 앞의 둘입니다.
    List<SubjectScore> scores = scoresWithDiffs(-5, 5, -5);

    assertThat(ScoreGap.largestGaps(scores, 2)).containsExactly(scores.get(0), scores.get(1));
  }

  @Test
  void largestGapsDoesNotChangeTheGivenList() {
    // 줄을 세우느라 받은 목록의 순서를 바꾸지 않습니다.
    List<SubjectScore> scores = new ArrayList<>(scoresWithDiffs(1, -9, 4));
    List<SubjectScore> before = List.copyOf(scores);

    ScoreGap.largestGaps(scores, 2);

    assertThat(scores).isEqualTo(before);
  }

  // ─── studyMinutesBeforeExam: 시험 전 2주 공부 시간 ───

  // 한국 시간 10월 "day일 시:분" 에 시작해서 minutes 분 공부한 기록을 만듭니다.
  private static StudyRecord record(int day, int hour, int minute, int minutes) {
    Instant startedAt = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, SEOUL).toInstant();
    return new StudyRecord(startedAt, minutes * MINUTE);
  }

  @Test
  void countsTheTwoWeeksRightBeforeTheExam() {
    // 시험이 10월 20일이면 10월 6일 ~ 10월 19일의 공부만 셉니다.
    // 10월 5일(15일 전)과 시험 당일(10월 20일)의 공부는 넣지 않습니다.
    LocalDate examDate = LocalDate.of(2026, 10, 20);
    List<StudyRecord> records =
        List.of(
            record(5, 19, 0, 100),
            record(6, 19, 0, 30),
            record(19, 19, 0, 40),
            record(20, 8, 0, 100));

    assertThat(ScoreGap.studyMinutesBeforeExam(records, examDate)).isEqualTo(70);
  }

  @Test
  void studyBeforeExamFollowsTheFourAmDayRule() {
    // 기록은 "시작한 날"(새벽 4시 기준)로 셉니다. 시험이 10월 20일일 때:
    LocalDate examDate = LocalDate.of(2026, 10, 20);

    // 10월 20일 03:30 에 시작한 공부는 아직 10월 19일 → 시험 전이라서 셉니다.
    assertThat(ScoreGap.studyMinutesBeforeExam(List.of(record(20, 3, 30, 25)), examDate))
        .isEqualTo(25);
    // 10월 20일 04:00 에 시작한 공부는 시험 당일 → 세지 않습니다.
    assertThat(ScoreGap.studyMinutesBeforeExam(List.of(record(20, 4, 0, 25)), examDate))
        .isEqualTo(0);
    // 10월 6일 03:30 에 시작한 공부는 아직 10월 5일 → 2주보다 앞이라 세지 않습니다.
    assertThat(ScoreGap.studyMinutesBeforeExam(List.of(record(6, 3, 30, 25)), examDate))
        .isEqualTo(0);
  }

  @Test
  void studyMinutesAreRoundedOnlyOnce() {
    // 기록마다 반올림하지 않고, 모두 더한 뒤 한 번만 반올림합니다. 20분 24초 × 3 = 61분 12초 → 61분
    LocalDate examDate = LocalDate.of(2026, 10, 20);
    long length = 20 * MINUTE + 24_000;
    List<StudyRecord> records = new ArrayList<>();
    for (int day = 10; day <= 12; day++) {
      Instant startedAt = ZonedDateTime.of(2026, 10, day, 19, 0, 0, 0, SEOUL).toInstant();
      records.add(new StudyRecord(startedAt, length));
    }

    assertThat(ScoreGap.studyMinutesBeforeExam(records, examDate)).isEqualTo(61);
  }

  @Test
  void noRecordsMeansZeroMinutes() {
    // 기록이 없으면 0분입니다.
    assertThat(ScoreGap.studyMinutesBeforeExam(List.of(), LocalDate.of(2026, 10, 20))).isEqualTo(0);
  }
}
