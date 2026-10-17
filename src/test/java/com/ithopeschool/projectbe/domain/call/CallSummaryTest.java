package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.call.CallSummary.CheckInItem;
import com.ithopeschool.projectbe.domain.call.CallSummary.EvaluationItem;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.summary.DaySummary.DayPlan;
import com.ithopeschool.projectbe.domain.summary.DaySummary.DaySession;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 스프링을 띄우지 않는 단위 테스트입니다. 지난 통화 요약 카드를 만드는 규칙(이슈 #29)을 봅니다.
// "FE 예" 로 시작하는 것은 FE 함수(src/lib/callSummary.ts) 주석의 예시를 그대로 옮긴 것입니다.
class CallSummaryTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private static LocalDate october(int day) {
    return LocalDate.of(2026, 10, day);
  }

  // 10월 day일 저녁 7시에 시작해서 minutes 분 공부한 기록을 만듭니다. (일시정지 없음)
  private static DaySession study(int day, int minutes) {
    Instant startedAt = october(day).atTime(19, 0).atZone(SEOUL).toInstant();
    return new DaySession(startedAt, startedAt.plusSeconds(minutes * 60L), 0, 0);
  }

  // 예상 순공 120분만 적은 체크인을 만듭니다.
  private static CheckInItem checkIn(int day) {
    return new CheckInItem(october(day), 120, null, null, null, null);
  }

  // ─── 어제 요약 한 줄 ───

  @Test
  @DisplayName("FE 예) 계획과 공부 기록이 모두 없으면 없다고 말한다")
  void headlineWithoutPlanAndStudy() {
    assertThat(CallSummary.headlineOf(october(4), List.of(), List.of()))
        .isEqualTo("어제는 계획도 공부 기록도 없어요");
  }

  @Test
  @DisplayName("FE 예) 계획은 없고 공부만 했으면 순공시간만 말한다")
  void headlineWithStudyOnly() {
    assertThat(CallSummary.headlineOf(october(4), List.of(), List.of(study(4, 40))))
        .isEqualTo("어제 순공 40분 (세워 둔 계획은 없었어요)");
  }

  @Test
  @DisplayName("FE 예) 계획이 있으면 순공시간과 달성률을 말한다")
  void headlineWithPlan() {
    List<DayPlan> plans = List.of(new DayPlan(october(4), 240));

    // 190 ÷ 240 = 79.1…% → 79
    assertThat(CallSummary.headlineOf(october(4), plans, List.of(study(4, 190))))
        .isEqualTo("어제 순공 3시간 10분, 계획의 79%");
  }

  @Test
  @DisplayName("계획은 있는데 공부를 안 했으면 '1분 미만, 계획의 0%' 라고 적는다")
  void headlineWithPlanButNoStudy() {
    List<DayPlan> plans = List.of(new DayPlan(october(4), 60));

    assertThat(CallSummary.headlineOf(october(4), plans, List.of()))
        .isEqualTo("어제 순공 1분 미만, 계획의 0%");
  }

  @Test
  @DisplayName("계획의 목표가 0분이라 달성률을 모르는 날은 계획 없이 공부한 날처럼 적는다")
  void headlineWithZeroTargetPlan() {
    List<DayPlan> plans = List.of(new DayPlan(october(4), 0));

    assertThat(CallSummary.headlineOf(october(4), plans, List.of(study(4, 60))))
        .isEqualTo("어제 순공 1시간 (세워 둔 계획은 없었어요)");
  }

  // ─── 오늘의 약속 ───

  @Test
  @DisplayName("약속은 '만약 ~ → ~' 한 문장이고, 앞뒤 빈칸은 뗀다")
  void promiseSentence() {
    assertThat(CallSummary.promiseOf("학원에서 돌아오면", "영어 단어 20개"))
        .isEqualTo("만약 학원에서 돌아오면 → 영어 단어 20개");
    assertThat(CallSummary.promiseOf("  학원에서 돌아오면 ", " 영어 단어 20개  "))
        .isEqualTo("만약 학원에서 돌아오면 → 영어 단어 20개");
  }

  @Test
  @DisplayName("둘 중 하나라도 비어 있으면 약속이 아니다")
  void halfPromiseIsNotAPromise() {
    assertThat(CallSummary.promiseOf("학원에서 돌아오면", null)).isNull();
    assertThat(CallSummary.promiseOf(null, "영어 단어 20개")).isNull();
    assertThat(CallSummary.promiseOf("학원에서 돌아오면", "   ")).isNull();
    assertThat(CallSummary.promiseOf("", "")).isNull();
  }

  // ─── 못 채운 이유 ───

  @Test
  @DisplayName("못 채운 이유는 버튼의 글자로 바꾼다 (7개 모두)")
  void missReasonLabels() {
    assertThat(CallSummary.missReasonLabel(MissReason.TIRED)).isEqualTo("피곤·수면");
    assertThat(CallSummary.missReasonLabel(MissReason.EVENT)).isEqualTo("약속·행사");
    assertThat(CallSummary.missReasonLabel(MissReason.PHONE)).isEqualTo("폰·SNS");
    assertThat(CallSummary.missReasonLabel(MissReason.TOO_MUCH)).isEqualTo("계획 과다");
    assertThat(CallSummary.missReasonLabel(MissReason.UNWILLING)).isEqualTo("하기 싫음");
    assertThat(CallSummary.missReasonLabel(MissReason.SICK)).isEqualTo("아픔");
    assertThat(CallSummary.missReasonLabel(MissReason.ETC)).isEqualTo("기타");
  }

  @Test
  @DisplayName("'기타' 에 적은 한 줄만 괄호로 붙인다. 다른 이유에는 적은 글을 붙이지 않는다")
  void noteIsShownOnlyForEtc() {
    assertThat(CallSummary.missReasonTextOf(MissReason.ETC, "학원 보강")).isEqualTo("기타 (학원 보강)");
    assertThat(CallSummary.missReasonTextOf(MissReason.ETC, "  학원 보강 ")).isEqualTo("기타 (학원 보강)");
    assertThat(CallSummary.missReasonTextOf(MissReason.ETC, "  ")).isEqualTo("기타");
    assertThat(CallSummary.missReasonTextOf(MissReason.ETC, null)).isEqualTo("기타");
    assertThat(CallSummary.missReasonTextOf(MissReason.TIRED, "어제 늦게 잠")).isEqualTo("피곤·수면");
    assertThat(CallSummary.missReasonTextOf(null, "학원 보강")).isNull();
  }

  // ─── 목록 ───

  @Test
  @DisplayName("FE 예) 체크인이 10/3, 10/5, 10/4 에 있으면 카드는 10/5, 10/4, 10/3 순서다")
  void recentCardComesFirst() {
    List<CallSummary> cards =
        CallSummary.listOf(
            List.of(checkIn(3), checkIn(5), checkIn(4)), List.of(), List.of(), List.of());

    assertThat(cards)
        .extracting(CallSummary::date)
        .containsExactly(october(5), october(4), october(3));
  }

  @Test
  @DisplayName("카드의 어제 요약은 체크인한 날의 전날 기록으로 만든다")
  void headlineIsOfTheDayBefore() {
    // 10/5 에 통화했습니다. 10/4 에 계획 240분, 공부 190분 / 10/5(통화한 날)의 공부 30분은 쓰지 않습니다.
    List<DayPlan> plans = List.of(new DayPlan(october(4), 240));
    List<DaySession> sessions = List.of(study(4, 190), study(5, 30));

    CallSummary card = CallSummary.listOf(List.of(checkIn(5)), plans, sessions, List.of()).get(0);

    assertThat(card.headline()).isEqualTo("어제 순공 3시간 10분, 계획의 79%");
  }

  @Test
  @DisplayName("체크인에 적은 약속·못 채운 이유·예상 시간이 카드에 담긴다")
  void cardFromAFullCheckIn() {
    CheckInItem full =
        new CheckInItem(october(5), 150, MissReason.PHONE, null, "학원에서 돌아오면", "영어 단어 20개");

    CallSummary card = CallSummary.listOf(List.of(full), List.of(), List.of(), List.of()).get(0);

    assertThat(card)
        .isEqualTo(
            new CallSummary(
                october(5),
                "어제는 계획도 공부 기록도 없어요",
                "만약 학원에서 돌아오면 → 영어 단어 20개",
                "폰·SNS",
                150,
                null,
                null));
  }

  @Test
  @DisplayName("그날 평가가 있으면 AI 한마디와 관리 강도를 채우고, 없으면 비워 둔다")
  void evaluationFillsCommentAndCareLevel() {
    List<EvaluationItem> evaluations =
        List.of(
            new EvaluationItem(october(5), "어제보다 첫 시작이 40분 빨랐어!", 1),
            // 한마디가 빈칸뿐인 평가: 한마디는 없는 것으로 보지만 관리 강도는 채웁니다.
            new EvaluationItem(october(4), "   ", 0));

    List<CallSummary> cards =
        CallSummary.listOf(
            List.of(checkIn(5), checkIn(4), checkIn(3)), List.of(), List.of(), evaluations);

    assertThat(cards.get(0).aiComment()).isEqualTo("어제보다 첫 시작이 40분 빨랐어!");
    assertThat(cards.get(0).careLevel()).isEqualTo(1);
    assertThat(cards.get(1).aiComment()).isNull();
    assertThat(cards.get(1).careLevel()).isEqualTo(0);
    assertThat(cards.get(2).aiComment()).isNull();
    assertThat(cards.get(2).careLevel()).isNull();
  }

  @Test
  @DisplayName("체크인이 없으면 카드도 없다")
  void noCheckInsMeansNoCards() {
    assertThat(CallSummary.listOf(List.of(), List.of(), List.of(study(4, 60)), List.of()))
        .isEmpty();
  }
}
