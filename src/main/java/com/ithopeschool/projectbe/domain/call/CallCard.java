package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.call.dto.CallSessionRequest;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.focus.AwaySummary;
import com.ithopeschool.projectbe.domain.focus.AwaySummary.AwayRecord;
import com.ithopeschool.projectbe.domain.focus.AwaySummary.UnlockRecord;
import com.ithopeschool.projectbe.domain.session.StudyStreak;
import com.ithopeschool.projectbe.domain.session.StudyTime;
import com.ithopeschool.projectbe.domain.stats.StudyFacts;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactPlan;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactSession;
import com.ithopeschool.projectbe.domain.stats.WeeklyFacts;
import com.ithopeschool.projectbe.domain.summary.DaySummary;
import com.ithopeschool.projectbe.domain.summary.DaySummary.DayPlan;
import com.ithopeschool.projectbe.domain.summary.DaySummary.DaySession;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// AI 통화에 쓸 "학생 카드" 를 서버의 기록으로 만듭니다. (이슈 #28 "대본 속 숫자가 모두 DB 에서 계산한 값이다")
// 지금은 FE 가 브라우저의 기록으로 카드를 만들어 보냅니다. (CallSessionRequest.Card)
// 서버에 기록이 쌓이면 이 함수로 같은 카드를 만들어서, FE 가 보낸 숫자를 믿지 않아도 되게 합니다.
// 만든 카드는 CallScript.instructions 에 그대로 넘길 수 있습니다.
//
// 정해 둔 규칙
//   1. 계획도 공부 기록도 체크인도 하나도 없으면 카드가 없습니다(null). 처음 온 사람처럼 통화합니다.
//   2. "오늘·어제" 는 하루의 기준(새벽 4시)을 따릅니다. today 로 받습니다.
//   3. 어제의 순공시간은 DaySummary 가, 이번 주 숫자는 StudyFacts 가 계산합니다. 화면의 숫자와 같은 규칙입니다.
//      이번 주는 월요일부터 오늘까지입니다. (주간 리포트와 같습니다)
//   4. 이번 주 완주율은 목표가 있는 타이머가 5번 이상일 때만 넣습니다. 적으면 숫자가 크게 흔들려서 null 입니다.
//   5. 오늘 첫 계획의 시작 시각은 시각을 정한 계획 중 가장 이른 시각입니다.
//   6. 최근 기분은 오늘 포함 7일 안에 체크인한 날의 기분을 오래된 날부터 모읍니다. 1 ~ 5 가 아닌 값은 뺍니다.
//   7. 나간 기록·집중 모드를 푼 기록은 어제 것의 횟수와 시간대만 넣습니다. (AwaySummary)
//   8. 숫자는 요청 검사(CallSessionRequest.Card)가 받는 범위 안으로 맞춥니다.
//
// ⚠️ 카드에는 숫자와 정해진 분류값만 담습니다. 계획 내용·일기·이름·약속 문장·집중 모드를 풀 때 적은 이유 같은
//    "사용자가 쓴 글" 은 넣지 않습니다. 카드는 AI 회사(OpenAI)로 그대로 넘어가기 때문입니다.
//
// FE 의 buildCallCard (src/lib/callCard.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면에서 건 통화와 서버가 만든 대본이 어긋나지 않습니다.
public final class CallCard {

  // 요청 검사(CallSessionRequest.Card)가 받는 범위입니다.
  private static final int MAX_MINUTES_PER_DAY = 1440;
  private static final int MAX_START_DELAY_MINUTES = 600;
  private static final int MAX_PLAN_COUNT = 50;
  private static final int MAX_STREAK_DAYS = 3650;
  private static final int MAX_AWAY_COUNT = 200;
  private static final int MAX_UNLOCK_COUNT = 50;

  // 최근 기분을 며칠 치까지 볼지 (오늘 포함)
  private static final int MOOD_DAYS = 7;

  // 공부 기록 하나에서 카드 계산에 쓰는 값만 모은 것입니다.
  // planId: 어느 계획으로 켠 타이머인지. 계획 없이 켰으면 null
  // startedAt: 시작한 시각 / endedAt: 끝낸 시각
  // pausedMillis: 일시정지해 있던 시간의 합(밀리초) / pauseCount: 일시정지를 누른 횟수
  // targetMinutes: 타이머를 켤 때 정한 목표(분). 목표 없이 했으면 0
  // (공부 기록 테이블이 아직 바뀌는 중이라, 엔티티가 아니라 이 계산에 필요한 값만 받습니다)
  public record CardSession(
      Long planId,
      Instant startedAt,
      Instant endedAt,
      long pausedMillis,
      int pauseCount,
      int targetMinutes) {}

  // 체크인 하나에서 카드 계산에 쓰는 값만 모은 것입니다. 날짜마다 하나씩 넘깁니다.
  // date: 체크인한 날 / mood: 기분 1 ~ 5 / missReason: 어제 계획을 못 채운 이유. 안 골랐으면 null
  public record CardCheckIn(LocalDate date, int mood, MissReason missReason) {}

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private CallCard() {}

  // 계획·공부 기록·체크인·집중 기록으로 학생 카드를 만듭니다. 기록이 하나도 없으면 null 입니다. (규칙 1)
  //
  // plans, sessions: 계획과 공부 기록. 다른 날짜가 섞여 있어도 됩니다. 노는 계획과 노는 기록은 부르는 쪽에서 빼고 넘깁니다.
  //   연속 일수를 맞게 세려면 공부 기록은 오래된 것까지 넘겨야 합니다.
  // checkIns: 체크인들. 오늘 포함 7일 치가 있으면 됩니다.
  // aways, unlocks: 공부하다 나간 기록과 집중 모드를 푼 기록. 어제 것만 셉니다.
  // today: 오늘 날짜 (하루의 기준인 새벽 4시로 정한 날짜, DayRule) / now: 지금 시각
  public static CallSessionRequest.Card of(
      List<FactPlan> plans,
      List<CardSession> sessions,
      List<CardCheckIn> checkIns,
      List<AwayRecord> aways,
      List<UnlockRecord> unlocks,
      LocalDate today,
      Instant now) {
    if (plans.isEmpty() && sessions.isEmpty() && checkIns.isEmpty()) {
      return null;
    }
    LocalDate yesterday = today.minusDays(1);

    // 같은 기록을 계산마다 필요한 모양으로 바꿔 둡니다.
    List<DayPlan> dayPlans = new ArrayList<>();
    for (FactPlan plan : plans) {
      dayPlans.add(new DayPlan(plan.date(), plan.targetMinutes()));
    }
    List<DaySession> daySessions = new ArrayList<>();
    List<FactSession> factSessions = new ArrayList<>();
    List<Instant> startedAts = new ArrayList<>();
    for (CardSession session : sessions) {
      daySessions.add(
          new DaySession(
              session.startedAt(),
              session.endedAt(),
              session.pausedMillis(),
              session.pauseCount()));
      long studyMillis =
          StudyTime.studyMillis(session.startedAt(), session.endedAt(), session.pausedMillis());
      factSessions.add(
          new FactSession(
              session.planId(), session.startedAt(), studyMillis, session.targetMinutes()));
      startedAts.add(session.startedAt());
    }

    // 어제 · 오늘의 계획
    int yesterdayPlanMinutes = 0;
    int todayPlanCount = 0;
    int todayPlanMinutes = 0;
    LocalTime todayFirstStart = null;
    for (FactPlan plan : plans) {
      if (plan.date().equals(yesterday)) {
        yesterdayPlanMinutes += plan.targetMinutes();
      }
      if (plan.date().equals(today)) {
        todayPlanCount++;
        todayPlanMinutes += plan.targetMinutes();
        LocalTime startTime = plan.startTime();
        if (startTime != null && (todayFirstStart == null || startTime.isBefore(todayFirstStart))) {
          todayFirstStart = startTime;
        }
      }
    }

    DaySummary yesterdaySummary = DaySummary.of(yesterday, dayPlans, daySessions);
    // 이번 주: 월요일부터 오늘까지 (규칙 3)
    StudyFacts week = StudyFacts.of(plans, factSessions, WeeklyFacts.mondayOf(now), today);
    // 어제 하루의 나간 횟수 · 가장 자주 나간 시간대 · 집중 모드를 푼 횟수 (규칙 7)
    AwaySummary yesterdayFocus = AwaySummary.of(aways, unlocks, yesterday, yesterday);

    Integer weekCompletionRate =
        week.completionRate().sampleCount() >= WeeklyFacts.MIN_COMPLETION_SAMPLES
            ? clamp(week.completionRate().value(), 100)
            : null;

    return new CallSessionRequest.Card(
        clamp(yesterdaySummary.studyMinutes(), MAX_MINUTES_PER_DAY),
        clamp(yesterdayPlanMinutes, MAX_MINUTES_PER_DAY),
        missReasonCode(missReasonOf(today, checkIns)),
        clamp(todayPlanCount, MAX_PLAN_COUNT),
        clamp(todayPlanMinutes, MAX_MINUTES_PER_DAY),
        // LocalTime 을 글자로 바꾸면 "19:00" 모양입니다. (초가 없는 시각일 때)
        todayFirstStart == null ? null : todayFirstStart.toString(),
        clamp(week.planRate().value(), 100),
        weekCompletionRate,
        clamp(week.startDelayMinutes().value(), MAX_START_DELAY_MINUTES),
        recentMoodsOf(today, checkIns),
        clamp(StudyStreak.streakOf(startedAts, now), MAX_STREAK_DAYS),
        clamp(yesterdayFocus.awayCount(), MAX_AWAY_COUNT),
        yesterdayFocus.peakHour() == null ? null : yesterdayFocus.peakHour().hour(),
        clamp(yesterdayFocus.unlockCount(), MAX_UNLOCK_COUNT));
  }

  // 오늘 체크인에서 고른 "어제 못 채운 이유" 를 돌려줍니다. 오늘 체크인이 없거나 안 골랐으면 null 입니다.
  private static MissReason missReasonOf(LocalDate today, List<CardCheckIn> checkIns) {
    for (CardCheckIn checkIn : checkIns) {
      if (checkIn.date().equals(today)) {
        return checkIn.missReason();
      }
    }
    return null;
  }

  // 오늘 포함 7일 안에 체크인한 날의 기분을 오래된 날부터 모읍니다. (규칙 6)
  private static List<Integer> recentMoodsOf(LocalDate today, List<CardCheckIn> checkIns) {
    LocalDate oldest = today.minusDays(MOOD_DAYS - 1);
    List<CardCheckIn> recent = new ArrayList<>();
    for (CardCheckIn checkIn : checkIns) {
      if (!checkIn.date().isBefore(oldest) && !checkIn.date().isAfter(today)) {
        recent.add(checkIn);
      }
    }
    recent.sort(Comparator.comparing(CardCheckIn::date));

    List<Integer> moods = new ArrayList<>();
    for (CardCheckIn checkIn : recent) {
      if (checkIn.mood() >= 1 && checkIn.mood() <= 5) {
        moods.add(checkIn.mood());
      }
    }
    return moods;
  }

  // 못 채운 이유를 카드에 담는 글자로 바꿉니다. FE 의 MissReason 과 같은 글자입니다. 안 골랐으면 null 입니다.
  //
  //   TOO_MUCH → "tooMuch"   /   PHONE → "phone"
  static String missReasonCode(MissReason missReason) {
    if (missReason == null) {
      return null;
    }
    return switch (missReason) {
      case TIRED -> "tired";
      case EVENT -> "event";
      case PHONE -> "phone";
      case TOO_MUCH -> "tooMuch";
      case UNWILLING -> "unwilling";
      case SICK -> "sick";
      case PROCRASTINATED -> "procrastinated";
      case ETC -> "etc";
    };
  }

  // 숫자를 0 ~ max 안으로 맞춥니다. 모르는 값(null)은 그대로 null 입니다. (규칙 8)
  private static Integer clamp(Integer value, int max) {
    if (value == null) {
      return null;
    }
    return Math.min(max, Math.max(0, value));
  }
}
