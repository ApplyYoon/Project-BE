package com.ithopeschool.projectbe.domain.routine;

import java.util.ArrayList;
import java.util.List;

// "처음 계획" 으로 첫 주 루틴 초안을 만듭니다. (이슈 #85 루틴, FE #240)
// 처음 계획 = 온보딩 마지막에 하는 약속입니다. (하루에 몇 시간, 일주일에 며칠)
// 고른 과목마다 루틴 하나를 만들어서, 첫날부터 계획이 비어 있지 않게 합니다.
//
// 정해 둔 규칙
//   1. "일주일에 며칠" 을 요일로 바꿉니다. (0 = 일요일 … 6 = 토요일, RoutineFill 과 같은 번호)
//        3 → 월·수·금 / 5 → 평일 / 6 → 월 ~ 토 / 7 → 매일 / 그 밖의 숫자는 평일
//   2. 하루 목표 시간을 과목 수만큼 10분 단위로 나눕니다. 남는 시간은 앞 과목부터 10분씩 더 줍니다.
//        180분 ÷ 3과목 → 60 · 60 · 60 / 180분 ÷ 4과목 → 50 · 50 · 40 · 40 (45분씩이 아니라 10분 단위로)
//   3. 한 과목은 최소 10분입니다. 과목이 아주 많으면 합계가 하루 목표보다 커질 수 있습니다.
//   4. 10분이 안 되는 나머지는 버립니다.
//   5. 루틴의 할 일은 "과목 이름 + 공부" 입니다. (예: "국어 공부")
//
// FE 의 getWeekdaysFor · splitMinutes · createRoutineDraft (src/lib/initialPlan.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면에서 본 초안과 서버가 만든 루틴이 어긋나지 않습니다.
public final class InitialPlan {

  // 하루 목표로 고를 수 있는 시간(시간 단위)
  public static final List<Integer> DAILY_HOUR_OPTIONS = List.of(1, 2, 3, 4, 5);

  // 일주일에 며칠 할지 고를 수 있는 값
  public static final List<Integer> DAYS_PER_WEEK_OPTIONS = List.of(3, 5, 6, 7);

  // 나누는 단위(분)이자 한 과목의 최소 시간입니다.
  private static final int UNIT_MINUTES = 10;

  // 과목 하나에서 필요한 값만 모은 것입니다. id: 과목 번호 / name: 과목 이름
  public record SubjectName(Long id, String name) {}

  // 루틴 초안 하나입니다. 과목마다 하나씩 만듭니다. (공부 루틴만 만듭니다)
  // weekdays: 반복할 요일 / subjectId: 과목 번호 / content: 할 일 / targetMinutes: 목표 시간(분)
  // 아직 수락하지 않은 초안이므로 엔티티 대신 필요한 값만 담습니다.
  public record RoutineDraft(
      List<Integer> weekdays, Long subjectId, String content, int targetMinutes) {}

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private InitialPlan() {}

  // "일주일에 며칠" 을 실제 요일로 바꿉니다. (규칙 1)
  public static List<Integer> weekdaysFor(int daysPerWeek) {
    if (daysPerWeek == 3) {
      return List.of(1, 3, 5);
    }
    if (daysPerWeek == 6) {
      return List.of(1, 2, 3, 4, 5, 6);
    }
    if (daysPerWeek == 7) {
      return List.of(0, 1, 2, 3, 4, 5, 6);
    }
    return List.of(1, 2, 3, 4, 5);
  }

  // 하루 목표 시간(분)을 과목 수만큼 나눕니다. 과목이 없으면 빈 목록입니다. (규칙 2 ~ 4)
  //
  //   (180, 3) → [60, 60, 60]   (180, 4) → [50, 50, 40, 40]   (120, 3) → [40, 40, 40]
  //   (60, 1)  → [60]           (180, 0) → []
  public static List<Integer> splitMinutes(int dailyMinutes, int count) {
    if (count <= 0) {
      return List.of();
    }

    // 10분짜리 조각이 몇 개인지 세고, 과목마다 똑같이 나눠 준 뒤 남는 조각을 앞에서부터 하나씩 더 줍니다.
    int totalUnits = dailyMinutes / UNIT_MINUTES;
    int baseUnits = Math.max(1, totalUnits / count);
    int extraUnits = Math.max(0, totalUnits - baseUnits * count);

    List<Integer> result = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      result.add((baseUnits + (index < extraUnits ? 1 : 0)) * UNIT_MINUTES);
    }
    return result;
  }

  // 고른 과목과 시간으로 첫 주 루틴 초안을 만듭니다. 과목마다 루틴 하나입니다. (규칙 5)
  //
  // subjects: 고른 과목들 (보여줄 순서대로)
  // dailyMinutes: 하루 목표 시간(분) / daysPerWeek: 일주일에 며칠 (3 · 5 · 6 · 7)
  //
  //   ([국어, 수학, 영어], 180, 5)
  //     → [평일 · 국어 · "국어 공부" · 60분, 평일 · 수학 · "수학 공부" · 60분, 평일 · 영어 · "영어 공부" · 60분]
  public static List<RoutineDraft> routineDraftsOf(
      List<SubjectName> subjects, int dailyMinutes, int daysPerWeek) {
    List<Integer> weekdays = weekdaysFor(daysPerWeek);
    List<Integer> minutesList = splitMinutes(dailyMinutes, subjects.size());

    List<RoutineDraft> drafts = new ArrayList<>();
    for (int index = 0; index < subjects.size(); index++) {
      SubjectName subject = subjects.get(index);
      drafts.add(
          new RoutineDraft(weekdays, subject.id(), subject.name() + " 공부", minutesList.get(index)));
    }
    return drafts;
  }
}
