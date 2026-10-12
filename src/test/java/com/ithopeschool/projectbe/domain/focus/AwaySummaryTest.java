package com.ithopeschool.projectbe.domain.focus;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.focus.AwaySummary.AwayHour;
import com.ithopeschool.projectbe.domain.focus.AwaySummary.AwayRecord;
import com.ithopeschool.projectbe.domain.focus.AwaySummary.UnlockRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

// 공부 중에 나간 기록과 집중 모드 해제 기록의 요약 규칙(이슈 #67)을 확인합니다.
// feExample 로 시작하는 2개는 FE 함수(getAwaySummary) 주석의 예시를 그대로 옮긴 것입니다.
// 기간은 FE 예시와 같은 2026-10-05(월) ~ 2026-10-11(일) 입니다.
class AwaySummaryTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
  private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 11);

  private static final long MINUTE = 60_000;

  // 한국 시간 10월 "day일 시:분" 을 순간(Instant)으로 바꾸는 도우미입니다.
  private static Instant at(int day, int hour, int minute) {
    return ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, SEOUL).toInstant();
  }

  // 10월 day일 시:분에 나가서 minutes 분 있다가 온 기록을 만듭니다.
  private static AwayRecord away(int day, int hour, int minute, int minutes) {
    return new AwayRecord(at(day, hour, minute), minutes * MINUTE);
  }

  // 사용자가 직접 푼 해제 기록을 만듭니다.
  private static UnlockRecord unlock(int day, int hour, int minute, String reason) {
    return new UnlockRecord(at(day, hour, minute), reason, false);
  }

  // 설정·안전 브레이크 때문에 자동으로 풀린 해제 기록을 만듭니다.
  private static UnlockRecord autoUnlock(int day, int hour, int minute) {
    return new UnlockRecord(at(day, hour, minute), "지금은 쉬어 가는 게 좋아서 집중 모드를 풀었어요.", true);
  }

  private static AwaySummary summary(List<AwayRecord> aways, List<UnlockRecord> unlocks) {
    return AwaySummary.of(aways, unlocks, MONDAY, SUNDAY);
  }

  // ─── FE 예시 ───

  @Test
  void feExample1WeekWithAwaysAndUnlocks() {
    // FE 예) 딴짓: 10/06 21:10 에 3분, 10/06 21:40 에 2분, 10/08 19:05 에 10분, (10/12 것은 기간 밖)
    //        해제: 10/06 21:45 직접 풂("급한 연락"), 10/09 자동으로 풀림
    //        → { awayCount: 3, awayMinutes: 15, peakHour: { hour: 21, count: 2 }, unlockCount: 1,
    // recentUnlocks: [10/06 것] }
    List<AwayRecord> aways =
        List.of(away(6, 21, 10, 3), away(6, 21, 40, 2), away(8, 19, 5, 10), away(12, 20, 0, 30));
    UnlockRecord manual = unlock(6, 21, 45, "급한 연락");
    List<UnlockRecord> unlocks = List.of(manual, autoUnlock(9, 20, 0));

    assertThat(summary(aways, unlocks))
        .isEqualTo(new AwaySummary(3, 15, new AwayHour(21, 2), 1, List.of(manual)));
  }

  @Test
  void feExample2NoRecords() {
    // FE 예) 기록이 하나도 없음 → { awayCount: 0, awayMinutes: 0, peakHour: null, unlockCount: 0,
    // recentUnlocks: [] }
    assertThat(summary(List.of(), List.of())).isEqualTo(new AwaySummary(0, 0, null, 0, List.of()));
  }

  // ─── 딴짓 ───

  @Test
  void periodIncludesBothEnds() {
    // 시작 날짜(10/05)와 끝 날짜(10/11)의 기록은 세고, 하루 앞(10/04)·하루 뒤(10/12)는 세지 않습니다.
    List<AwayRecord> aways =
        List.of(away(4, 20, 0, 100), away(5, 20, 0, 1), away(11, 20, 0, 2), away(12, 20, 0, 100));

    AwaySummary summary = summary(aways, List.of());

    assertThat(summary.awayCount()).isEqualTo(2);
    assertThat(summary.awayMinutes()).isEqualTo(3);
  }

  @Test
  void awayDateFollowsTheFourAmDayRule() {
    // 나간 날짜는 새벽 4시 기준입니다. 10/12(월) 새벽 1시에 나간 것은 아직 10/11(일) → 기간 안입니다.
    // 10/05(월) 새벽 1시에 나간 것은 아직 10/04(일) → 기간 밖입니다.
    AwaySummary summary = summary(List.of(away(12, 1, 0, 5), away(5, 1, 0, 50)), List.of());

    assertThat(summary.awayCount()).isEqualTo(1);
    assertThat(summary.awayMinutes()).isEqualTo(5);
    // 시간대는 시계 시각 그대로 새벽 1시입니다.
    assertThat(summary.peakHour()).isEqualTo(new AwayHour(1, 1));
  }

  @Test
  void peakHourIsTheHourWithMostAways() {
    // 가장 자주 나간 시간대: 19시에 1번, 21시에 3번, 23시에 2번 → 21시(3번)
    List<AwayRecord> aways =
        List.of(
            away(6, 19, 5, 1),
            away(6, 21, 0, 1),
            away(7, 21, 30, 1),
            away(8, 21, 59, 1),
            away(8, 23, 0, 1),
            away(9, 23, 10, 1));

    assertThat(summary(aways, List.of()).peakHour()).isEqualTo(new AwayHour(21, 3));
  }

  @Test
  void earlierHourWinsWhenCountsAreTheSame() {
    // 횟수가 같으면 더 이른 시간대를 고릅니다. 기록의 순서와는 상관없습니다. (22시 2번, 16시 2번 → 16시)
    List<AwayRecord> aways =
        List.of(away(6, 22, 0, 1), away(7, 22, 30, 1), away(6, 16, 0, 1), away(7, 16, 30, 1));

    assertThat(summary(aways, List.of()).peakHour()).isEqualTo(new AwayHour(16, 2));
  }

  @Test
  void awayMinutesAreRoundedOnlyOnce() {
    // 나가 있던 시간은 모두 더한 뒤 마지막에 한 번만 반올림합니다. 40초 세 번 = 2분 (기록마다 반올림하면 3분이 됩니다)
    List<AwayRecord> aways =
        List.of(
            new AwayRecord(at(6, 20, 0), 40_000),
            new AwayRecord(at(6, 20, 10), 40_000),
            new AwayRecord(at(6, 20, 20), 40_000));

    assertThat(summary(aways, List.of()).awayMinutes()).isEqualTo(2);
  }

  // ─── 해제 ───

  @Test
  void autoUnlocksAreNotCounted() {
    // 설정이나 안전 브레이크 때문에 저절로 풀린 것은 세지 않습니다. 사용자가 직접 푼 것만 셉니다.
    List<UnlockRecord> unlocks =
        List.of(autoUnlock(6, 20, 0), autoUnlock(7, 20, 0), unlock(8, 20, 0, "쉬는 시간"));

    AwaySummary summary = summary(List.of(), unlocks);

    assertThat(summary.unlockCount()).isEqualTo(1);
    assertThat(summary.recentUnlocks()).extracting(UnlockRecord::reason).containsExactly("쉬는 시간");
  }

  @Test
  void recentUnlocksAreTheLatestThree() {
    // 최근 해제는 최근 것부터 최대 3개입니다. 횟수는 5번 모두 셉니다.
    List<UnlockRecord> unlocks =
        List.of(
            unlock(5, 20, 0, "첫째"),
            unlock(9, 20, 0, "넷째"),
            unlock(7, 20, 0, "셋째"),
            unlock(10, 20, 0, "다섯째"),
            unlock(6, 20, 0, "둘째"));

    AwaySummary summary = summary(List.of(), unlocks);

    assertThat(summary.unlockCount()).isEqualTo(5);
    assertThat(summary.recentUnlocks())
        .extracting(UnlockRecord::reason)
        .containsExactly("다섯째", "넷째", "셋째");
  }

  @Test
  void unlocksOutsideThePeriodAreNotCounted() {
    // 해제는 "푼 시각" 의 날짜(새벽 4시 기준)로 기간에 넣습니다.
    List<UnlockRecord> unlocks =
        List.of(unlock(4, 23, 0, "지난주"), unlock(12, 2, 0, "일요일 밤"), unlock(12, 9, 0, "다음 주"));

    AwaySummary summary = summary(List.of(), unlocks);

    // 10/12 새벽 2시는 아직 10/11(일)이라 기간 안이고, 나머지 둘은 기간 밖입니다.
    assertThat(summary.unlockCount()).isEqualTo(1);
    assertThat(summary.recentUnlocks()).extracting(UnlockRecord::reason).containsExactly("일요일 밤");
  }

  @Test
  void awaysAndUnlocksAreCountedSeparately() {
    // 딴짓과 해제는 따로 셉니다. 해제만 있는 주에는 나간 횟수가 0 이고 시간대도 없습니다.
    AwaySummary summary = summary(List.of(), List.of(unlock(6, 21, 0, "급한 연락")));

    assertThat(summary.awayCount()).isEqualTo(0);
    assertThat(summary.peakHour()).isNull();
    assertThat(summary.unlockCount()).isEqualTo(1);
  }
}
