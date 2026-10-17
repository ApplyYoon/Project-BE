package com.ithopeschool.projectbe.domain.cardresult;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.cardresult.CardDay.Session;
import com.ithopeschool.projectbe.domain.session.SessionMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// 카드 결과 판정을 FE src/lib/cardResult.ts 주석의 예시 그대로 확인합니다. (이슈 #229)
// 같은 입력이면 FE 와 같은 결과가 나와야 리포트 · 통화가 어긋나지 않습니다.
class CardResultJudgeTest {

  // FE 예시의 카드: 10/17 19:00 시작 · 60분
  private static final LocalDate OCT_17 = LocalDate.of(2026, 10, 17);
  private static final LocalTime SEVEN_PM = LocalTime.of(19, 0);
  private static final long MINUTE = 60_000;

  // 한국 시간 2026년 10월의 순간입니다.
  private static Instant kst(int day, int hour, int minute) {
    return ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, ZoneId.of("Asia/Seoul")).toInstant();
  }

  // 타이머 세션 (from ~ to, 일시정지 없음)
  private static Session timer(Instant from, Instant to) {
    return new Session(
        from,
        to.toEpochMilli() - from.toEpochMilli(),
        SessionMode.NORMAL,
        false,
        Set.of(),
        false,
        false);
  }

  // [끝내기] 로 끝낸 타이머 세션
  private static Session endedEarly(Instant from, Instant to) {
    return new Session(
        from,
        to.toEpochMilli() - from.toEpochMilli(),
        SessionMode.NORMAL,
        true,
        Set.of(),
        false,
        false);
  }

  // 순공시간만 정한 타이머 세션 (19:00 시작)
  private static Session studied(long minutes) {
    return new Session(
        kst(17, 19, 0), minutes * MINUTE, SessionMode.NORMAL, false, Set.of(), false, false);
  }

  // 체크한 항목이 있는 타이머 세션 (19:00 시작)
  private static Session checked(long minutes, String... keys) {
    return new Session(
        kst(17, 19, 0), minutes * MINUTE, SessionMode.NORMAL, false, Set.of(keys), false, false);
  }

  // 사후 입력 세션 (19:00 ~ 20:00 으로 적음). proof 가 true 면 증빙이 있음
  private static Session selfReport(boolean proof) {
    return new Session(
        kst(17, 19, 0), 60 * MINUTE, SessionMode.SELF_REPORT, false, Set.of(), proof, false);
  }

  // 체크리스트 항목 1개짜리 10/17 19:00 · 60분 카드 하루치
  private static CardDay day(List<Session> sessions) {
    return new CardDay(SEVEN_PM, 60, List.of("i1"), null, OCT_17, sessions, null);
  }

  // 체크리스트 항목 n개짜리 카드 하루치 (i1, i2, ...)
  private static CardDay dayWithChecklist(int items, Integer expectedDone, List<Session> sessions) {
    List<String> keys = new ArrayList<>();
    for (int i = 1; i <= items; i++) {
      keys.add("i" + i);
    }
    return new CardDay(SEVEN_PM, 60, keys, expectedDone, OCT_17, sessions, null);
  }

  @Nested
  @DisplayName("getCardResult — 10/17 19:00 시작 · 60분 카드")
  class GetCardResult {

    private final Instant afterCard = kst(17, 21, 0);

    @Test
    @DisplayName("19:05 ~ 20:00 → 실행, 18:50 ~ 20:00(일찍 시작) → 실행, 19:20 ~ 20:00 → 지연")
    void doneAndLate() {
      assertThat(
              CardResultJudge.getCardResult(
                  day(List.of(timer(kst(17, 19, 5), kst(17, 20, 0)))), afterCard))
          .isEqualTo(CardResult.DONE);
      assertThat(
              CardResultJudge.getCardResult(
                  day(List.of(timer(kst(17, 18, 50), kst(17, 20, 0)))), afterCard))
          .isEqualTo(CardResult.DONE);
      assertThat(
              CardResultJudge.getCardResult(
                  day(List.of(timer(kst(17, 19, 20), kst(17, 20, 0)))), afterCard))
          .isEqualTo(CardResult.LATE);
    }

    @Test
    @DisplayName("딱 10분 늦게(19:10) 시작하면 아직 제때, 19:11 은 지연")
    void graceBoundary() {
      assertThat(
              CardResultJudge.getCardResult(
                  day(List.of(timer(kst(17, 19, 10), kst(17, 20, 10)))), afterCard))
          .isEqualTo(CardResult.DONE);
      assertThat(
              CardResultJudge.getCardResult(
                  day(List.of(timer(kst(17, 19, 11), kst(17, 20, 11)))), afterCard))
          .isEqualTo(CardResult.LATE);
    }

    @Test
    @DisplayName("19:00 ~ 19:30 에 [끝내기] → 중도 종료, 끝까지 채우고 [끝내기] 면 실행")
    void endedEarlyResult() {
      assertThat(
              CardResultJudge.getCardResult(
                  day(List.of(endedEarly(kst(17, 19, 0), kst(17, 19, 30)))), afterCard))
          .isEqualTo(CardResult.ENDED_EARLY);
      // 실행률이 1 이면 [끝내기] 를 눌렀어도 중도 종료가 아닙니다.
      assertThat(
              CardResultJudge.getCardResult(
                  day(List.of(endedEarly(kst(17, 19, 0), kst(17, 20, 0)))), afterCard))
          .isEqualTo(CardResult.DONE);
    }

    @Test
    @DisplayName("세션 없음: 지금 19:30 → 아직 모름(null), 20:01 → 미처리, 딱 20:00 → 미처리")
    void noSession() {
      assertThat(CardResultJudge.getCardResult(day(List.of()), kst(17, 19, 30))).isNull();
      assertThat(CardResultJudge.getCardResult(day(List.of()), kst(17, 20, 1)))
          .isEqualTo(CardResult.UNHANDLED);
      assertThat(CardResultJudge.getCardResult(day(List.of()), kst(17, 20, 0)))
          .isEqualTo(CardResult.UNHANDLED);
    }

    @Test
    @DisplayName("표시가 있으면 세션과 상관없이 그 표시 (휴식 · 미루기 · 실패)")
    void markFirst() {
      List<Session> sessions = List.of(timer(kst(17, 19, 0), kst(17, 20, 0)));
      for (CardResult mark : List.of(CardResult.REST, CardResult.POSTPONED, CardResult.FAILED)) {
        CardDay marked = new CardDay(SEVEN_PM, 60, List.of("i1"), null, OCT_17, sessions, mark);
        assertThat(CardResultJudge.getCardResult(marked, afterCard)).isEqualTo(mark);
      }
    }

    @Test
    @DisplayName("증빙 없는 사후 입력뿐이면 자기 보고, 증빙이 있으면 일반 실행과 똑같이 본다")
    void selfReportResult() {
      assertThat(CardResultJudge.getCardResult(day(List.of(selfReport(false))), afterCard))
          .isEqualTo(CardResult.SELF_REPORT);
      assertThat(CardResultJudge.getCardResult(day(List.of(selfReport(true))), afterCard))
          .isEqualTo(CardResult.DONE);
    }

    @Test
    @DisplayName("시작 시각이 없는 옛 카드: 세션이 없으면 그날(새벽 4시 전)까지 null, 다음 날 미처리 · 세션이 있으면 실행")
    void noStartTime() {
      CardDay empty = new CardDay(null, 60, List.of("i1"), null, OCT_17, List.of(), null);
      assertThat(CardResultJudge.getCardResult(empty, kst(17, 23, 0))).isNull();
      // 10/18 03:59 는 아직 10/17 입니다.
      assertThat(CardResultJudge.getCardResult(empty, kst(18, 3, 59))).isNull();
      assertThat(CardResultJudge.getCardResult(empty, kst(18, 4, 0)))
          .isEqualTo(CardResult.UNHANDLED);

      CardDay studiedLate =
          new CardDay(
              null,
              60,
              List.of("i1"),
              null,
              OCT_17,
              List.of(timer(kst(17, 22, 0), kst(17, 23, 0))),
              null);
      assertThat(CardResultJudge.getCardResult(studiedLate, kst(17, 23, 0)))
          .isEqualTo(CardResult.DONE);
    }

    @Test
    @DisplayName("01:30 카드는 그 날짜의 밤(달력으로 다음 날 01:30)이 예정 시각이다")
    void overnightCard() {
      CardDay overnight =
          new CardDay(
              LocalTime.of(1, 30),
              60,
              List.of("i1"),
              null,
              OCT_17,
              List.of(timer(kst(18, 1, 35), kst(18, 2, 30))),
              null);
      assertThat(CardResultJudge.getCardResult(overnight, kst(18, 3, 0)))
          .isEqualTo(CardResult.DONE);
      CardDay notYet =
          new CardDay(LocalTime.of(1, 30), 60, List.of("i1"), null, OCT_17, List.of(), null);
      // 10/17 23:00 에는 아직 10/18 01:30 카드가 끝나지 않았습니다.
      assertThat(CardResultJudge.getCardResult(notYet, kst(17, 23, 0))).isNull();
    }

    @Test
    @DisplayName("증빙이 꼭 필요했는데 증빙 없이 끝낸 세션은 없는 것으로 본다 (관리 4단계)")
    void proofRequired() {
      Session withoutProof =
          new Session(
              kst(17, 19, 0), 60 * MINUTE, SessionMode.NORMAL, false, Set.of("i1"), false, true);
      Session withProof =
          new Session(
              kst(17, 19, 0), 60 * MINUTE, SessionMode.NORMAL, false, Set.of("i1"), true, true);

      // [보통 세션, 증빙 필요인데 없음] → [보통 세션] 만 남음 → 늦게 시작한 보통 세션으로 판정
      CardDay mixed = day(List.of(timer(kst(17, 19, 30), kst(17, 20, 0)), withoutProof));
      assertThat(CardResultJudge.getCardResult(mixed, afterCard)).isEqualTo(CardResult.LATE);
      // 그것뿐이면 세션이 없는 것과 같아서 카드 시간이 끝났으면 미처리
      assertThat(CardResultJudge.getCardResult(day(List.of(withoutProof)), afterCard))
          .isEqualTo(CardResult.UNHANDLED);
      assertThat(CardResultJudge.getRunRate(day(List.of(withoutProof)))).isZero();
      assertThat(CardResultJudge.getCheckedCount(day(List.of(withoutProof)))).isZero();
      // 증빙이 있으면 그대로 실행
      assertThat(CardResultJudge.getCardResult(day(List.of(withProof)), afterCard))
          .isEqualTo(CardResult.DONE);
    }
  }

  @Test
  @DisplayName("미실행은 미루기 · 실패 · 미처리 셋뿐이다")
  void isMiss() {
    assertThat(CardResult.POSTPONED.isMiss()).isTrue();
    assertThat(CardResult.FAILED.isMiss()).isTrue();
    assertThat(CardResult.UNHANDLED.isMiss()).isTrue();
    for (CardResult result :
        List.of(
            CardResult.DONE,
            CardResult.LATE,
            CardResult.ENDED_EARLY,
            CardResult.REST,
            CardResult.SELF_REPORT)) {
      assertThat(result.isMiss()).as(result.name()).isFalse();
    }
  }

  @Test
  @DisplayName(
      "isStartedOnTime: 19:05 시작 → true, 19:20 시작 → false, 세션 없음 · 사후 입력뿐 · 시작 시각 없음 → null")
  void startedOnTime() {
    assertThat(CardResultJudge.isStartedOnTime(day(List.of(timer(kst(17, 19, 5), kst(17, 20, 0))))))
        .isTrue();
    assertThat(
            CardResultJudge.isStartedOnTime(day(List.of(timer(kst(17, 19, 20), kst(17, 20, 0))))))
        .isFalse();
    assertThat(CardResultJudge.isStartedOnTime(day(List.of()))).isNull();
    assertThat(CardResultJudge.isStartedOnTime(day(List.of(selfReport(true))))).isNull();
    CardDay noStartTime =
        new CardDay(
            null,
            60,
            List.of("i1"),
            null,
            OCT_17,
            List.of(timer(kst(17, 19, 5), kst(17, 20, 0))),
            null);
    assertThat(CardResultJudge.isStartedOnTime(noStartTime)).isNull();
  }

  @Test
  @DisplayName("getRunRate: 60분 카드에 45분 → 0.75, 70분 → 1, 증빙 없는 사후 입력 → 0.5, 증빙 있는 사후 입력 → 시간대로")
  void runRate() {
    assertThat(CardResultJudge.getRunRate(day(List.of(studied(45))))).isEqualTo(0.75);
    assertThat(CardResultJudge.getRunRate(day(List.of(studied(70))))).isEqualTo(1.0);
    assertThat(CardResultJudge.getRunRate(day(List.of(selfReport(false))))).isEqualTo(0.5);
    assertThat(CardResultJudge.getRunRate(day(List.of(selfReport(true))))).isEqualTo(1.0);
    // 세션 여러 개는 더합니다. (20분 + 25분 = 45분)
    assertThat(CardResultJudge.getRunRate(day(List.of(studied(20), studied(25))))).isEqualTo(0.75);
    // 계획 시간이 0분이면 0
    CardDay zero = new CardDay(SEVEN_PM, 0, List.of("i1"), null, OCT_17, List.of(), null);
    assertThat(CardResultJudge.getRunRate(zero)).isZero();
  }

  @Test
  @DisplayName("체크한 항목은 세션이 여러 개여도 한 번씩만, 지금 체크리스트에 있는 것만 센다")
  void checkedCount() {
    CardDay twoSessions =
        dayWithChecklist(3, null, List.of(checked(30, "i1", "i2"), checked(30, "i2", "지운 항목")));
    assertThat(CardResultJudge.getCheckedCount(twoSessions)).isEqualTo(2);
    assertThat(CardResultJudge.getChecklistRate(twoSessions)).isEqualTo(2.0 / 3);
    // 체크리스트가 비어 있으면 달성률 1
    CardDay noChecklist = dayWithChecklist(0, null, List.of(studied(60)));
    assertThat(CardResultJudge.getChecklistRate(noChecklist)).isEqualTo(1.0);
  }

  @Test
  @DisplayName("getPlanAxis: 3/3 · 실행률 1 → 다 함, 2/3 · 0.9 → 계획 과대, 2/3 · 0.5 → 실행 축, 세션 없음 → null")
  void planAxis() {
    assertThat(
            CardResultJudge.getPlanAxis(
                dayWithChecklist(3, null, List.of(checked(60, "i1", "i2", "i3")))))
        .isEqualTo(PlanAxis.COMPLETE);
    assertThat(
            CardResultJudge.getPlanAxis(
                dayWithChecklist(3, null, List.of(checked(54, "i1", "i2")))))
        .isEqualTo(PlanAxis.OVER_PLANNED);
    assertThat(
            CardResultJudge.getPlanAxis(
                dayWithChecklist(3, null, List.of(checked(30, "i1", "i2")))))
        .isEqualTo(PlanAxis.EXECUTION);
    assertThat(CardResultJudge.getPlanAxis(dayWithChecklist(3, null, List.of()))).isNull();
    // 실행률이 딱 0.8(48분) 이면 계획 과대
    assertThat(CardResultJudge.getPlanAxis(dayWithChecklist(3, null, List.of(checked(48, "i1")))))
        .isEqualTo(PlanAxis.OVER_PLANNED);
  }

  @Test
  @DisplayName("getPredictionGap: 5개 · 예상 없음 · 3개 체크 → -2, 5개 · 예상 3개 · 3개 체크 → 0, 세션 없음 → null")
  void predictionGap() {
    assertThat(
            CardResultJudge.getPredictionGap(
                dayWithChecklist(5, null, List.of(checked(60, "i1", "i2", "i3")))))
        .isEqualTo(-2);
    assertThat(
            CardResultJudge.getPredictionGap(
                dayWithChecklist(5, 3, List.of(checked(60, "i1", "i2", "i3")))))
        .isZero();
    assertThat(CardResultJudge.getPredictionGap(dayWithChecklist(5, 3, List.of()))).isNull();
  }

  @Test
  @DisplayName("getPlanPattern: 평균 0.99 → 느슨, 0.8 → 적정, 0.54 → 과대, 2개뿐 → null")
  void planPattern() {
    assertThat(CardResultJudge.getPlanPattern(List.of(1.0, 1.0, 1.0, 1.0, 0.95)))
        .isEqualTo(PlanPattern.LOOSE);
    assertThat(CardResultJudge.getPlanPattern(List.of(0.8, 0.7, 0.9, 0.8, 0.8)))
        .isEqualTo(PlanPattern.FIT);
    assertThat(CardResultJudge.getPlanPattern(List.of(0.5, 0.4, 0.6, 0.5, 0.7)))
        .isEqualTo(PlanPattern.OVER);
    assertThat(CardResultJudge.getPlanPattern(List.of(1.0, 1.0))).isNull();
    // 60~70% 는 기획서의 적정(70~90%) 밖이지만 과대라고 할 만큼은 아니라 적정입니다. (FE 와 같음)
    assertThat(CardResultJudge.getPlanPattern(List.of(0.65, 0.65, 0.65, 0.65, 0.65)))
        .isEqualTo(PlanPattern.FIT);
  }
}
