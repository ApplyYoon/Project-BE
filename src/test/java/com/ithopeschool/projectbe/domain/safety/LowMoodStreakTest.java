package com.ithopeschool.projectbe.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.safety.LowMoodStreak.MoodRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// "기분 저하가 5일 넘게(= 오늘 포함 6일 연속) 이어졌는지" 규칙(이슈 #59)을 확인합니다.
// feExample 로 시작하는 3개는 FE 함수(hasLowMoodStreak) 주석의 예시를 그대로 옮긴 것입니다.
class LowMoodStreakTest {

  // 테스트에서 쓰는 "오늘": 2026년 10월 10일
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

  // 오늘부터 거꾸로, 넘겨받은 기분 점수를 하루에 하나씩 기록으로 만듭니다.
  // 예: moods(2, 1, 2) → 오늘 2점, 어제 1점, 그저께 2점
  private static List<MoodRecord> moods(int... moodsFromToday) {
    List<MoodRecord> records = new ArrayList<>();
    for (int daysAgo = 0; daysAgo < moodsFromToday.length; daysAgo++) {
      records.add(new MoodRecord(TODAY.minusDays(daysAgo), moodsFromToday[daysAgo]));
    }
    return records;
  }

  @Test
  void feExample1SixLowDaysInARow() {
    // FE 예1) 오늘 포함 6일 모두 기분 1~2 → true
    assertThat(LowMoodStreak.hasLowMoodStreak(TODAY, moods(2, 1, 2, 2, 1, 2))).isTrue();
  }

  @Test
  void feExample2OnlyFiveLowDays() {
    // FE 예2) 오늘 포함 5일만 기분 1~2 (6일째는 기록 없음) → false
    assertThat(LowMoodStreak.hasLowMoodStreak(TODAY, moods(2, 1, 2, 2, 1))).isFalse();
  }

  @Test
  void feExample3OneGoodDayBreaksTheStreak() {
    // FE 예3) 6일 중 하루라도 기분 3 이상 → false
    assertThat(LowMoodStreak.hasLowMoodStreak(TODAY, moods(2, 1, 3, 2, 1, 2))).isFalse();
  }

  @Test
  void moodTwoIsLowButThreeIsNot() {
    // 경계값: 2 는 기분 저하, 3 은 아닙니다.
    assertThat(LowMoodStreak.hasLowMoodStreak(TODAY, moods(2, 2, 2, 2, 2, 2))).isTrue();
    assertThat(LowMoodStreak.hasLowMoodStreak(TODAY, moods(3, 2, 2, 2, 2, 2))).isFalse();
  }

  @Test
  void noRecordsMeansNoStreak() {
    // 기록이 하나도 없으면 false
    assertThat(LowMoodStreak.hasLowMoodStreak(TODAY, List.of())).isFalse();
  }

  @Test
  void missingTodayBreaksTheStreak() {
    // 어제까지 6일 연속 낮았어도, 오늘 기록이 아직 없으면 false 입니다. (오늘을 포함해서 세기 때문)
    List<MoodRecord> untilYesterday = new ArrayList<>();
    for (int daysAgo = 1; daysAgo <= 6; daysAgo++) {
      untilYesterday.add(new MoodRecord(TODAY.minusDays(daysAgo), 1));
    }
    assertThat(LowMoodStreak.hasLowMoodStreak(TODAY, untilYesterday)).isFalse();
  }

  @Test
  void missingDayInTheMiddleBreaksTheStreak() {
    // 가운데 하루(3일 전)만 기록이 없어도 이어진 것으로 보지 않습니다.
    List<MoodRecord> records = new ArrayList<>(moods(1, 1, 1, 1, 1, 1, 1));
    records.removeIf(record -> record.date().equals(TODAY.minusDays(3)));
    assertThat(LowMoodStreak.hasLowMoodStreak(TODAY, records)).isFalse();
  }

  @Test
  void lowestMoodOfTheDayCounts() {
    // 같은 날 기록이 여러 개면 가장 낮은 것을 봅니다. 오늘 1점과 4점을 둘 다 적었으면 오늘은 1점입니다.
    List<MoodRecord> records = new ArrayList<>(moods(4, 2, 2, 2, 2, 2));
    records.add(new MoodRecord(TODAY, 1));
    assertThat(LowMoodStreak.hasLowMoodStreak(TODAY, records)).isTrue();

    // 적은 순서가 반대여도(1점을 먼저, 4점을 나중에) 결과는 같습니다.
    List<MoodRecord> reversed = new ArrayList<>(moods(1, 2, 2, 2, 2, 2));
    reversed.add(new MoodRecord(TODAY, 4));
    assertThat(LowMoodStreak.hasLowMoodStreak(TODAY, reversed)).isTrue();
  }

  @Test
  void olderDaysDoNotMatter() {
    // 7일 전보다 오래된 날은 보지 않습니다. 6일 연속 낮았으면, 그 전에 좋았던 날이 있어도 true 입니다.
    assertThat(LowMoodStreak.hasLowMoodStreak(TODAY, moods(2, 2, 2, 2, 2, 2, 5, 5))).isTrue();
  }

  @Test
  void goesBackToFalseAsSoonAsMoodRecovers() {
    // 어제까지 6일 연속 낮았더라도, 오늘 3점을 적으면 바로 false 로 돌아옵니다.
    assertThat(LowMoodStreak.hasLowMoodStreak(TODAY, moods(3, 1, 1, 1, 1, 1, 1))).isFalse();
  }

  @Test
  void countsDaysAcrossMonths() {
    // 달이 바뀌어도 날짜를 맞게 거슬러 올라갑니다. 10월 3일부터 9월 28일까지 6일.
    LocalDate today = LocalDate.of(2026, 10, 3);
    List<MoodRecord> records = new ArrayList<>();
    for (int daysAgo = 0; daysAgo < 6; daysAgo++) {
      records.add(new MoodRecord(today.minusDays(daysAgo), 2));
    }
    assertThat(LowMoodStreak.hasLowMoodStreak(today, records)).isTrue();
  }

  @Test
  void todayFollowsTheFourAmDayRule() {
    // "오늘" 은 새벽 4시에 바뀝니다. (DayRule) 10월 10일까지 6일 연속 낮은 기록이 있을 때:
    ZoneId seoul = ZoneId.of("Asia/Seoul");
    Instant beforeFour = ZonedDateTime.of(2026, 10, 11, 3, 59, 0, 0, seoul).toInstant();
    Instant atFour = ZonedDateTime.of(2026, 10, 11, 4, 0, 0, 0, seoul).toInstant();
    List<MoodRecord> records = moods(1, 1, 1, 1, 1, 1);

    // 10월 11일 03:59 는 아직 10월 10일 → 6일 연속 그대로 → true
    assertThat(LowMoodStreak.hasLowMoodStreak(beforeFour, records)).isTrue();
    // 10월 11일 04:00 부터는 10월 11일 → 오늘 기록이 아직 없음 → false
    assertThat(LowMoodStreak.hasLowMoodStreak(atFour, records)).isFalse();
  }
}
