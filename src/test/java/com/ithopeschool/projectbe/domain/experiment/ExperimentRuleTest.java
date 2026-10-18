package com.ithopeschool.projectbe.domain.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.card.CardPriority;
import com.ithopeschool.projectbe.domain.cardresult.CardResult;
import com.ithopeschool.projectbe.domain.cardresult.JudgedCardDay;
import com.ithopeschool.projectbe.domain.suggestion.SuggestionGrade;
import com.ithopeschool.projectbe.domain.suggestion.SuggestionRate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// FE 의 judgeExperiment (src/lib/experiments.ts) 주석의 예시를 그대로 옮겨 같은 결과가 나오는지 확인합니다.
class ExperimentRuleTest {

  // 10월 15일 시작 → 기준선 10/1 ~ 10/14, 실험 10/15 ~ 10/28
  private static final LocalDate START = LocalDate.of(2026, 10, 15);
  private static final Set<Long> CARDS = Set.of(1L);

  private static JudgedCardDay day(long cardId, LocalDate date, CardResult result, double runRate) {
    return new JudgedCardDay(
        cardId, date, true, CardPriority.FLEXIBLE, result, runRate, null, null, null);
  }

  // from 부터 count 일 동안 카드 1번의 하루치. 앞의 done 일은 실행(1), 나머지는 실패(0)
  private static List<JudgedCardDay> days(LocalDate from, int count, int done) {
    List<JudgedCardDay> days = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      boolean isDone = i < done;
      days.add(
          day(1, from.plusDays(i), isDone ? CardResult.DONE : CardResult.FAILED, isDone ? 1 : 0));
    }
    return days;
  }

  private static List<JudgedCardDay> baselineAndExperiment(
      int baselineDone, int experimentDone, int experimentCount) {
    List<JudgedCardDay> all = new ArrayList<>(days(START.minusDays(14), 10, baselineDone));
    all.addAll(days(START, experimentCount, experimentDone));
    return all;
  }

  @Test
  @DisplayName("날짜: 10/15 시작이면 마지막 날 10/28, 10/19 는 5일째, 10/29 부터 판정하고 15일째로 멈춘다")
  void dates() {
    assertThat(ExperimentRule.endDate(START)).isEqualTo(LocalDate.of(2026, 10, 28));
    assertThat(ExperimentRule.dayOf(START, LocalDate.of(2026, 10, 19))).isEqualTo(5);
    assertThat(ExperimentRule.isJudgeDay(START, LocalDate.of(2026, 10, 28))).isFalse();
    assertThat(ExperimentRule.isJudgeDay(START, LocalDate.of(2026, 10, 29))).isTrue();
    assertThat(ExperimentRule.dayOf(START, LocalDate.of(2026, 11, 30))).isEqualTo(15);
  }

  @Test
  @DisplayName("기준선 0.50, 실험 0.70, 기준 10%p → 효과 있음 (+20%p)")
  void effective() {
    ExperimentRule.Judgement judgement =
        ExperimentRule.judge(CARDS, START, 10, baselineAndExperiment(5, 7, 10));

    assertThat(judgement.status()).isEqualTo(ExperimentStatus.EFFECTIVE);
    assertThat(judgement.baselineRate()).isEqualTo(0.5);
    assertThat(judgement.experimentRate()).isEqualTo(0.7);
  }

  @Test
  @DisplayName("기준선 0.50, 실험 0.58, 기준 10%p → 효과 없음 (+8%p). 딱 기준만큼 오른 것도 효과 없음")
  void noEffect() {
    // 실험 기간 50개 중 29개 실행 = 0.58
    List<JudgedCardDay> all = new ArrayList<>(days(START.minusDays(14), 10, 5));
    for (int i = 0; i < 50; i++) {
      boolean isDone = i < 29;
      all.add(
          day(
              100 + i,
              START.plusDays(i % 14),
              isDone ? CardResult.DONE : CardResult.FAILED,
              isDone ? 1 : 0));
    }
    Set<Long> cards = new HashSet<>(CARDS);
    for (long i = 100; i < 150; i++) {
      cards.add(i);
    }
    assertThat(ExperimentRule.judge(cards, START, 10, all).status())
        .isEqualTo(ExperimentStatus.NO_EFFECT);

    // 0.50 → 0.60 은 정확히 +10%p 라서 "넘게" 오른 것이 아닙니다.
    assertThat(ExperimentRule.judge(CARDS, START, 10, baselineAndExperiment(5, 6, 10)).status())
        .isEqualTo(ExperimentStatus.NO_EFFECT);
  }

  @Test
  @DisplayName("실험 기간 카드 3개뿐이면 알 수 없음, 붙이지 않은 카드와 휴식한 날은 세지 않는다")
  void unclear() {
    ExperimentRule.Judgement few =
        ExperimentRule.judge(CARDS, START, 10, baselineAndExperiment(5, 3, 3));
    assertThat(few.status()).isEqualTo(ExperimentStatus.UNCLEAR);
    assertThat(few.baselineRate()).isEqualTo(0.5);
    assertThat(few.experimentRate()).isNull();

    // 실험 기간에 휴식 3일 + 다른 카드 5개 + 붙인 카드 4개 → 붙인 카드는 4개뿐이라 알 수 없음
    List<JudgedCardDay> all = new ArrayList<>(days(START.minusDays(14), 10, 5));
    all.addAll(days(START, 4, 4));
    for (int i = 0; i < 3; i++) {
      all.add(day(1, START.plusDays(5 + i), CardResult.REST, 0));
    }
    for (int i = 0; i < 5; i++) {
      all.add(day(2, START.plusDays(i), CardResult.DONE, 1));
    }
    assertThat(ExperimentRule.judge(CARDS, START, 10, all).status())
        .isEqualTo(ExperimentStatus.UNCLEAR);
  }

  @Test
  @DisplayName("이행률: 실험 기간 붙인 카드를 실행한 비율, 휴식은 빼고 70% 이상 잘 시행 · 셀 것이 없으면 null")
  void fulfillment() {
    List<JudgedCardDay> all = new ArrayList<>(days(START, 10, 7));
    all.add(day(1, START.plusDays(11), CardResult.REST, 0));
    // 기준선 기간의 기록은 이행률에 들어가지 않습니다.
    all.addAll(days(START.minusDays(14), 5, 0));

    SuggestionRate rate = ExperimentRule.fulfillment(CARDS, START, all);
    assertThat(rate.rate()).isEqualTo(70);
    assertThat(rate.grade()).isEqualTo(SuggestionGrade.GOOD);

    assertThat(ExperimentRule.fulfillment(CARDS, START, List.of())).isNull();
  }

  @Test
  @DisplayName("전략 id 는 FE 와 같은 10개만 받는다")
  void strategies() {
    assertThat(ExperimentStrategy.values()).hasSize(10);
    assertThat(ExperimentStrategy.fromId("phone-bag")).isEqualTo(ExperimentStrategy.PHONE_BAG);
    assertThat(ExperimentStrategy.fromId("phone-easy-1")).isNull();
  }
}
