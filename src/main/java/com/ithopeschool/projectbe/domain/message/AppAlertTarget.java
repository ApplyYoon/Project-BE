package com.ithopeschool.projectbe.domain.message;

import com.ithopeschool.projectbe.domain.stats.StudyFacts;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactPlan;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// 앱 알림이 "무엇을 두고" 말할지 찾습니다. (이슈 #31)
// EventMessage.pick 은 보낼지 말지를, AppAlert 는 문장을 정합니다. 여기서는 그 둘에 넣을 대상을 고릅니다.
//
//   "19:00 국어 비문학, 시작할 시간이야"  →  어느 계획인가?  →  firstPlannedPlan
//   "국어 50분 완주!"                     →  어느 타이머인가? →  finishedTimerToday
//
// 정해 둔 규칙
//   1. 계획은 "시작 시각을 정했고 아직 안 끝낸 공부 계획" 만 봅니다. 그중 시작 시각이 가장 이른 것 하나입니다.
//      노는 계획에는 독촉하지 않습니다. 부르는 쪽에서 공부 계획만 넘깁니다.
//   2. 00:00 ~ 03:59 로 적은 계획은 달력으로 다음 날 새벽입니다. 그래서 01:00 계획은 23:00 계획보다 뒤입니다.
//      (하루의 기준 새벽 4시, StudyFacts.plannedStartAt)
//   3. 계획의 시작 시각이 "정말 지났을 때" 만 늦은 것으로 봅니다. 시각만 비교하면 저녁 8시에
//      새벽 1시 계획을 "지났다" 고 잘못 볼 수 있어서, 날짜까지 합친 순간으로 비교합니다. (passedStartTime)
//   4. 타이머는 "목표를 정했고 그 목표를 채운 오늘의 공부 기록" 만 봅니다. 그중 가장 늦게 끝난 것 하나입니다.
//      채웠는지는 순공시간(일시정지 뺀 시간)이 목표 이상인지로 봅니다. 주간 지표의 완주(StudyFacts)와 같은 기준입니다.
//
// 예시
//   19:00 국어(안 끝냄) · 21:00 수학(안 끝냄) · 영어(시각 없음)   →  19:00 국어
//   19:00 국어(끝냄) · 21:00 수학(안 끝냄)                         →  21:00 수학
//   전부 시각 없음                                                 →  null
//   오늘 기록: 목표 50분에 50분 함, 목표 30분에 10분 함            →  앞의 기록
//   오늘 기록: 목표 없이 2시간 함                                  →  null  (약속한 목표가 없습니다)
//   어제 기록만 있음                                               →  null
//
// ⚠️ 늦었다고 보는 기준은 "시작 시각이 되면 바로" 입니다. FE 와 같고, 여유 시간(예: 15분)은 두지 않았습니다.
// FE 의 findFirstPlannedPlan · findFinishedTimerToday (src/lib/appAlert.ts),
// checkAppAlerts 의 isPlanTimePassed (src/lib/appAlertCheck.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버가 같은 계획·같은 타이머를 고릅니다.
public final class AppAlertTarget {

  private static final long MILLIS_PER_MINUTE = 60_000;

  // 공부 계획 하나에서 필요한 값만 모은 것입니다.
  // id: 계획 번호 / date: 계획한 날짜 / startTime: 시작하기로 한 시각. 정하지 않았으면 null / done: 끝냈는지
  public record PlanItem(Long id, LocalDate date, LocalTime startTime, boolean done) {}

  // 가장 먼저 시작하기로 한 계획과, 그 계획을 시작하기로 한 순간입니다.
  public record FirstPlan(PlanItem plan, Instant startAt) {}

  // 공부 기록 하나에서 필요한 값만 모은 것입니다.
  // subjectId: 과목 번호 ("국어 50분 완주!" 의 과목)
  // startedAt: 시작한 시각 / endedAt: 끝낸 시각
  // studyMillis: 순공시간(밀리초). StudyTime.studyMillis 의 결과를 넣습니다.
  // targetMinutes: 타이머를 켤 때 정한 목표(분). 목표 없이 했으면 0
  // (공부 기록 테이블이 아직 바뀌는 중이라, 엔티티가 아니라 이 계산에 필요한 값만 받습니다)
  public record TimerItem(
      Long subjectId, Instant startedAt, Instant endedAt, long studyMillis, int targetMinutes) {}

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private AppAlertTarget() {}

  // 오늘 계획 중 "가장 먼저 시작하기로 한, 아직 안 끝낸 계획" 을 돌려줍니다. 없으면 null 입니다. (규칙 1, 2)
  //
  // todayStudyPlans: 오늘의 공부 계획들. 노는 계획은 부르는 쪽에서 빼고 넘깁니다.
  public static FirstPlan firstPlannedPlan(List<PlanItem> todayStudyPlans) {
    FirstPlan first = null;
    for (PlanItem plan : todayStudyPlans) {
      if (plan.done()) {
        continue;
      }
      // 날짜와 시작 시각을 합쳐 "시작하기로 한 순간" 을 만듭니다. 시각을 안 정했으면 null 입니다.
      Instant startAt =
          StudyFacts.plannedStartAt(new FactPlan(plan.id(), plan.date(), 0, plan.startTime()));
      if (startAt == null) {
        continue;
      }
      // 더 이를 때만 바꾸므로, 시작 시각이 같으면 목록에서 먼저 나온 계획이 남습니다.
      if (first == null || startAt.isBefore(first.startAt())) {
        first = new FirstPlan(plan, startAt);
      }
    }
    return first;
  }

  // EventMessage.pick 의 plannedStartTime 에 넣을 값을 돌려줍니다. (규칙 3)
  // 가장 먼저 시작하기로 한 계획의 시각이 정말 지났으면 그 시각을, 아직 안 됐거나 그런 계획이 없으면 null 을 돌려줍니다.
  //
  //   지금 20:00, 19:00 계획          →  19:00   (지났음)
  //   지금 18:00, 19:00 계획          →  null    (아직 안 됨)
  //   지금 20:00, 01:00 계획(내일 새벽) →  null    (시각만 보면 지난 것 같지만 아직 안 됨)
  public static LocalTime passedStartTime(FirstPlan first, Instant now) {
    if (first == null || first.startAt().isAfter(now)) {
      return null;
    }
    return first.plan().startTime();
  }

  // 오늘 "목표를 정한 타이머" 를 끝까지 한 기록 중 가장 늦게 끝난 것을 돌려줍니다. 없으면 null 입니다. (규칙 4)
  //
  // studySessions: 공부 기록들. 다른 날짜가 섞여 있어도 됩니다. 노는 기록은 부르는 쪽에서 빼고 넘깁니다.
  // today: 오늘 날짜 (하루의 기준인 새벽 4시로 정한 날짜, DayRule)
  public static TimerItem finishedTimerToday(List<TimerItem> studySessions, LocalDate today) {
    TimerItem latest = null;
    for (TimerItem session : studySessions) {
      // 목표가 없는 타이머와 오늘 시작하지 않은 타이머는 보지 않습니다.
      if (session.targetMinutes() <= 0 || !DayRule.dateOf(session.startedAt()).equals(today)) {
        continue;
      }
      if (session.studyMillis() < session.targetMinutes() * MILLIS_PER_MINUTE) {
        continue;
      }
      if (latest == null || session.endedAt().isAfter(latest.endedAt())) {
        latest = session;
      }
    }
    return latest;
  }
}
