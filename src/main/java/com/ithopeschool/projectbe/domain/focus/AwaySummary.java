package com.ithopeschool.projectbe.domain.focus;

import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 공부 중에 나간 기록(딴짓)과 집중 모드 해제 기록을 기간으로 묶어 셉니다. (이슈 #67 의 "시간대별로 모아 본다")
// 벌을 주려는 숫자가 아니라, 어느 시간대에 약한지 스스로 알아보는 재료입니다.
//
// 규칙
//   1. 딴짓은 "나간 날짜"(하루의 기준은 새벽 4시, DayRule)로 기간에 넣습니다.
//   2. 해제는 "푼 시각" 의 날짜로 기간에 넣고, 사용자가 직접 푼 것만 셉니다.
//      (설정이나 안전 브레이크 때문에 저절로 풀린 것은 세지 않습니다)
//   3. 가장 자주 나간 시간대는 나간 시각의 "시" 로 셉니다. 횟수가 같으면 더 이른 시간대를 고릅니다.
//
// 예시 — 10/05(월) ~ 10/11(일)
//   딴짓: 10/06 21:10 에 3분, 10/06 21:40 에 2분, 10/08 19:05 에 10분, (10/12 것은 기간 밖)
//   해제: 10/06 21:45 직접 풂("급한 연락"), 10/09 자동으로 풀림
//     →  나간 횟수 3, 나가 있던 시간 15분, 가장 자주 나간 시간대 21시(2번), 직접 푼 횟수 1, 최근 해제 [10/06 것]
//   기록이 하나도 없음
//     →  나간 횟수 0, 0분, 시간대 없음(null), 직접 푼 횟수 0, 최근 해제 없음
//
// FE 의 getAwaySummary (src/lib/awaySummary.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 숫자가 어긋나지 않습니다.
//
// awayCount: 공부 중에 나갔다 온 횟수
// awayMinutes: 나가 있던 시간을 모두 더한 것(분, 반올림)
// peakHour: 가장 자주 나간 시간대. 나간 적이 없으면 null
// unlockCount: 집중 모드를 직접 푼 횟수
// recentUnlocks: 직접 푼 기록 중 최근 것부터 최대 3개
public record AwaySummary(
    int awayCount,
    int awayMinutes,
    AwayHour peakHour,
    int unlockCount,
    List<UnlockRecord> recentUnlocks) {

  // "최근 해제" 로 보여주는 개수
  public static final int RECENT_UNLOCK_COUNT = 3;

  private static final long MILLIS_PER_MINUTE = 60_000;

  private static final int HOURS_PER_DAY = 24;

  // 배포 서버는 보통 UTC 로 돌아가서, 서버 컴퓨터의 시간대에 기대지 않고 한국 시간을 직접 지정합니다.
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  // 한 시간대에 나간 횟수입니다. 예) hour 21, count 3 = 밤 9시대에 3번
  public record AwayHour(int hour, int count) {}

  // 공부 중에 나갔다 온 기록 하나에서 계산에 쓰는 값만 모은 것입니다.
  // leftAt: 나간 시각 / awayMillis: 나가 있던 시간(밀리초)
  public record AwayRecord(Instant leftAt, long awayMillis) {}

  // 집중 모드를 해제한 기록 하나에서 계산에 쓰는 값만 모은 것입니다.
  // unlockedAt: 해제한 시각 / reason: 해제 이유 (사용자가 적은 한 줄, 또는 자동 해제 안내 문구)
  // isAuto: 사용자가 직접 푼 것이 아니라 설정·안전 브레이크 때문에 자동으로 풀렸으면 true
  // (해제 기록 테이블은 아직 없고 설계는 학생이 합니다. 그래서 엔티티가 아니라 필요한 값만 받습니다)
  public record UnlockRecord(Instant unlockedAt, String reason, boolean isAuto) {}

  // startDate ~ endDate (둘 다 포함) 기간의 집중 기록을 요약합니다.
  // awayRecords, unlockRecords: 다른 날짜가 섞여 있어도 됩니다. 이 함수가 기간에 해당하는 것만 골라서 셉니다.
  public static AwaySummary of(
      List<AwayRecord> awayRecords,
      List<UnlockRecord> unlockRecords,
      LocalDate startDate,
      LocalDate endDate) {
    // ─── 딴짓 (규칙 1, 3) ───
    int awayCount = 0;
    long awayMillis = 0;
    // 시간대(0~23)마다 나간 횟수를 셉니다. 자리 번호가 곧 "시" 입니다.
    int[] countByHour = new int[HOURS_PER_DAY];
    for (AwayRecord record : awayRecords) {
      if (!isInRange(DayRule.dateOf(record.leftAt()), startDate, endDate)) {
        continue;
      }
      awayCount++;
      awayMillis += record.awayMillis();
      countByHour[record.leftAt().atZone(SEOUL).getHour()]++;
    }

    // 횟수가 가장 많은 시간대를 찾습니다. 이른 시간대부터 보고 "더 많을 때만" 바꾸니, 같으면 더 이른 시간대가 남습니다.
    AwayHour peakHour = null;
    for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
      int count = countByHour[hour];
      if (count > 0 && (peakHour == null || count > peakHour.count())) {
        peakHour = new AwayHour(hour, count);
      }
    }

    // ─── 해제 (규칙 2) ───
    List<UnlockRecord> unlocks = new ArrayList<>();
    for (UnlockRecord record : unlockRecords) {
      if (!record.isAuto() && isInRange(DayRule.dateOf(record.unlockedAt()), startDate, endDate)) {
        unlocks.add(record);
      }
    }
    // 최근에 푼 것이 앞에 오게 줄 세웁니다.
    unlocks.sort(Comparator.comparing(UnlockRecord::unlockedAt).reversed());
    List<UnlockRecord> recentUnlocks =
        List.copyOf(unlocks.subList(0, Math.min(RECENT_UNLOCK_COUNT, unlocks.size())));

    return new AwaySummary(
        awayCount,
        (int) Math.round((double) awayMillis / MILLIS_PER_MINUTE),
        peakHour,
        unlocks.size(),
        recentUnlocks);
  }

  // 날짜가 기간 안(시작과 끝 포함)인지 봅니다.
  private static boolean isInRange(LocalDate date, LocalDate startDate, LocalDate endDate) {
    return !date.isBefore(startDate) && !date.isAfter(endDate);
  }
}
