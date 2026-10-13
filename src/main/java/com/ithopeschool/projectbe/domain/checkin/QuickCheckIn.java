package com.ithopeschool.projectbe.domain.checkin;

import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// 하루 한 번 하는 "1탭 체크인" 을 만듭니다. (이슈 #24 의 "바뀐 점": 매일 체크인의 기본이 기분 1탭)
// 그날 앱을 처음 열면 기분 하나만 묻고, 나머지 칸은 묻지 않고 이미 있는 숫자로 채웁니다.
//
// 규칙
//   1. 날짜는 하루의 기준(새벽 4시)으로 정합니다. 새벽 1시에 누르면 전날의 체크인입니다. (DayRule)
//   2. 컨디션은 따로 묻지 않아서 기분과 같은 값으로 채웁니다.
//      (컨디션이 비어 있으면 관리 강도·안전 브레이크 계산이 돌아가지 않기 때문입니다)
//   3. 오늘 예상 순공시간은 그날 공부 계획의 목표 시간을 모두 더한 값입니다. 노는 계획은 뺍니다.
//   4. 그날 공부 계획이 없으면(더한 값이 0 이면) 처음 계획에서 정한 하루 목표를 씁니다. 그것도 없으면 0 입니다.
//
// 예시 — 계획: 10/09 [수학 45분], 10/10 [수학 60분, 영어 30분, 놀기 20분] / 처음 계획의 하루 목표 120분
//   10/10 09:00 에 기분 4  →  10/10, 컨디션 4, 기분 4, 예상 90분
//   10/10 01:00 에 기분 2  →  10/09, 컨디션 2, 기분 2, 예상 45분   (새벽 4시 전이라 전날)
//   10/11 09:00 에 기분 3  →  10/11, 컨디션 3, 기분 3, 예상 120분  (계획이 없어서 하루 목표)
//
// 이 함수는 체크인에 들어갈 값만 만듭니다.
// 저장하는 일과, 1탭으로 한 체크인이라고 표시(source)하는 일은 부르는 쪽이 합니다.
//
// FE 의 getPlannedMinutes · makeQuickCheckIn · saveQuickCheckIn (src/lib/quickCheckIn.ts) 과 같은
// 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 체크인이 어긋나지 않습니다.
//
// date: 체크인한 날
// condition: 오늘 컨디션 (1 ~ 5). 기분과 같은 값입니다
// mood: 오늘 기분 (1 ~ 5, 1 이 가장 처짐)
// expectedMinutes: 오늘 예상 순공시간(분)
public record QuickCheckIn(LocalDate date, int condition, int mood, int expectedMinutes) {

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

  // 기분 하나로 체크인을 만듭니다. 컨디션은 기분과 같은 값으로 채웁니다. (규칙 2)
  //
  //   of(10/10, 4, 90)  →  10/10, 컨디션 4, 기분 4, 예상 90분
  public static QuickCheckIn of(LocalDate date, int mood, int expectedMinutes) {
    return new QuickCheckIn(date, mood, mood, expectedMinutes);
  }

  // 지금(now) 고른 기분으로 오늘의 체크인을 만듭니다. 규칙 1 ~ 4 를 한 번에 적용합니다.
  //
  // now: 기분을 고른 순간
  // mood: 고른 기분 (1 ~ 5)
  // plans: 여러 날짜가 섞여 있어도 됩니다. 이 함수가 오늘 것만 골라서 계산합니다
  // fallbackMinutes: 처음 계획에서 정한 하루 목표(분). 처음 계획이 없으면 0
  public static QuickCheckIn of(Instant now, int mood, List<PlanItem> plans, int fallbackMinutes) {
    // 새벽 4시 전이면 전날입니다. (규칙 1)
    LocalDate today = DayRule.dateOf(now);

    List<PlanItem> todayPlans = new ArrayList<>();
    for (PlanItem plan : plans) {
      if (plan.date().equals(today)) {
        todayPlans.add(plan);
      }
    }

    return of(today, mood, plannedMinutes(todayPlans, fallbackMinutes));
  }
}
