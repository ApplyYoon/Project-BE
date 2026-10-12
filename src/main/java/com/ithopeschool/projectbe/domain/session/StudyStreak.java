package com.ithopeschool.projectbe.domain.session;

import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// "오늘까지 며칠 연속으로 공부했는지" 를 셉니다.
//
//   - 오늘 공부했으면 오늘부터 하루씩 거꾸로 셉니다.
//   - 오늘 아직 안 했으면 어제부터 셉니다. (오늘은 아직 기회가 남아 있으니까 끊긴 것이 아닙니다)
//   - 하루라도 빠진 날을 만나면 거기서 멈춥니다. 그래서 어제도 안 했으면 0 입니다.
//   - 하루에 여러 번 공부해도 한 번으로 칩니다.
//
//   기록 0개                         →  0
//   오늘만 공부                      →  1
//   어제까지 3일 연속, 오늘은 아직    →  3
//
// 날짜는 하루의 기준(새벽 4시, DayRule)으로 셉니다. 새벽 2시에 한 공부는 전날 공부입니다.
// 기록은 "시작한 날" 의 것으로 셉니다. (하루 요약 DaySummary 와 같은 기준이라 서로 맞습니다)
//
// FE 의 getStudyStreak · hasStudiedToday (src/lib/streak.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 연속 일수가 어긋나지 않습니다.
public final class StudyStreak {

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private StudyStreak() {}

  // 지금(now)까지 며칠 연속으로 공부했는지 돌려줍니다.
  //
  // startedAts: 공부 기록들의 시작 시각. 순서는 상관없고, 같은 날이 여러 번 있어도 됩니다.
  //   노는 기록은 공부한 날이 아니라서, 부르는 쪽에서 빼고 넘깁니다. (FE 는 함수 안에서 걸러냅니다)
  // now: 지금 시각
  public static int streakOf(List<Instant> startedAts, Instant now) {
    Set<LocalDate> studiedDays = studiedDaysOf(startedAts);

    // 오늘 아직 안 했으면 어제부터 셉니다.
    LocalDate day = DayRule.dateOf(now);
    if (!studiedDays.contains(day)) {
      day = day.minusDays(1);
    }

    int streak = 0;
    while (studiedDays.contains(day)) {
      streak++;
      day = day.minusDays(1);
    }
    return streak;
  }

  // 오늘 공부 기록이 하나라도 있으면 true 를 돌려줍니다.
  // (이벤트 메시지를 고를 때 "오늘 공부를 한 번이라도 시작했는지" 로 씁니다 — EventMessage.Input.hasStudiedToday)
  public static boolean hasStudiedToday(List<Instant> startedAts, Instant now) {
    return studiedDaysOf(startedAts).contains(DayRule.dateOf(now));
  }

  // 공부한 날짜만 모읍니다. 하루에 여러 번 공부해도 한 번으로 칩니다. (Set 은 같은 값을 한 번만 담습니다)
  private static Set<LocalDate> studiedDaysOf(List<Instant> startedAts) {
    Set<LocalDate> studiedDays = new HashSet<>();
    for (Instant startedAt : startedAts) {
      studiedDays.add(DayRule.dateOf(startedAt));
    }
    return studiedDays;
  }
}
