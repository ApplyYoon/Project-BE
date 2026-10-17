package com.ithopeschool.projectbe.domain.care;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.ithopeschool.projectbe.domain.card.CardPriority;
import com.ithopeschool.projectbe.domain.cardresult.CardResult;
import com.ithopeschool.projectbe.domain.cardresult.JudgedCardDay;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 재발 신호를 FE src/lib/relapseSignal.ts 주석의 예시 그대로 확인합니다. (이슈 #230)
// 예시는 모두 today = since = 2026-10-17 입니다.
class RelapseSignalTest {

  private static final LocalDate OCT_17 = LocalDate.of(2026, 10, 17);
  private static final Long A = 1L;
  private static final Long B = 2L;

  private static LocalDate oct(int day) {
    return LocalDate.of(2026, 10, day);
  }

  // 루틴(반복) · 유연 카드의 하루치
  private static JudgedCardDay routine(Long cardId, LocalDate date, CardResult result) {
    return new JudgedCardDay(
        cardId,
        date,
        true,
        CardPriority.FLEXIBLE,
        result,
        result.isMiss() ? 0 : 1,
        null,
        null,
        null);
  }

  // 1회성 카드의 하루치 (우선순위 · 실행률 · 원인을 정함)
  private static JudgedCardDay oneOff(
      Long cardId,
      LocalDate date,
      CardPriority priority,
      CardResult result,
      double runRate,
      MissReason missReason) {
    return new JudgedCardDay(
        cardId, date, false, priority, result, runRate, missReason, null, null);
  }

  // from ~ to 매일 실행률 rate 인 1회성 실행 카드 (카드 번호는 날짜마다 다름)
  private static List<JudgedCardDay> daily(LocalDate from, LocalDate to, double rate) {
    List<JudgedCardDay> days = new ArrayList<>();
    long id = 100;
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      days.add(oneOff(id++, date, CardPriority.FLEXIBLE, CardResult.DONE, rate, null));
    }
    return days;
  }

  @Test
  @DisplayName("① 루틴 카드 A: 10/15 실패, 10/17 미루기 → routineTwice, routineCardIds [A]")
  void routineTwice() {
    RelapseSignal signal =
        RelapseSignal.judge(
            List.of(
                routine(A, oct(15), CardResult.FAILED), routine(A, oct(17), CardResult.POSTPONED)),
            OCT_17,
            OCT_17);

    assertThat(signal.hasSignal()).isTrue();
    assertThat(signal.reasons()).containsExactly(RelapseReason.ROUTINE_TWICE);
    assertThat(signal.routineCardIds()).containsExactly(A);
  }

  @Test
  @DisplayName("① 루틴 카드 A: 10/15 실패, 10/17 실행 → 신호 없음 (연속 아님)")
  void routineNotTwice() {
    RelapseSignal signal =
        RelapseSignal.judge(
            List.of(routine(A, oct(15), CardResult.FAILED), routine(A, oct(17), CardResult.DONE)),
            OCT_17,
            OCT_17);

    assertThat(signal.hasSignal()).isFalse();
    assertThat(signal.reasons()).isEmpty();
    assertThat(signal.routineCardIds()).isEmpty();
  }

  @Test
  @DisplayName("① 루틴 카드 A: 10/13 실패, 10/15 실패 (since 10/17) → 신호 없음 (어제까지 판정한 일)")
  void routineAlreadyJudged() {
    RelapseSignal signal =
        RelapseSignal.judge(
            List.of(routine(A, oct(13), CardResult.FAILED), routine(A, oct(15), CardResult.FAILED)),
            OCT_17,
            OCT_17);

    assertThat(signal.hasSignal()).isFalse();
  }

  @Test
  @DisplayName("① 미처리(unhandled)도 미실행으로 센다, 한 번뿐이면 신호가 아니다")
  void routineUnhandled() {
    assertThat(
            RelapseSignal.judge(
                    List.of(
                        routine(A, oct(15), CardResult.UNHANDLED),
                        routine(A, oct(17), CardResult.FAILED)),
                    OCT_17,
                    OCT_17)
                .reasons())
        .containsExactly(RelapseReason.ROUTINE_TWICE);
    assertThat(
            RelapseSignal.judge(List.of(routine(A, oct(17), CardResult.FAILED)), OCT_17, OCT_17)
                .hasSignal())
        .isFalse();
  }

  @Test
  @DisplayName("③ 절대 카드 B: 10/17 실패 → absoluteMiss, since 전의 일이면 신호 없음")
  void absoluteMiss() {
    List<JudgedCardDay> days =
        List.of(oneOff(B, OCT_17, CardPriority.ABSOLUTE, CardResult.FAILED, 0, MissReason.PHONE));

    RelapseSignal signal = RelapseSignal.judge(days, OCT_17, OCT_17);
    assertThat(signal.reasons()).containsExactly(RelapseReason.ABSOLUTE_MISS);
    assertThat(signal.mainCause()).isEqualTo(MissReason.PHONE);

    assertThat(RelapseSignal.judge(days, OCT_17, oct(18)).hasSignal()).isFalse();
  }

  @Test
  @DisplayName("② 10/11~10/17 평균 0.5, 9/27~10/10 평균 0.8 → rateDrop (0.5 < 0.56)")
  void rateDrop() {
    List<JudgedCardDay> days = new ArrayList<>(daily(LocalDate.of(2026, 9, 27), oct(10), 0.8));
    days.addAll(daily(oct(11), OCT_17, 0.5));

    RelapseSignal signal = RelapseSignal.judge(days, OCT_17, OCT_17);

    assertThat(signal.reasons()).containsExactly(RelapseReason.RATE_DROP);
    assertThat(signal.recentRate()).isEqualTo(0.5);
    assertThat(signal.baselineRate()).isCloseTo(0.8, within(1e-9));
  }

  @Test
  @DisplayName("② 10/11~10/17 평균 0.6, 9/27~10/10 평균 0.8 → 신호 없음 (0.6 ≥ 0.56)")
  void noRateDrop() {
    List<JudgedCardDay> days = new ArrayList<>(daily(LocalDate.of(2026, 9, 27), oct(10), 0.8));
    days.addAll(daily(oct(11), OCT_17, 0.6));

    RelapseSignal signal = RelapseSignal.judge(days, OCT_17, OCT_17);

    assertThat(signal.hasSignal()).isFalse();
    assertThat(signal.recentRate()).isNotNull();
  }

  @Test
  @DisplayName("② 기록이 기준선 첫날(9/27)보다 늦게 시작했으면 판정하지 않는다 (실행률도 null)")
  void noBaselineYet() {
    List<JudgedCardDay> days = new ArrayList<>(daily(LocalDate.of(2026, 9, 28), oct(10), 0.8));
    days.addAll(daily(oct(11), OCT_17, 0.1));

    RelapseSignal signal = RelapseSignal.judge(days, OCT_17, OCT_17);

    assertThat(signal.hasSignal()).isFalse();
    assertThat(signal.recentRate()).isNull();
    assertThat(signal.baselineRate()).isNull();
  }

  @Test
  @DisplayName("휴식(rest) 처리한 날은 어느 계산에도 안 들어간다")
  void restIgnored() {
    // 루틴 A: 10/15 실패, 10/16 휴식, 10/17 실패 → 휴식을 빼면 연속 2번
    assertThat(
            RelapseSignal.judge(
                    List.of(
                        routine(A, oct(15), CardResult.FAILED),
                        routine(A, oct(16), CardResult.REST),
                        routine(A, oct(17), CardResult.FAILED)),
                    OCT_17,
                    OCT_17)
                .routineCardIds())
        .containsExactly(A);
    // 절대 카드의 휴식 날은 미실행이 아닙니다.
    assertThat(
            RelapseSignal.judge(
                    List.of(oneOff(B, OCT_17, CardPriority.ABSOLUTE, CardResult.REST, 0, null)),
                    OCT_17,
                    OCT_17)
                .hasSignal())
        .isFalse();
    // 기준선 첫날(9/27)에 휴식 날만 있으면 기준선이 없는 것입니다.
    List<JudgedCardDay> days = new ArrayList<>();
    days.add(oneOff(B, LocalDate.of(2026, 9, 27), CardPriority.FLEXIBLE, CardResult.REST, 0, null));
    days.addAll(daily(oct(1), OCT_17, 0.5));
    assertThat(RelapseSignal.judge(days, OCT_17, OCT_17).recentRate()).isNull();
  }

  @Test
  @DisplayName("주원인: 최근 7일 미실행 원인 중 가장 많은 것, 수가 같으면 더 최근 것, 7일 전 것은 안 셈")
  void mainCause() {
    List<JudgedCardDay> days =
        List.of(
            // 7일 밖(10/10) — 세지 않음
            oneOff(1L, oct(10), CardPriority.FLEXIBLE, CardResult.FAILED, 0, MissReason.TIRED),
            oneOff(2L, oct(10), CardPriority.FLEXIBLE, CardResult.FAILED, 0, MissReason.TIRED),
            oneOff(3L, oct(12), CardPriority.FLEXIBLE, CardResult.FAILED, 0, MissReason.PHONE),
            oneOff(4L, oct(13), CardPriority.FLEXIBLE, CardResult.FAILED, 0, MissReason.SICK),
            oneOff(5L, oct(16), CardPriority.FLEXIBLE, CardResult.FAILED, 0, MissReason.SICK),
            oneOff(6L, oct(17), CardPriority.FLEXIBLE, CardResult.FAILED, 0, MissReason.PHONE),
            // 원인을 안 고른 미실행은 세지 않음
            oneOff(7L, oct(17), CardPriority.FLEXIBLE, CardResult.UNHANDLED, 0, null));

    // 폰 2번(마지막 10/17) · 아픔 2번(마지막 10/16) → 더 최근인 폰
    assertThat(RelapseSignal.judge(days, OCT_17, OCT_17).mainCause()).isEqualTo(MissReason.PHONE);
    assertThat(RelapseSignal.judge(List.of(), OCT_17, OCT_17).mainCause()).isNull();
  }

  @Test
  @DisplayName("오늘 뒤의 날은 넣어도 보지 않는다")
  void futureIgnored() {
    assertThat(
            RelapseSignal.judge(
                    List.of(
                        routine(A, oct(18), CardResult.FAILED),
                        routine(A, oct(19), CardResult.FAILED)),
                    OCT_17,
                    OCT_17)
                .hasSignal())
        .isFalse();
  }
}
