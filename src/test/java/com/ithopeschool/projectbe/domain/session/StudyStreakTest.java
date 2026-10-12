package com.ithopeschool.projectbe.domain.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// 공부 연속 일수 규칙을 확인합니다.
// feExample 로 시작하는 3개는 FE 함수(getStudyStreak) 주석의 예시를 그대로 옮긴 것입니다.
class StudyStreakTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  // 한국 시간 10월 "day일 hour시" 를 순간(Instant)으로 바꾸는 도우미입니다.
  private static Instant at(int day, int hour) {
    return ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, SEOUL).toInstant();
  }

  // 테스트에서 쓰는 "지금": 10월 12일 저녁 8시
  private static final Instant NOW = at(12, 20);

  // 10월 from 일부터 to 일까지 매일 저녁 7시에 공부를 시작한 기록을 만듭니다.
  private static List<Instant> studiedEveryDay(int from, int to) {
    List<Instant> startedAts = new ArrayList<>();
    for (int day = from; day <= to; day++) {
      startedAts.add(at(day, 19));
    }
    return startedAts;
  }

  // ─── FE 예시 ───

  @Test
  void feExample1NoRecordsIsZero() {
    // FE 예) 기록 0개 → 0
    assertThat(StudyStreak.streakOf(List.of(), NOW)).isEqualTo(0);
  }

  @Test
  void feExample2OnlyTodayIsOne() {
    // FE 예) 오늘만 공부 → 1
    assertThat(StudyStreak.streakOf(List.of(at(12, 19)), NOW)).isEqualTo(1);
  }

  @Test
  void feExample3TodayNotYetStillKeepsTheStreak() {
    // FE 예) 어제까지 3일 연속, 오늘은 아직 → 3 (오늘은 아직 기회가 남아 있어서 끊긴 것이 아닙니다)
    assertThat(StudyStreak.streakOf(studiedEveryDay(9, 11), NOW)).isEqualTo(3);
  }

  // ─── 세는 규칙 ───

  @Test
  void todayIsAddedOnceStudied() {
    // 어제까지 3일 연속이었고 오늘도 공부했으면 4 입니다.
    assertThat(StudyStreak.streakOf(studiedEveryDay(9, 12), NOW)).isEqualTo(4);
  }

  @Test
  void missingYesterdayAndTodayIsZero() {
    // 오늘도 어제도 안 했으면, 그 전에 며칠을 이어 왔든 0 입니다.
    assertThat(StudyStreak.streakOf(studiedEveryDay(5, 10), NOW)).isEqualTo(0);
  }

  @Test
  void stopsAtTheFirstMissingDay() {
    // 하루라도 빠진 날을 만나면 거기서 멈춥니다. 10·11·12일은 했고 9일을 빠뜨렸으면, 그 전(6 ~ 8일)은 세지 않습니다.
    List<Instant> startedAts = new ArrayList<>(studiedEveryDay(6, 8));
    startedAts.addAll(studiedEveryDay(10, 12));

    assertThat(StudyStreak.streakOf(startedAts, NOW)).isEqualTo(3);
  }

  @Test
  void severalSessionsInOneDayCountOnce() {
    // 하루에 여러 번 공부해도 하루입니다.
    List<Instant> startedAts = List.of(at(12, 9), at(12, 14), at(12, 19), at(11, 10), at(11, 21));

    assertThat(StudyStreak.streakOf(startedAts, NOW)).isEqualTo(2);
  }

  @Test
  void orderOfRecordsDoesNotMatter() {
    // 기록의 순서가 뒤섞여 있어도 결과는 같습니다.
    List<Instant> shuffled = List.of(at(10, 19), at(12, 19), at(9, 19), at(11, 19));

    assertThat(StudyStreak.streakOf(shuffled, NOW)).isEqualTo(4);
  }

  @Test
  void streakContinuesAcrossMonths() {
    // 달이 바뀌어도 이어서 셉니다. 9월 29일부터 10월 12일까지 14일 연속
    List<Instant> startedAts = new ArrayList<>(studiedEveryDay(1, 12));
    startedAts.add(ZonedDateTime.of(2026, 9, 29, 19, 0, 0, 0, SEOUL).toInstant());
    startedAts.add(ZonedDateTime.of(2026, 9, 30, 19, 0, 0, 0, SEOUL).toInstant());

    assertThat(StudyStreak.streakOf(startedAts, NOW)).isEqualTo(14);
  }

  // ─── 하루의 기준 (새벽 4시) ───

  @Test
  void studyBeforeFourAmBelongsToTheDayBefore() {
    // 10월 12일 새벽 2시에 한 공부는 10월 11일의 공부입니다.
    // 그래서 10월 12일 저녁에 보면 "오늘은 아직 안 함, 어제는 함" → 1
    assertThat(StudyStreak.streakOf(List.of(at(12, 2)), NOW)).isEqualTo(1);
    assertThat(StudyStreak.hasStudiedToday(List.of(at(12, 2)), NOW)).isFalse();
  }

  @Test
  void nowBeforeFourAmIsStillYesterday() {
    // 지금이 10월 13일 새벽 2시면 아직 10월 12일입니다. 10월 12일 저녁의 공부가 "오늘" 공부입니다.
    Instant lateNight = at(13, 2);

    assertThat(StudyStreak.hasStudiedToday(List.of(at(12, 19)), lateNight)).isTrue();
    assertThat(StudyStreak.streakOf(studiedEveryDay(10, 12), lateNight)).isEqualTo(3);
    // 새벽 4시가 되면 10월 13일이 되고, 오늘은 아직 안 한 날입니다. 연속 일수는 그대로 3 입니다.
    assertThat(StudyStreak.hasStudiedToday(List.of(at(12, 19)), at(13, 4))).isFalse();
    assertThat(StudyStreak.streakOf(studiedEveryDay(10, 12), at(13, 4))).isEqualTo(3);
  }

  // ─── hasStudiedToday ───

  @Test
  void hasStudiedTodayLooksAtTodayOnly() {
    // 오늘 기록이 하나라도 있으면 true, 어제까지의 기록만 있으면 false 입니다.
    assertThat(StudyStreak.hasStudiedToday(List.of(at(12, 9)), NOW)).isTrue();
    assertThat(StudyStreak.hasStudiedToday(studiedEveryDay(9, 11), NOW)).isFalse();
    assertThat(StudyStreak.hasStudiedToday(List.of(), NOW)).isFalse();
  }
}
