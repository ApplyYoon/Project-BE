package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.ai.dto.LongPlanOutline;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 긴 계획의 엔진(날짜 · 시각 나누기)만 봅니다. AI 는 부르지 않습니다.
class LongPlanSchedulerTest {

  // 2026-10-19 은 월요일입니다.
  private static final LocalDate MON = LocalDate.of(2026, 10, 19);

  private static LongPlanOutline.Topic topic(String title, int minutes) {
    return new LongPlanOutline.Topic(title, minutes, List.of(title + " 하기"));
  }

  private static LongPlanOutline plan(
      LocalDate start,
      LocalDate end,
      int daily,
      String startTime,
      List<String> restDays,
      List<LongPlanOutline.Topic> topics) {
    return new LongPlanOutline("중간고사", start, end, daily, startTime, restDays, topics);
  }

  private static DayPlanRevise.Card card(
      LocalDate date, String start, int minutes, String title, List<String> checklist) {
    return new DayPlanRevise.Card(date, start, minutes, title, checklist);
  }

  @Test
  @DisplayName("공부 날은 시작 날 ~ 시험 전날 중 쉬는 요일을 뺀 날")
  void studyDays() {
    // 10-19(월) ~ 11-01(일), 시험 11-02 → 14일 중 주말 4일을 빼면 10일
    List<LocalDate> days =
        LongPlanScheduler.studyDaysOf(MON, MON.plusDays(14), List.of("SAT", "SUN"));
    assertThat(days).hasSize(10);
    assertThat(days).doesNotContain(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 25));
    assertThat(days.get(days.size() - 1)).isEqualTo(LocalDate.of(2026, 10, 30));
    assertThat(LongPlanScheduler.dayCodeOf(MON)).isEqualTo("MON");
  }

  @Test
  @DisplayName("총복습 날: 7일 이상이면 2일, 3 ~ 6일이면 1일, 2일 이하면 없음")
  void reviewDays() {
    assertThat(LongPlanScheduler.reviewDayCount(14)).isEqualTo(2);
    assertThat(LongPlanScheduler.reviewDayCount(7)).isEqualTo(2);
    assertThat(LongPlanScheduler.reviewDayCount(6)).isEqualTo(1);
    assertThat(LongPlanScheduler.reviewDayCount(3)).isEqualTo(1);
    assertThat(LongPlanScheduler.reviewDayCount(2)).isZero();
    assertThat(LongPlanScheduler.capacityOf(10, 60)).isEqualTo(480);
  }

  @Test
  @DisplayName("할 일을 순서대로 고르게 나누고, 나뉜 할 일은 (1/3) 을 붙이고, 마지막 2일은 총복습")
  void schedulesEvenly() {
    // 공부 날 7일(10-19 ~ 10-25) → 할 일 5일 · 복습 2일. 할 일 합 240분 ÷ 5일 → 하루 50분으로 고르게
    LongPlanScheduler.Result result =
        LongPlanScheduler.schedule(
            plan(
                MON,
                MON.plusDays(7),
                60,
                "19:00",
                List.of(),
                List.of(topic("미적분 1단원", 120), topic("미적분 2단원", 60), topic("미적분 3단원", 60))));

    assertThat(result.overflow()).isFalse();
    assertThat(result.cards())
        .containsExactly(
            card(MON, "19:00", 50, "미적분 1단원 (1/3)", List.of("미적분 1단원 하기")),
            card(MON.plusDays(1), "19:00", 50, "미적분 1단원 (2/3)", List.of("미적분 1단원 하기")),
            card(MON.plusDays(2), "19:00", 20, "미적분 1단원 (3/3)", List.of("미적분 1단원 하기")),
            card(MON.plusDays(2), "19:30", 30, "미적분 2단원 (1/2)", List.of("미적분 2단원 하기")),
            card(MON.plusDays(3), "19:00", 30, "미적분 2단원 (2/2)", List.of("미적분 2단원 하기")),
            card(MON.plusDays(3), "19:40", 20, "미적분 3단원 (1/2)", List.of("미적분 3단원 하기")),
            card(MON.plusDays(4), "19:00", 40, "미적분 3단원 (2/2)", List.of("미적분 3단원 하기")),
            card(MON.plusDays(5), "19:00", 60, "총복습 (1/2)", LongPlanScheduler.REVIEW_CHECKLIST),
            card(MON.plusDays(6), "19:00", 60, "총복습 (2/2)", LongPlanScheduler.REVIEW_CHECKLIST));
  }

  @Test
  @DisplayName("쉬는 요일에는 카드가 없고, 체크리스트가 없으면 제목을 한 항목으로")
  void skipsRestDays() {
    // 10-23(금) ~ 10-26(월), 시험 10-27. 주말을 쉬면 공부 날은 금 · 월 2일 → 복습 날 없음
    LocalDate fri = LocalDate.of(2026, 10, 23);
    LongPlanScheduler.Result result =
        LongPlanScheduler.schedule(
            plan(
                fri,
                fri.plusDays(4),
                60,
                "20:00",
                List.of("SAT", "SUN"),
                List.of(
                    new LongPlanOutline.Topic("영어 본문 1과", 60, List.of()), topic("영어 본문 2과", 60))));

    assertThat(result.overflow()).isFalse();
    assertThat(result.cards())
        .containsExactly(
            card(fri, "20:00", 60, "영어 본문 1과", List.of("영어 본문 1과")),
            card(fri.plusDays(3), "20:00", 60, "영어 본문 2과", List.of("영어 본문 2과 하기")));
  }

  @Test
  @DisplayName("할 일이 하루 시간 안에 다 안 들어가면 비례로 줄이고 overflow")
  void shrinksWhenTooMuch() {
    // 공부 날 2일 × 60분 = 120분에 400분 → 60 + 60
    LongPlanScheduler.Result result =
        LongPlanScheduler.schedule(
            plan(
                MON,
                MON.plusDays(2),
                60,
                "19:00",
                List.of(),
                List.of(topic("국어", 200), topic("수학", 200))));

    assertThat(result.overflow()).isTrue();
    assertThat(result.cards())
        .containsExactly(
            card(MON, "19:00", 60, "국어", List.of("국어 하기")),
            card(MON.plusDays(1), "19:00", 60, "수학", List.of("수학 하기")));
    assertThat(LongPlanScheduler.shrunk(List.of(120, 60, 10), 90)).containsExactly(55, 25, 20);
  }

  @Test
  @DisplayName("한 장은 120분까지, 15분보다 짧은 꼬리는 만들지 않는다")
  void splitsLongTopic() {
    // 하루 300분, 130분짜리 할 일 → 115 + 15 (꼬리 10분을 남기지 않음)
    LongPlanScheduler.Result result =
        LongPlanScheduler.schedule(
            plan(MON, MON.plusDays(1), 300, "09:00", List.of(), List.of(topic("한국사", 130))));

    assertThat(result.cards())
        .extracting(DayPlanRevise.Card::durationMinutes)
        .containsExactly(115, 15);
    assertThat(result.cards())
        .extracting(DayPlanRevise.Card::startTime)
        .containsExactly("09:00", "11:05");
  }

  @Test
  @DisplayName("하루 시간보다 조금 긴 할 일은 15분 안쪽으로 넘겨서 한 장으로")
  void absorbsShortTail() {
    // 하루 20분에 25분짜리 → 꼬리 5분을 붙여 25분 한 장 (다음 날로 끝없이 밀리지 않음)
    LongPlanScheduler.Result result =
        LongPlanScheduler.schedule(
            plan(MON, MON.plusDays(2), 20, "19:00", List.of(), List.of(topic("화학 1단원", 25))));

    assertThat(result.overflow()).isFalse();
    assertThat(result.cards())
        .containsExactly(card(MON, "19:00", 25, "화학 1단원", List.of("화학 1단원 하기")));
  }

  @Test
  @DisplayName("자정을 넘는 카드는 버리고 overflow, 공부 날이 없으면 빈 목록")
  void dropsAfterMidnightAndEmpty() {
    LongPlanScheduler.Result late =
        LongPlanScheduler.schedule(
            plan(MON, MON.plusDays(1), 120, "23:00", List.of(), List.of(topic("물리", 90))));
    assertThat(late.cards()).isEmpty();
    assertThat(late.overflow()).isTrue();

    // 토 ~ 일만 있는데 주말을 쉼
    LocalDate sat = LocalDate.of(2026, 10, 24);
    LongPlanScheduler.Result none =
        LongPlanScheduler.schedule(
            plan(
                sat,
                sat.plusDays(2),
                60,
                "19:00",
                List.of("SAT", "SUN"),
                List.of(topic("물리", 60))));
    assertThat(none.cards()).isEmpty();
    assertThat(none.overflow()).isTrue();
  }

  @Test
  @DisplayName("나뉜 할 일의 제목은 30자를 넘지 않게 앞을 줄인다")
  void numberedTitle() {
    assertThat(LongPlanScheduler.numbered("수학", 1, 1)).isEqualTo("수학");
    assertThat(LongPlanScheduler.numbered("수학", 2, 3)).isEqualTo("수학 (2/3)");
    String longTitle = "가".repeat(30);
    assertThat(LongPlanScheduler.numbered(longTitle, 10, 12)).hasSize(30).endsWith(" (10/12)");
    assertThat(LongPlanScheduler.spreadMinutes(600, 28)).isEqualTo(30);
    assertThat(LongPlanScheduler.spreadMinutes(900, 10)).isEqualTo(90);
    assertThat(LongPlanScheduler.spreadMinutes(910, 10)).isEqualTo(95);
  }
}
