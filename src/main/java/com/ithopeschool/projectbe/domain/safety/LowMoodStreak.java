package com.ithopeschool.projectbe.domain.safety;

import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// "기분 저하가 5일 넘게 이어졌는지" 검사기입니다. (이슈 #59, 기획서 v2 5장 「안전 브레이크」)
// 기분이 계속 가라앉은 사람에게 채찍은 해롭습니다. 이 검사가 true 면 채찍 대신 회복 모드로 가야 합니다.
//
// 정해 둔 규칙
//   1. 기분은 1(많이 처짐) ~ 5(아주 좋음) 이고, 1~2 를 "기분 저하" 로 봅니다.
//   2. "5일 넘게" = 오늘을 포함해 6일 연속입니다.
//   3. 기록이 없는 날이 하루라도 끼어 있으면 이어진 것으로 보지 않습니다. (오늘 기록이 없어도 마찬가지)
//   4. 같은 날 기록이 여러 개면 가장 낮은 것을 그날의 기분으로 봅니다.
//   5. 하루라도 기분이 3 이상이면 바로 false 로 돌아옵니다. (따로 기억해 두는 상태가 없습니다)
//
//   오늘 포함 6일 모두 기분 1~2              →  true
//   오늘 포함 5일만 1~2 (6일째는 기록 없음)   →  false
//   6일 중 하루라도 기분 3 이상              →  false
//
// FE 의 hasLowMoodStreak (src/lib/safetyBrake.ts) 과 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 두 화면의 판단이 어긋나지 않습니다.
public final class LowMoodStreak {

  // 이 값 이하면 "기분 저하" 로 봅니다. (1~5 중 1, 2)
  public static final int LOW_MOOD_MAX = 2;

  // "5일 넘게" 이어져야 하므로, 오늘을 포함해 6일 연속을 봅니다.
  public static final int STREAK_DAYS = 6;

  // 어느 날의 기분 기록 하나입니다. date: 기록한 날짜 / mood: 1(많이 처짐) ~ 5(아주 좋음)
  // (체크인 테이블이 아직 없어서, 이 검사에 필요한 두 값만 받습니다)
  public record MoodRecord(LocalDate date, int mood) {}

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private LowMoodStreak() {}

  // 지금(now)까지 기분 저하가 6일 연속 이어졌으면 true 를 돌려줍니다.
  // "오늘" 은 하루의 기준(새벽 4시, DayRule)으로 정합니다. 새벽 2시는 아직 어제입니다.
  public static boolean hasLowMoodStreak(Instant now, List<MoodRecord> records) {
    return hasLowMoodStreak(DayRule.dateOf(now), records);
  }

  // today 를 포함해 6일 연속으로 기분 저하였으면 true 를 돌려줍니다.
  // records 는 순서가 뒤섞여 있어도, 다른 날짜가 섞여 있어도 됩니다.
  public static boolean hasLowMoodStreak(LocalDate today, List<MoodRecord> records) {
    // 날짜마다 "그날 가장 낮은 기분" 하나만 남깁니다.
    // (나중에 적은 것으로 덮어쓰면, 1점을 준 날도 뒤에 적은 3점 때문에 "괜찮은 날" 이 되어 버립니다)
    Map<LocalDate, Integer> lowestMoodByDate = new HashMap<>();
    for (MoodRecord record : records) {
      Integer previous = lowestMoodByDate.get(record.date());
      if (previous == null || record.mood() < previous) {
        lowestMoodByDate.put(record.date(), record.mood());
      }
    }

    // 오늘부터 하루씩 거꾸로 6일을 봅니다. 기록이 없거나 기분이 3 이상인 날을 만나면 끝입니다.
    for (int daysAgo = 0; daysAgo < STREAK_DAYS; daysAgo++) {
      Integer mood = lowestMoodByDate.get(today.minusDays(daysAgo));
      if (mood == null || mood > LOW_MOOD_MAX) {
        return false;
      }
    }
    return true;
  }
}
