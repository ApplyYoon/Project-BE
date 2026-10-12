package com.ithopeschool.projectbe.domain.summary;

import com.ithopeschool.projectbe.domain.plan.AchievementRate;
import com.ithopeschool.projectbe.domain.session.StudyTime;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 하루치 계획과 공부 기록으로 그날의 기본 지표를 계산합니다. (이슈 #34 하루 요약표의 "계산" 부분)
// 숫자는 코드가 계산하고, AI 는 그 숫자를 해석만 합니다.
//
// 정해 둔 규칙
//   1. 날짜는 한국 시간 기준이고, 하루는 새벽 4시에 바뀝니다. (DayRule) 00:00 ~ 03:59 에 시작한 기록은 전날 기록입니다.
//   2. 새벽 4시를 넘긴 기록은 "시작한 날" 의 기록으로 셉니다. 전체 시간을 시작한 날에 넣고, 다음 날에는 0분입니다.
//   3. 순공시간(분)은 기록의 순공(밀리초)을 모두 더한 뒤 마지막에 한 번만 반올림합니다. 기록마다 반올림하지 않습니다.
//      기록의 순공 = 끝 − 시작 − 일시정지한 시간. 계산은 StudyTime.studyMillis 가 합니다.
//   4. 평균 기록 길이(분)도 (전체 길이 ÷ 기록 수) 를 마지막에 한 번만 반올림합니다.
//   5. 달성률 = 순공시간 ÷ 계획 목표 시간 합계 × 100, 소수점은 버리고 100 을 넘으면 100 입니다. (AchievementRate.percentOf)
//   6. 계획 목표 시간 합계가 0 이면 나눌 수 없으므로 달성률은 null(계산 불가) 입니다. 공부를 했어도 null 입니다.
//   7. 끝 시각이 시작 시각보다 빠른 잘못된 기록은 길이를 0분으로 칩니다.
//   8. 일시정지 횟수는 그날 시작한 기록들의 횟수를 모두 더합니다. 음수 같은 이상한 값은 0번으로 칩니다.
//   9. 일시정지해 있던 시간은 순공에서 뺍니다. 음수이거나 (끝 − 시작)보다 큰 이상한 값은 빼지 않습니다.
//
// 예시 (입력 → 결과)
//   계획도 기록도 없는 날               →  순공 0, 달성률 null, 첫 시작 null, 평균 0, 일시정지 0
//   수학 60분 계획, 기록 없음            →  순공 0, 달성률 0   (0% 는 "계획은 있는데 공부를 안 한 날" 입니다)
//   계획 240분, 10:00~11:00 (일시정지 1번) + 14:00~16:10 (일시정지 2번)
//     →  순공 190, 달성률 79 (190 ÷ 240 = 79.1…), 첫 시작 10:00, 평균 95, 일시정지 3
//   계획 240분인데 239분 공부            →  달성률 99  (100 은 다 채웠을 때만 나옵니다)
//   10월 5일 03:30 ~ 04:30              →  60분 전부가 10월 4일 기록. 10월 5일은 0분
//
// FE 의 getDaySummary (src/lib/daySummary.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 숫자가 어긋나지 않습니다.
//
// studyMinutes: 순공시간(분)
// achievementRate: 계획 달성률(0~100). 계획이 없는 날은 null
// firstStartAt: 그날 가장 먼저 공부를 시작한 시각. 공부 기록이 없으면 null
// averageSessionMinutes: 기록 하나의 평균 순공시간(분). 공부 기록이 없으면 0
// pauseCount: 일시정지를 누른 횟수의 합계
public record DaySummary(
    int studyMinutes,
    Integer achievementRate,
    Instant firstStartAt,
    int averageSessionMinutes,
    int pauseCount) {

  private static final long MILLIS_PER_MINUTE = 60_000;

  // 공부 계획 하나에서 계산에 쓰는 값만 모은 것입니다.
  // date: 계획한 날짜 / targetMinutes: 목표 공부 시간(분)
  public record DayPlan(LocalDate date, int targetMinutes) {}

  // 공부 기록 하나에서 계산에 쓰는 값만 모은 것입니다.
  // startedAt: 시작한 시각 / endedAt: 끝낸 시각
  // pausedMillis: 그 사이에 일시정지해 있던 시간의 합(밀리초). 일시정지한 적이 없으면 0
  // pauseCount: 일시정지를 누른 횟수
  // (공부 기록 테이블이 아직 바뀌는 중이라, 엔티티가 아니라 이 계산에 필요한 네 값만 받습니다)
  public record DaySession(Instant startedAt, Instant endedAt, long pausedMillis, int pauseCount) {}

  // date 하루의 기본 지표를 계산합니다.
  //
  // date: 계산할 날짜
  // plans, sessions: 여러 날짜가 섞여 있어도 됩니다. 이 함수가 date 에 해당하는 것만 골라서 계산합니다.
  //   노는 계획과 노는 기록은 공부가 아니라서, 부르는 쪽에서 빼고 넘깁니다. (FE 는 함수 안에서 걸러냅니다)
  public static DaySummary of(LocalDate date, List<DayPlan> plans, List<DaySession> sessions) {
    // 그날 시작한 기록의 길이를 모두 더하고, 가장 이른 시작 시각을 찾습니다. (규칙 1, 2)
    long totalMillis = 0;
    int sessionCount = 0;
    Instant firstStartAt = null;
    int pauseCount = 0;
    for (DaySession session : sessions) {
      if (!DayRule.dateOf(session.startedAt()).equals(date)) {
        continue;
      }
      sessionCount++;

      // 일시정지한 시간을 뺀 순공입니다. 끝이 시작보다 빠른 잘못된 기록은 0 으로 칩니다. (규칙 7, 9)
      totalMillis +=
          StudyTime.studyMillis(session.startedAt(), session.endedAt(), session.pausedMillis());

      if (firstStartAt == null || session.startedAt().isBefore(firstStartAt)) {
        firstStartAt = session.startedAt();
      }

      // 일시정지 횟수를 더합니다. 음수는 더하지 않습니다. (규칙 8)
      if (session.pauseCount() > 0) {
        pauseCount += session.pauseCount();
      }
    }

    // 마지막에 한 번만 반올림합니다. (규칙 3, 4)
    int studyMinutes = StudyTime.toMinutes(totalMillis);
    int averageSessionMinutes =
        sessionCount == 0
            ? 0
            : (int) Math.round((double) totalMillis / sessionCount / MILLIS_PER_MINUTE);

    // 그날 계획의 목표 시간을 모두 더합니다.
    int targetMinutes = 0;
    for (DayPlan plan : plans) {
      if (plan.date().equals(date)) {
        targetMinutes += plan.targetMinutes();
      }
    }

    // 목표 시간 합계가 0 이면 달성률은 null 입니다. (규칙 5, 6)
    Integer achievementRate = AchievementRate.percentOf(studyMinutes, targetMinutes);

    return new DaySummary(
        studyMinutes, achievementRate, firstStartAt, averageSessionMinutes, pauseCount);
  }
}
