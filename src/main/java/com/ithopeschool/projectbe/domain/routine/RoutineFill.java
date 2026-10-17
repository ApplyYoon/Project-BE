package com.ithopeschool.projectbe.domain.routine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// "그 날짜에 어느 루틴을 계획으로 채워야 하는지" 를 정합니다. (이슈 #85)
// 루틴은 요일마다 반복되는 계획의 "틀" 이고, 날마다의 계획은 따로 만들어집니다.
// 그날 계획을 고치거나 지워도 루틴은 바뀌지 않고, 루틴을 지워도 이미 만들어진 계획은 남습니다.
//
// 규칙
//   1. 그 날짜의 요일이 루틴의 요일 목록에 있어야 합니다.
//   2. 이미 그 날짜에 넣은 적이 있는 루틴은 다시 채우지 않습니다.
//      그래서 한 날짜에 한 번만 생기고, 그날 지운 루틴 계획이 되살아나지 않습니다.
//   3. 지난 날짜에는 아무것도 채우지 않습니다. (오늘과 앞으로의 날짜만)
//
// 예시 (2026-10-12 는 월요일, 오늘은 2026-10-10)
//   루틴 A(월·수·금 수학 60분), 루틴 B(화·목 영어 30분)
//   10월 12일, 아직 아무것도 안 넣음   →  [A]
//   10월 12일, A 는 이미 넣었음        →  []
//   10월 5일 (지난 날짜)              →  []
//
// 이 함수는 "채울 루틴의 번호" 만 알려 줍니다. 계획을 만들어 저장하고, 그 날짜에 넣었다고 적어 두는 일은 부르는 쪽이 합니다.
//
// FE 의 getRoutinePlansToAdd (src/lib/routines.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 서버 연결 전후로 채워지는 계획이 달라지지 않습니다.
public final class RoutineFill {

  // 루틴 하나에서 이 계산에 쓰는 값만 모은 것입니다.
  // id: 루틴 번호 / weekdays: 반복하는 요일들. 0 = 일요일, 1 = 월요일 … 6 = 토요일
  // 저장 여부와 분리해서 계산하도록 루틴 엔티티에서 필요한 두 값만 받습니다.
  public record RoutineDays(Long id, List<Integer> weekdays) {}

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private RoutineFill() {}

  // 날짜가 무슨 요일인지 숫자로 돌려줍니다. (0 = 일요일 … 6 = 토요일)
  // 자바의 요일은 월요일 1 ~ 일요일 7 이라서, 7 로 나눈 나머지로 바꿉니다. (일요일 7 → 0)
  public static int weekdayOf(LocalDate date) {
    return date.getDayOfWeek().getValue() % 7;
  }

  // 그 날짜에 새로 채워야 할 루틴의 번호들을 돌려줍니다. 순서는 받은 루틴 목록의 순서 그대로입니다.
  //
  // routines: 내 루틴 전체
  // filledRoutineIds: 그 날짜에 이미 계획으로 넣은 적이 있는 루틴의 번호들
  //   넣은 뒤에 그 계획을 지웠더라도 여기에 남아 있어야 합니다. 그래야 지운 계획이 되살아나지 않습니다.
  // date: 채우려는 날짜
  // today: 오늘 날짜 (하루의 기준인 새벽 4시로 정한 날짜, DayRule)
  public static List<Long> routineIdsToFill(
      List<RoutineDays> routines,
      Collection<Long> filledRoutineIds,
      LocalDate date,
      LocalDate today) {
    // 규칙 3: 지난 날짜에는 채우지 않습니다.
    if (date.isBefore(today)) {
      return List.of();
    }

    int weekday = weekdayOf(date);
    List<Long> routineIds = new ArrayList<>();
    for (RoutineDays routine : routines) {
      // 규칙 1: 그 요일의 루틴이어야 합니다.
      if (!routine.weekdays().contains(weekday)) {
        continue;
      }
      // 규칙 2: 이미 넣은 루틴은 건너뜁니다.
      if (filledRoutineIds.contains(routine.id())) {
        continue;
      }
      routineIds.add(routine.id());
    }
    return routineIds;
  }
}
