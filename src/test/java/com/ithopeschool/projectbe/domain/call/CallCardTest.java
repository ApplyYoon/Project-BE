package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.call.CallCard.CardCheckIn;
import com.ithopeschool.projectbe.domain.call.CallCard.CardSession;
import com.ithopeschool.projectbe.domain.call.dto.CallSessionRequest;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.focus.AwaySummary.AwayRecord;
import com.ithopeschool.projectbe.domain.focus.AwaySummary.UnlockRecord;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactPlan;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 스프링을 띄우지 않는 단위 테스트입니다. 서버의 기록으로 통화의 "학생 카드" 를 만드는 규칙(이슈 #28)을 봅니다.
// 오늘은 2026-10-07(수) 저녁 8시입니다. (FE 함수 buildCallCard 주석의 예시와 같은 날)
class CallCardTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

  private static final long MINUTE = 60_000;

  // 한국 시간 10월 "day일 시:분" 을 순간(Instant)으로 바꾸는 도우미입니다.
  private static Instant at(int day, int hour, int minute) {
    return ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, SEOUL).toInstant();
  }

  private static final Instant NOW = at(7, 20, 0);

  private static LocalDate october(int day) {
    return LocalDate.of(2026, 10, day);
  }

  // 10월 day일의 계획을 만듭니다. hour 가 -1 이면 시작 시각을 정하지 않은 계획입니다.
  private static FactPlan plan(long id, int day, int targetMinutes, int hour) {
    return new FactPlan(id, october(day), targetMinutes, hour < 0 ? null : LocalTime.of(hour, 0));
  }

  // 계획·목표 없이 10월 day일 hour시에 시작해서 minutes 분 공부한 기록을 만듭니다.
  private static CardSession study(int day, int hour, int minutes) {
    Instant startedAt = at(day, hour, 0);
    return new CardSession(null, startedAt, startedAt.plusMillis(minutes * MINUTE), 0, 0, 0);
  }

  // 목표를 정하고 한 기록을 만듭니다. 순공이 목표 이상이면 완주입니다.
  private static CardSession timer(int day, int hour, int minutes, int targetMinutes) {
    Instant startedAt = at(day, hour, 0);
    return new CardSession(
        null, startedAt, startedAt.plusMillis(minutes * MINUTE), 0, 0, targetMinutes);
  }

  private static AwayRecord away(int day, int hour, int minute, int minutes) {
    return new AwayRecord(at(day, hour, minute), minutes * MINUTE);
  }

  private static CallSessionRequest.Card card(
      List<FactPlan> plans, List<CardSession> sessions, List<CardCheckIn> checkIns) {
    return CallCard.of(plans, sessions, checkIns, List.of(), List.of(), TODAY, NOW);
  }

  // ─── FE 예시 ───

  @Test
  @DisplayName("FE 예) 어제 계획 240분에 190분 공부, 오늘 계획 3개, 최근 기분 3·2·4, 4일 연속 공부")
  void feExampleCard() {
    List<FactPlan> plans =
        List.of(plan(1, 6, 240, -1), plan(2, 7, 60, 21), plan(3, 7, 60, 19), plan(4, 7, 60, -1));
    // 10/03 ~ 10/06 에 매일 공부했고(4일 연속), 오늘은 아직 안 했습니다. 어제는 190분입니다.
    List<CardSession> sessions =
        List.of(study(3, 19, 60), study(4, 19, 60), study(5, 19, 60), study(6, 10, 190));
    List<CardCheckIn> checkIns =
        List.of(
            new CardCheckIn(october(5), 3, null),
            new CardCheckIn(october(6), 2, null),
            new CardCheckIn(october(7), 4, MissReason.PHONE));
    // 어제 3번 나갔고 그중 2번이 밤 9시대입니다. 집중 모드는 스스로 1번 풀었습니다. (자동으로 풀린 것은 세지 않음)
    List<AwayRecord> aways = List.of(away(6, 21, 10, 3), away(6, 21, 40, 2), away(6, 19, 5, 10));
    List<UnlockRecord> unlocks =
        List.of(
            new UnlockRecord(at(6, 21, 45), "급한 연락", false),
            new UnlockRecord(at(6, 22, 0), "지금은 쉬어 가는 게 좋아서 집중 모드를 풀었어요.", true));

    CallSessionRequest.Card card =
        CallCard.of(plans, sessions, checkIns, aways, unlocks, TODAY, NOW);

    assertThat(card)
        .isEqualTo(
            new CallSessionRequest.Card(
                190,
                240,
                "phone",
                3,
                180,
                "19:00",
                // 이번 주(월 ~ 수): 계획 240 + 180 = 420분, 순공 60 + 190 = 250분 → 59.5…% → 59
                59,
                // 목표가 있는 타이머가 없어서 완주율과 시작 지연은 모릅니다.
                null,
                null,
                List.of(3, 2, 4),
                4,
                3,
                21,
                1));
  }

  // ─── 카드가 없는 경우 ───

  @Test
  @DisplayName("계획도 공부 기록도 체크인도 없으면 카드가 없다 (처음 온 사람)")
  void noRecordsMeansNoCard() {
    assertThat(card(List.of(), List.of(), List.of())).isNull();
    // 나간 기록만 있는 것은 "기록이 있다" 로 치지 않습니다. (FE 와 같습니다)
    assertThat(
            CallCard.of(
                List.of(), List.of(), List.of(), List.of(away(6, 21, 0, 5)), List.of(), TODAY, NOW))
        .isNull();
  }

  @Test
  @DisplayName("체크인 하나만 있어도 카드는 만들고, 모르는 숫자는 0 이 아니라 null 이다")
  void onlyACheckIn() {
    CallSessionRequest.Card card =
        card(List.of(), List.of(), List.of(new CardCheckIn(october(7), 5, null)));

    assertThat(card)
        .isEqualTo(
            new CallSessionRequest.Card(
                0, 0, null, 0, 0, null, null, null, null, List.of(5), 0, 0, null, 0));
  }

  // ─── 이번 주 숫자 ───

  @Test
  @DisplayName("이번 주 완주율은 목표가 있는 타이머가 5번 이상일 때만 넣는다")
  void completionRateNeedsFiveTimers() {
    // 월 ~ 수에 목표 30분 타이머: 30분(완주) · 30분(완주) · 10분(멈춤) · 30분(완주) → 4번
    List<CardSession> four = new ArrayList<>();
    four.add(timer(5, 19, 30, 30));
    four.add(timer(5, 21, 30, 30));
    four.add(timer(6, 19, 10, 30));
    four.add(timer(6, 21, 30, 30));
    assertThat(card(List.of(), four, List.of()).weekCompletionRate()).isNull();

    // 한 번 더(멈춤) 하면 5번 중 3번 완주 → 60%
    List<CardSession> five = new ArrayList<>(four);
    five.add(timer(7, 10, 10, 30));
    assertThat(card(List.of(), five, List.of()).weekCompletionRate()).isEqualTo(60);
  }

  @Test
  @DisplayName("이번 주 숫자는 월요일부터 오늘까지만 본다 (지난주 일요일의 기록은 넣지 않는다)")
  void weekStartsOnMonday() {
    // 일요일(10/04)에 계획 100분·공부 100분, 월요일(10/05)에 계획 100분·공부 50분
    List<FactPlan> plans = List.of(plan(1, 4, 100, -1), plan(2, 5, 100, -1));
    List<CardSession> sessions = List.of(study(4, 19, 100), study(5, 19, 50));

    assertThat(card(plans, sessions, List.of()).weekPlanRate()).isEqualTo(50);
  }

  @Test
  @DisplayName("계획보다 훨씬 많이 공부해도 계획 대비 실제는 100 까지만 담는다 (요청 검사가 받는 범위)")
  void planRateIsClampedToHundred() {
    List<FactPlan> plans = List.of(plan(1, 6, 30, -1));
    List<CardSession> sessions = List.of(study(6, 19, 120));

    CallSessionRequest.Card card = card(plans, sessions, List.of());

    assertThat(card.weekPlanRate()).isEqualTo(100);
    // 어제의 숫자는 그대로입니다.
    assertThat(card.yesterdayStudyMinutes()).isEqualTo(120);
    assertThat(card.yesterdayPlanMinutes()).isEqualTo(30);
  }

  @Test
  @DisplayName("이번 주 평균 시작 지연은 계획으로 켠 첫 타이머가 얼마나 늦었는지다")
  void weekStartDelay() {
    // 월요일 19:00 계획을 19:20 에 시작(20분 늦음), 화요일 19:00 계획을 19:10 에 시작(10분 늦음) → 평균 15분
    List<FactPlan> plans = List.of(plan(1, 5, 60, 19), plan(2, 6, 60, 19));
    List<CardSession> sessions =
        List.of(
            new CardSession(1L, at(5, 19, 20), at(5, 20, 0), 0, 0, 0),
            new CardSession(2L, at(6, 19, 10), at(6, 20, 0), 0, 0, 0));

    assertThat(card(plans, sessions, List.of()).weekStartDelayMinutes()).isEqualTo(15);
  }

  // ─── 오늘의 계획 ───

  @Test
  @DisplayName("오늘 첫 시작 시각은 시각을 정한 계획 중 가장 이른 것이고, 없으면 null 이다")
  void todayFirstStart() {
    List<FactPlan> withTimes = List.of(plan(1, 7, 30, 21), plan(2, 7, 30, -1), plan(3, 7, 30, 9));
    assertThat(card(withTimes, List.of(), List.of()).todayFirstStart()).isEqualTo("09:00");

    List<FactPlan> withoutTimes = List.of(plan(1, 7, 30, -1));
    CallSessionRequest.Card card = card(withoutTimes, List.of(), List.of());
    assertThat(card.todayFirstStart()).isNull();
    assertThat(card.todayPlanCount()).isEqualTo(1);
    assertThat(card.todayPlanMinutes()).isEqualTo(30);
  }

  // ─── 체크인 ───

  @Test
  @DisplayName("못 채운 이유는 오늘 체크인에서만 꺼낸다 (어제 체크인의 이유는 쓰지 않는다)")
  void missReasonComesFromTodaysCheckIn() {
    List<CardCheckIn> onlyYesterday = List.of(new CardCheckIn(october(6), 3, MissReason.TIRED));
    assertThat(card(List.of(), List.of(), onlyYesterday).yesterdayMissReason()).isNull();

    List<CardCheckIn> both =
        List.of(
            new CardCheckIn(october(6), 3, MissReason.TIRED),
            new CardCheckIn(october(7), 3, MissReason.TOO_MUCH));
    assertThat(card(List.of(), List.of(), both).yesterdayMissReason()).isEqualTo("tooMuch");
  }

  @Test
  @DisplayName("못 채운 이유 7개는 FE 와 같은 글자로 담는다")
  void missReasonCodes() {
    assertThat(CallCard.missReasonCode(MissReason.TIRED)).isEqualTo("tired");
    assertThat(CallCard.missReasonCode(MissReason.EVENT)).isEqualTo("event");
    assertThat(CallCard.missReasonCode(MissReason.PHONE)).isEqualTo("phone");
    assertThat(CallCard.missReasonCode(MissReason.TOO_MUCH)).isEqualTo("tooMuch");
    assertThat(CallCard.missReasonCode(MissReason.UNWILLING)).isEqualTo("unwilling");
    assertThat(CallCard.missReasonCode(MissReason.SICK)).isEqualTo("sick");
    assertThat(CallCard.missReasonCode(MissReason.ETC)).isEqualTo("etc");
    assertThat(CallCard.missReasonCode(null)).isNull();
  }

  @Test
  @DisplayName("최근 기분은 오늘 포함 7일 치를 오래된 날부터 담고, 1 ~ 5 가 아닌 값은 뺀다")
  void recentMoods() {
    List<CardCheckIn> checkIns =
        List.of(
            new CardCheckIn(october(7), 4, null),
            // 7일보다 전(9/30)과 범위 밖 기분(0, 6)은 담지 않습니다.
            new CardCheckIn(LocalDate.of(2026, 9, 30), 1, null),
            new CardCheckIn(october(1), 2, null),
            new CardCheckIn(october(3), 0, null),
            new CardCheckIn(october(4), 6, null),
            new CardCheckIn(october(5), 5, null));

    assertThat(card(List.of(), List.of(), checkIns).recentMoods()).containsExactly(2, 5, 4);
  }

  // ─── 집중 기록 ───

  @Test
  @DisplayName("나간 기록과 집중 모드를 푼 기록은 어제 것만 센다")
  void focusRecordsOfYesterdayOnly() {
    List<AwayRecord> aways = List.of(away(5, 21, 0, 5), away(6, 15, 0, 5), away(7, 10, 0, 5));
    List<UnlockRecord> unlocks =
        List.of(
            new UnlockRecord(at(5, 21, 0), "쉬는 시간", false),
            new UnlockRecord(at(6, 15, 30), "쉬는 시간", false),
            new UnlockRecord(at(6, 16, 30), "그냥 하기 싫어", false),
            new UnlockRecord(at(7, 10, 0), "급한 연락", false));

    CallSessionRequest.Card card =
        CallCard.of(List.of(), List.of(study(6, 15, 30)), List.of(), aways, unlocks, TODAY, NOW);

    assertThat(card.yesterdayAwayCount()).isEqualTo(1);
    assertThat(card.yesterdayAwayPeakHour()).isEqualTo(15);
    assertThat(card.yesterdayUnlockCount()).isEqualTo(2);
  }

  // ─── 통화 대본에 넘길 수 있는 모양인지 ───

  @Test
  @DisplayName("만든 카드는 FE 가 보낼 때와 같은 검사를 통과하고, 통화 대본을 만들 수 있다")
  void cardPassesTheRequestValidationAndMakesAScript() {
    // 범위를 넘기 쉬운 값들: 하루 30시간 공부, 계획 60개, 계획 3000분, 나간 기록 300번
    List<FactPlan> plans = new ArrayList<>();
    for (long id = 1; id <= 60; id++) {
      plans.add(plan(id, 7, 50, 19));
    }
    plans.add(plan(100, 6, 3000, -1));
    List<CardSession> sessions = List.of(study(6, 4, 30 * 60));
    List<AwayRecord> aways = new ArrayList<>();
    for (int count = 0; count < 300; count++) {
      aways.add(away(6, 21, 0, 1));
    }

    CallSessionRequest.Card card =
        CallCard.of(plans, sessions, List.of(), aways, List.of(), TODAY, NOW);

    assertThat(card.yesterdayStudyMinutes()).isEqualTo(1440);
    assertThat(card.yesterdayPlanMinutes()).isEqualTo(1440);
    assertThat(card.todayPlanCount()).isEqualTo(50);
    assertThat(card.todayPlanMinutes()).isEqualTo(1440);
    assertThat(card.yesterdayAwayCount()).isEqualTo(200);
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();
      assertThat(validator.validate(card)).isEmpty();
    }
    assertThat(CallScript.instructions(2, 3, card)).contains("[오늘의 사실]");
  }
}
