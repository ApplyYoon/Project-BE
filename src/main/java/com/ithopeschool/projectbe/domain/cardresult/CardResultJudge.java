package com.ithopeschool.projectbe.domain.cardresult;

import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.cardresult.CardDay.Session;
import com.ithopeschool.projectbe.domain.session.SessionMode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 카드 결과 판정기입니다. (최종 기획서 「5.1 카드 결과」, 이슈 #229)
// 값만 계산하는 순수 함수라서 DB 를 모릅니다. 카드 하루치(CardDay)는 CardResultService 가 만들어 넘깁니다.
//
// 카드 하루치(그 카드 · 그 날짜)를 두 축으로 봅니다.
//   - 실행 축: 제때 시작해서 끝까지 했는지 → 결과 8가지(CardResult) + 실행률
//   - 계획 축: 체크리스트를 얼마나 했는지 → 계획 과대 / 실행 축 문제 (PlanAxis)
//
// FE 의 src/lib/cardResult.ts 와 같은 규칙 · 같은 숫자입니다. 테스트(CardResultJudgeTest)는 FE 주석의 예시를 그대로 옮긴 것입니다.
// 숫자는 모두 출발값입니다. 바꿀 때는 FE(#381)의 상수와 같이 고칩니다. 한쪽만 고치면 화면과 서버의 결과가 어긋납니다.
public final class CardResultJudge {

  // 실행률이 이만큼 이상인데 체크리스트가 남으면 '계획 과대' 입니다
  public static final double PLAN_AXIS_RUN_RATE = 0.8;
  // 증빙 없는 사후 입력(자기 보고)은 실행률을 이 값으로 넣습니다 (「4.4 타이머」 사후 입력)
  public static final double SELF_REPORT_RUN_RATE = 0.5;
  // 계획 패턴: 최근 4주 체크리스트 달성률 평균이 이 값을 넘으면 '느슨'
  public static final double PATTERN_LOOSE_OVER = 0.9;
  // 계획 패턴: 평균이 이 값 밑이면 '과대'
  public static final double PATTERN_OVER_UNDER = 0.6;
  // 계획 패턴을 말하려면 카드 하루치가 이만큼은 있어야 합니다 (그 전에는 '가설' 로만 말한다, 「2 수집 원칙」)
  public static final int PATTERN_MIN_SAMPLES = 5;
  // 예정 시각보다 이만큼(분)까지 늦게 시작해도 "제때" 로 봅니다 (FE planResult.ts 의 ON_TIME_GRACE_MINUTES)
  public static final int ON_TIME_GRACE_MINUTES = 10;

  private static final long MILLIS_PER_MINUTE = 60_000;

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private CardResultJudge() {}

  // 카드 하루치의 결과를 정합니다. 아직 판정할 수 없으면 null 입니다 (카드 시간 전이고 세션도 없음).
  //
  // ■ 규칙 (위에서부터 먼저 맞는 것)
  //   1. 사람이 처리한 표시가 있으면 그것 (휴식 → REST, 미루기 → POSTPONED, 실패 → FAILED)
  //   2. 세션이 없으면: 카드 시간이 끝났으면 UNHANDLED, 아니면 null
  //      (시작 시각이 없는 옛 카드는 그날이 끝나면(다음 날 새벽 4시) 끝난 것으로 봅니다)
  //   3. 세션이 증빙 없는 사후 입력뿐이면 SELF_REPORT (증빙이 있으면 일반 실행과 똑같이 봅니다)
  //   4. [끝내기] 로 끝낸 세션이 있고 실행률이 1 이 안 되면 ENDED_EARLY
  //   5. 처음 시작한 시각이 예정 시각 + 10분 안이면 DONE, 더 늦으면 LATE
  //      (일찍 시작해도 DONE, 시작 시각이 없는 카드도 DONE)
  //   ※ 관리 4단계에서 증빙이 꼭 필요했는데 증빙 없이 끝낸 세션은 처음부터 없는 것으로 봅니다 (countedSessions)
  //
  // ■ 예시 (10/17 19:00 시작 · 60분 카드)
  //   세션 19:05 ~ 20:00                → DONE
  //   세션 18:50 ~ 20:00 (일찍 시작)     → DONE
  //   세션 19:20 ~ 20:00                → LATE
  //   세션 19:00 ~ 19:30, [끝내기]      → ENDED_EARLY
  //   세션 없음, 지금 19:30             → null
  //   세션 없음, 지금 20:01             → UNHANDLED
  //   표시 REST (세션과 상관없이)         → REST
  //   사후 입력 세션만                   → SELF_REPORT
  public static CardResult getCardResult(CardDay day, Instant now) {
    // 규칙 1
    if (day.mark() != null) {
      return day.mark();
    }

    Instant scheduledStartAt = scheduledStartAt(day);
    List<Session> sessions = countedSessions(day);

    // 규칙 2
    if (sessions.isEmpty()) {
      if (scheduledStartAt == null) {
        // 시작 시각이 없으면 그날이 지나야(새벽 4시 기준 날짜가 바뀌어야) 끝난 것입니다.
        return DayRule.dateOf(now).isAfter(day.date()) ? CardResult.UNHANDLED : null;
      }
      Instant scheduledEndAt =
          scheduledStartAt.plusMillis(day.durationMinutes() * MILLIS_PER_MINUTE);
      // 예정 종료 시각과 같은 순간부터 끝난 것입니다. (isBefore 가 아니면 = 같거나 뒤)
      return now.isBefore(scheduledEndAt) ? null : CardResult.UNHANDLED;
    }

    // 규칙 3
    boolean onlyUnprovenSelfReport = true;
    for (Session session : sessions) {
      if (!isUnprovenSelfReport(session)) {
        onlyUnprovenSelfReport = false;
      }
    }
    if (onlyUnprovenSelfReport) {
      return CardResult.SELF_REPORT;
    }

    // 규칙 4
    boolean anyEndedEarly = false;
    for (Session session : sessions) {
      if (session.endedEarly()) {
        anyEndedEarly = true;
      }
    }
    if (anyEndedEarly && getRunRate(day) < 1) {
      return CardResult.ENDED_EARLY;
    }

    // 규칙 5
    if (scheduledStartAt == null) {
      return CardResult.DONE;
    }
    return isWithinGrace(firstStartAt(sessions), scheduledStartAt)
        ? CardResult.DONE
        : CardResult.LATE;
  }

  // 정시에 시작했는지 (예정 시각 + 10분 안에 처음 시작). 의지력의 '정시 시작 비율' 에 씁니다 (「5.3」).
  // 시작하지 않았거나(세션 없음) 사후 입력뿐이거나 시작 시각이 없는 카드면 null 입니다 (비율 계산에서 뺌).
  //
  //   19:00 카드, 19:05 시작 → true     19:00 카드, 19:20 시작 → false     세션 없음 → null
  public static Boolean isStartedOnTime(CardDay day) {
    // 사후 입력은 시각을 나중에 적은 것이라 "제때 시작" 을 따지지 않습니다.
    List<Session> measured = new ArrayList<>();
    for (Session session : countedSessions(day)) {
      if (session.mode() != SessionMode.SELF_REPORT) {
        measured.add(session);
      }
    }
    if (measured.isEmpty()) {
      return null;
    }
    Instant scheduledStartAt = scheduledStartAt(day);
    if (scheduledStartAt == null) {
      return null;
    }
    return isWithinGrace(firstStartAt(measured), scheduledStartAt);
  }

  // 판정에 넣는 세션이 하나라도 있는지. 없으면 계획 축 · 체크리스트 달성률 · 예측 정확도를 말하지 않습니다.
  public static boolean hasCountedSession(CardDay day) {
    return !countedSessions(day).isEmpty();
  }

  // 실행률 = 실제 실행 시간(일시정지 제외) ÷ 계획 시간, 최대 1
  //   - 증빙 없는 사후 입력이 있으면 0.5 로 넣습니다 (증빙이 있으면 다른 세션과 똑같이 계산)
  //   - 계획 시간이 0분이면 0
  //
  //   60분 카드, 45분 공부             → 0.75
  //   60분 카드, 70분 공부             → 1
  //   60분 카드, 증빙 없는 사후 입력    → 0.5
  public static double getRunRate(CardDay day) {
    long plannedMillis = day.durationMinutes() * MILLIS_PER_MINUTE;
    if (plannedMillis <= 0) {
      return 0;
    }
    List<Session> sessions = countedSessions(day);
    for (Session session : sessions) {
      if (isUnprovenSelfReport(session)) {
        return SELF_REPORT_RUN_RATE;
      }
    }
    long runMillis = 0;
    for (Session session : sessions) {
      runMillis += session.studyMillis();
    }
    return Math.min(1, (double) runMillis / plannedMillis);
  }

  // 그날 체크한 체크리스트 항목 수 (여러 세션에 나뉘어 있어도 한 번씩만 셈)
  // 지금 카드의 체크리스트에 있는 항목만 셉니다. (카드를 고쳐서 지운 항목의 체크는 세지 않음)
  public static int getCheckedCount(CardDay day) {
    Set<String> itemKeys = new HashSet<>(day.checklistKeys());
    Set<String> checked = new HashSet<>();
    for (Session session : countedSessions(day)) {
      for (String key : session.checkedKeys()) {
        if (itemKeys.contains(key)) {
          checked.add(key);
        }
      }
    }
    return checked.size();
  }

  // 체크리스트 달성률 (0 ~ 1). 체크리스트가 비어 있으면 1
  public static double getChecklistRate(CardDay day) {
    int total = day.checklistKeys().size();
    return total == 0 ? 1 : (double) getCheckedCount(day) / total;
  }

  // 계획 축 — 체크리스트가 남았을 때 어느 쪽 문제인지. 세션이 없으면 null
  //
  //   체크 3/3, 실행률 1     → COMPLETE
  //   체크 2/3, 실행률 0.9   → OVER_PLANNED
  //   체크 2/3, 실행률 0.5   → EXECUTION
  public static PlanAxis getPlanAxis(CardDay day) {
    if (!hasCountedSession(day)) {
      return null;
    }
    if (getChecklistRate(day) >= 1) {
      return PlanAxis.COMPLETE;
    }
    return getRunRate(day) >= PLAN_AXIS_RUN_RATE ? PlanAxis.OVER_PLANNED : PlanAxis.EXECUTION;
  }

  // 예측 정확도 = 실제로 체크한 개수 − 예상 달성 개수 (예상이 없으면 체크리스트 전부)
  // 음수면 예상보다 덜 했다는 뜻입니다. 세션이 없으면 null
  //
  //   체크리스트 5개, 예상 없음, 3개 체크  → -2
  //   체크리스트 5개, 예상 3개, 3개 체크   → 0
  public static Integer getPredictionGap(CardDay day) {
    if (!hasCountedSession(day)) {
      return null;
    }
    int expected = day.expectedDone() == null ? day.checklistKeys().size() : day.expectedDone();
    return getCheckedCount(day) - expected;
  }

  // 같은 목표 카드의 최근 4주 체크리스트 달성률(카드 하루치마다 0 ~ 1)로 계획 패턴을 정합니다.
  // 평균이 90% 를 넘으면 느슨, 60% 밑이면 과대, 그 사이는 적정입니다.
  // (기획서의 적정 구간은 70~90% 입니다. 60~70% 는 과대라고 할 만큼은 아니라 적정에 넣었습니다 — FE 와 같음)
  // 5개보다 적으면 아직 말하지 않습니다 (null).
  //
  //   [1, 1, 1, 1, 0.95]        → LOOSE
  //   [0.8, 0.7, 0.9, 0.8, 0.8] → FIT
  //   [0.5, 0.4, 0.6, 0.5, 0.7] → OVER
  //   [1, 1]                    → null
  public static PlanPattern getPlanPattern(List<Double> checklistRates) {
    if (checklistRates.size() < PATTERN_MIN_SAMPLES) {
      return null;
    }
    double sum = 0;
    for (double rate : checklistRates) {
      sum += rate;
    }
    double average = sum / checklistRates.size();
    if (average > PATTERN_LOOSE_OVER) {
      return PlanPattern.LOOSE;
    }
    if (average < PATTERN_OVER_UNDER) {
      return PlanPattern.OVER;
    }
    return PlanPattern.FIT;
  }

  // ---- 도우미 ----

  // 판정에 넣는 세션 — 관리 4단계에서 증빙이 꼭 필요했는데 증빙 없이 끝낸 세션은 실행으로 인정하지 않아 뺍니다. (FE #408)
  //   [보통 세션, 증빙 필요인데 없음] → [보통 세션]
  private static List<Session> countedSessions(CardDay day) {
    List<Session> counted = new ArrayList<>();
    for (Session session : day.sessions()) {
      if (session.proofRequired() && !session.hasProof()) {
        continue;
      }
      counted.add(session);
    }
    return counted;
  }

  // 증빙 없는 사후 입력인지
  private static boolean isUnprovenSelfReport(Session session) {
    return session.mode() == SessionMode.SELF_REPORT && !session.hasProof();
  }

  // 그날 카드의 예정 시작 순간. 시작 시각이 없는 카드면 null
  // 00:00 ~ 03:59 는 달력으로 다음 날 새벽입니다. (Card.startAtOf, FE getPlannedStartAt 과 같음)
  private static Instant scheduledStartAt(CardDay day) {
    if (day.startTime() == null) {
      return null;
    }
    return Card.startAtOf(day.date(), day.startTime());
  }

  // 세션 중 가장 먼저 시작한 시각 (세션이 하나 이상일 때만 부릅니다)
  private static Instant firstStartAt(List<Session> sessions) {
    Instant first = sessions.get(0).startedAt();
    for (Session session : sessions) {
      if (session.startedAt().isBefore(first)) {
        first = session.startedAt();
      }
    }
    return first;
  }

  // 처음 시작한 시각이 예정 시각 + 10분 안인지 (일찍 시작했으면 차이가 음수라 true)
  private static boolean isWithinGrace(Instant startedAt, Instant scheduledStartAt) {
    long lateMillis = Duration.between(scheduledStartAt, startedAt).toMillis();
    return lateMillis <= ON_TIME_GRACE_MINUTES * MILLIS_PER_MINUTE;
  }
}
