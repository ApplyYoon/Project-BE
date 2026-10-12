package com.ithopeschool.projectbe.domain.stats;

import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactPlan;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactSession;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactValue;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

// 주 단위로 숫자를 계산합니다. (이슈 #44 주간 리포트의 "숫자" 부분)
// 숫자 자체는 StudyFacts.of 가 계산하고, 여기서는 "어느 주를", "무엇과 비교할지" 만 정합니다.
//
// 정해 둔 규칙
//   1. 한 주는 월요일에 시작해서 일요일에 끝납니다.
//      하루는 새벽 4시에 시작하므로(DayRule), 월요일 새벽 2시는 아직 일요일 = 지난주입니다.
//   2. 이번 주(오늘이 들어 있는 주)는 월요일부터 "오늘까지" 만 계산합니다.
//      아직 오지 않은 날의 계획까지 넣으면 "계획 대비 실제" 가 주초마다 낮게 보이기 때문입니다.
//   3. 지난 주들은 월요일부터 일요일까지 7일을 모두 계산합니다. 비교하는 "그 전 주" 도 항상 7일입니다.
//   4. 하루 평균 = 그 주의 순공 합 ÷ 계산한 날 수. 이번 주 수요일이면 3일로 나누고, 지난 주면 7일로 나눕니다.
//      마지막에 한 번만 반올림합니다.
//
// 예시 — 오늘이 2026-10-07(수), 이번 주 월 60분 · 화 0분 · 수 90분 공부
//   이번 주는 10/05 ~ 10/07, 그 전 주는 09/28 ~ 10/04 를 계산
//   하루 평균 50분   ((60 + 0 + 90) ÷ 3일)
//
// FE 의 getMondayKey · getWeeklyFacts · getFactDelta (src/lib/weeklyFacts.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 주간 숫자가 어긋나지 않습니다.
//
// thisWeek: 보여줄 주의 숫자 / lastWeek: 그 전 주(7일 전 월요일 ~ 일요일)의 숫자
// dailyAverageMinutes: 보여줄 주의 하루 평균 순공시간(분)
public record WeeklyFacts(StudyFacts thisWeek, StudyFacts lastWeek, int dailyAverageMinutes) {

  // 완주율을 숫자로 보여주려면 있어야 하는 "목표가 있는 타이머" 의 최소 개수입니다.
  // 이보다 적으면 한두 번의 결과로 숫자가 크게 흔들려서, 숫자 대신 "○번 더 쌓이면 보여줄게요" 를 보여줍니다.
  public static final int MIN_COMPLETION_SAMPLES = 5;

  private static final int DAYS_PER_WEEK = 7;

  // 그 순간이 들어 있는 주의 월요일을 돌려줍니다.
  //
  //   10월 10일(토) 15시  →  10월 5일   (그 주 월요일)
  //   10월 11일(일) 23시  →  10월 5일   (일요일은 그 주의 마지막 날)
  //   10월 12일(월) 9시   →  10월 12일  (월요일 9시는 새 주)
  //   10월 12일(월) 2시   →  10월 5일   (월요일 새벽 2시는 아직 일요일)
  public static LocalDate mondayOf(Instant time) {
    return mondayOf(DayRule.dateOf(time));
  }

  // 그 날짜가 들어 있는 주의 월요일을 돌려줍니다. (날짜는 이미 새벽 4시 기준으로 정한 날짜여야 합니다)
  public static LocalDate mondayOf(LocalDate date) {
    // getDayOfWeek 는 월요일 1 ~ 일요일 7 이라서, 1 을 빼면 "월요일에서 며칠 지났는지" 가 됩니다.
    int daysFromMonday = date.getDayOfWeek().getValue() - 1;
    return date.minusDays(daysFromMonday);
  }

  // 한 주(월요일 ~ 일요일)의 숫자와 그 전 주의 숫자를 계산합니다.
  //
  // plans, sessions: 두 주(그 전 주 월요일부터)의 계획과 기록이 들어 있어야 합니다. 다른 날짜가 섞여 있어도 됩니다.
  //   노는 계획과 노는 기록은 부르는 쪽에서 빼고 넘깁니다. (StudyFacts.of 와 같습니다)
  // monday: 보여줄 주의 월요일 (mondayOf 의 결과) / today: 오늘 날짜
  public static WeeklyFacts of(
      List<FactPlan> plans, List<FactSession> sessions, LocalDate monday, LocalDate today) {
    LocalDate sunday = monday.plusDays(DAYS_PER_WEEK - 1);
    // 이번 주면 오늘까지만, 지난 주면 일요일까지 봅니다. (규칙 2, 3)
    LocalDate end = today.isBefore(sunday) ? today : sunday;

    // 월요일부터 끝 날짜까지 며칠인지 셉니다. (1 ~ 7. 끝 날짜가 월요일보다 앞이어도 1 로 봅니다)
    int dayCount = (int) Math.max(1, ChronoUnit.DAYS.between(monday, end) + 1);

    StudyFacts thisWeek = StudyFacts.of(plans, sessions, monday, end);
    StudyFacts lastWeek =
        StudyFacts.of(plans, sessions, monday.minusDays(DAYS_PER_WEEK), monday.minusDays(1));

    int dailyAverageMinutes = (int) Math.round((double) thisWeek.studyMinutes() / dayCount);
    return new WeeklyFacts(thisWeek, lastWeek, dailyAverageMinutes);
  }

  // 두 주의 숫자 차이(이번 − 그 전)를 구합니다. 화면의 ▲▼ 에 씁니다.
  // 둘 중 하나라도 계산할 수 없는 값(null)이면 비교할 수 없어서 null 이고, 차이가 없을 때도 표시하지 않으려고 null 입니다.
  //
  //   64 와 56     →  8
  //   18 과 23     →  -5
  //   64 와 64     →  null   (변화 없음)
  //   64 와 모름    →  null   (지난주 값을 모름)
  public static Integer deltaOf(FactValue current, FactValue previous) {
    if (current.value() == null || previous.value() == null) {
      return null;
    }
    int delta = current.value() - previous.value();
    return delta == 0 ? null : delta;
  }
}
