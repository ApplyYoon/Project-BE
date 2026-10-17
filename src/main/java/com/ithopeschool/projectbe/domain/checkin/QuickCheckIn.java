package com.ithopeschool.projectbe.domain.checkin;

import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// 하루 한 번 하는 "하루 첫 태그" 를 만듭니다. (이슈 #220, 기획서 4.2 「홈」. 처음은 이슈 #24 의 기분 1탭)
// 그날 처음 들어오면 컨디션(1 ~ 5)과 감정 태그(여러 개)를 한 줄로 묻고, 나머지 칸은 이미 있는 숫자로 채웁니다.
//
// 규칙
//   1. 날짜는 하루의 기준(새벽 4시)으로 정합니다. 새벽 1시에 누르면 전날의 체크인입니다. (DayRule)
//   2. 컨디션과 기분은 다른 값입니다. 컨디션만 받고, 기분(mood)에 복사하지 않습니다. (이슈 #220)
//      (예전 1탭 체크인은 기분 하나만 받아 컨디션에 복사했습니다)
//   3. 오늘 예상 순공시간은 그날 공부 계획의 목표 시간을 모두 더한 값입니다. 노는 계획은 뺍니다.
//   4. 그날 공부 계획이 없으면(더한 값이 0 이면) 처음 계획에서 정한 하루 목표를 씁니다. 그것도 없으면 0 입니다.
//   5. 감정 태그는 안 골라도 됩니다. (빈 목록) 태그를 건너뛰면 그날 통화가 묻습니다.
//
// 예시 — 계획: 10/09 [수학 45분], 10/10 [수학 60분, 영어 30분, 놀기 20분] / 처음 계획의 하루 목표 120분
//   10/10 09:00 에 컨디션 4 · [막막함]  →  10/10, 컨디션 4, [막막함], 예상 90분
//   10/10 01:00 에 컨디션 2            →  10/09, 컨디션 2, [], 예상 45분   (새벽 4시 전이라 전날)
//   10/11 09:00 에 컨디션 3            →  10/11, 컨디션 3, [], 예상 120분  (계획이 없어서 하루 목표)
//
// 이 함수는 체크인에 들어갈 값만 만듭니다.
// 저장하는 일과, 1탭으로 한 체크인이라고 표시(source)하는 일은 부르는 쪽이 합니다.
//
// FE 의 getPlannedMinutes · makeQuickCheckIn (src/lib/quickCheckIn.ts) 과 같은 규칙입니다.
// ⚠️ FE 는 아직 기분을 컨디션에 복사합니다. FE #387(하루 첫 진입 컨디션·감정 태그)에서 규칙 2 로 같이 바뀝니다.
//
// date: 체크인한 날
// condition: 오늘 컨디션 (1 ~ 5)
// emotions: 고른 감정 태그들. 안 골랐으면 빈 목록
// expectedMinutes: 오늘 예상 순공시간(분)
public record QuickCheckIn(
    LocalDate date, int condition, Set<EmotionTag> emotions, int expectedMinutes) {

  // 받은 감정 목록을 바깥에서 바꿔도 이 값이 바뀌지 않게 복사해 둡니다.
  public QuickCheckIn {
    emotions = emotions.isEmpty() ? EnumSet.noneOf(EmotionTag.class) : EnumSet.copyOf(emotions);
  }

  // 계획 하나에서 이 계산에 쓰는 값만 모은 것입니다.
  // date: 계획한 날짜 / targetMinutes: 목표 시간(분)
  // kind: 공부 계획인지 노는 계획인지. 비어 있으면(null) 공부 계획으로 봅니다
  public record PlanItem(LocalDate date, PlanKind kind, int targetMinutes) {}

  // 오늘 예상 순공시간(분)을 정합니다. 따로 묻지 않고 이미 있는 숫자를 씁니다. (규칙 3, 4)
  //
  // plans: 그날의 계획들. 노는 계획이 섞여 있어도 됩니다
  // fallbackMinutes: 그날 공부 계획이 없을 때 쓸 값. 처음 계획에서 정한 하루 목표(분)이고, 없으면 0
  //
  //   [수학 60분, 영어 30분, 놀기 20분], 120  →  90
  //   [], 120                               →  120
  //   [놀기 20분], 120                       →  120  (공부 계획이 없는 날)
  public static int plannedMinutes(List<PlanItem> plans, int fallbackMinutes) {
    int total = 0;
    for (PlanItem plan : plans) {
      if (plan.kind() != PlanKind.PLAY) {
        total += plan.targetMinutes();
      }
    }
    return total > 0 ? total : fallbackMinutes;
  }

  // 지금(now) 고른 컨디션 · 감정 태그로 오늘의 체크인을 만듭니다. 규칙 1 ~ 5 를 한 번에 적용합니다.
  //
  // now: 고른 순간
  // condition: 고른 컨디션 (1 ~ 5)
  // emotions: 고른 감정 태그들. 안 골랐으면 빈 목록
  // plans: 여러 날짜가 섞여 있어도 됩니다. 이 함수가 오늘 것만 골라서 계산합니다
  // fallbackMinutes: 처음 계획에서 정한 하루 목표(분). 처음 계획이 없으면 0
  public static QuickCheckIn of(
      Instant now,
      int condition,
      Set<EmotionTag> emotions,
      List<PlanItem> plans,
      int fallbackMinutes) {
    // 새벽 4시 전이면 전날입니다. (규칙 1)
    LocalDate today = DayRule.dateOf(now);

    List<PlanItem> todayPlans = new ArrayList<>();
    for (PlanItem plan : plans) {
      if (plan.date().equals(today)) {
        todayPlans.add(plan);
      }
    }

    return new QuickCheckIn(
        today, condition, emotions, plannedMinutes(todayPlans, fallbackMinutes));
  }
}
