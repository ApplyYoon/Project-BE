package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.ai.dto.DayPlanRequest;
import com.ithopeschool.projectbe.domain.ai.dto.LongPlanOutline;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 긴 계획의 질문 만들기와 답 검사만 봅니다. (GPT 는 부르지 않습니다)
class LongPlanTest {

  // 2026-10-19 은 월요일입니다.
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

  private static LongPlanOutline current() {
    return new LongPlanOutline(
        "중간고사 미적분 1~3단원",
        TODAY,
        TODAY.plusDays(14),
        60,
        "19:00",
        List.of(),
        List.of(
            new LongPlanOutline.Topic("미적분 1단원 개념", 90, List.of("교과서 12~20쪽 읽기", "예제 풀기")),
            new LongPlanOutline.Topic("미적분 2단원 개념", 90, List.of())));
  }

  private static LongPlan.ReviseAnswer change(
      Integer daily,
      String startTime,
      List<String> restDays,
      String endDate,
      List<LongPlan.TopicItem> topics) {
    return new LongPlan.ReviseAnswer("바꿨어.", daily, startTime, restDays, endDate, topics);
  }

  @Test
  @DisplayName("만들기 입력에는 목표 · 기간 · 공부 날 · 쓸 수 있는 시간 · 준비하는 것 · 사진 여부가 들어간다")
  void input() {
    String input =
        LongPlan.input(
            "11월 2일 중간고사 미적분",
            TODAY,
            TODAY.plusDays(14),
            60,
            List.of("SUN", "SAT"),
            new DayPlanRequest.Focus("SCHOOL_EXAM", null),
            true);

    assertThat(input)
        .contains("목표: 11월 2일 중간고사 미적분")
        .contains("기간: 2026-10-19 (월) ~ 2026-11-01 (일), 시험 · 마감 2026-11-02 (월)")
        .contains("공부 날: 10일 (쉬는 요일: 토, 일) — 마지막 2일은 총복습")
        .contains("할 일에 쓸 수 있는 시간: 480분")
        .contains("준비하는 것: 내신(학교 시험)")
        .contains("사진: 함께 보낸 이미지");
    assertThat(
            LongPlan.question("목표", TODAY, TODAY.plusDays(3), 60, List.of(), null, false).input())
        .contains("쉬는 요일: 없음")
        .contains("준비하는 것: (모름)")
        .contains("사진: (없음)");
  }

  @Test
  @DisplayName("고치기 입력에는 오늘 · 지금 계획 · 할 일 목록 · 학생의 요청이 들어간다")
  void reviseInput() {
    String input = LongPlan.reviseInput(current(), "주말은 빼 줘", TODAY);

    assertThat(input)
        .contains("오늘: 2026-10-19 (월)")
        .contains("하루 공부: 60분, 시작 19:00")
        .contains("1. 미적분 1단원 개념 · 90분 (교과서 12~20쪽 읽기 / 예제 풀기)")
        .contains("2. 미적분 2단원 개념 · 90분")
        .endsWith("학생의 요청: 주말은 빼 줘");
  }

  @Test
  @DisplayName("할 일 검사: 빈 제목 · 긴 제목 · 깎아내리는 말 · 시간 없음은 버리고, 시간은 20 ~ 240 으로 자른다")
  void topicsOf() {
    List<LongPlanOutline.Topic> topics =
        LongPlan.topicsOf(
            List.of(
                new LongPlan.TopicItem(" 수학 1단원 ", 10, List.of(" 개념 ", "개념", "", "예제", "문제", "오답")),
                new LongPlan.TopicItem("영어", 500, null),
                new LongPlan.TopicItem(" ", 60, List.of()),
                new LongPlan.TopicItem("가".repeat(31), 60, List.of()),
                new LongPlan.TopicItem("한심한 과목", 60, List.of()),
                new LongPlan.TopicItem("국어", null, List.of())));

    assertThat(topics)
        .containsExactly(
            new LongPlanOutline.Topic("수학 1단원", 20, List.of("개념", "예제", "문제")),
            new LongPlanOutline.Topic("영어", 240, List.of()));

    List<LongPlan.TopicItem> many = new ArrayList<>();
    for (int i = 1; i <= 25; i++) {
      many.add(new LongPlan.TopicItem("과목 " + i, 30, List.of()));
    }
    assertThat(LongPlan.topicsOf(many)).hasSize(20);
    assertThat(LongPlan.topicsOf(null)).isEmpty();
  }

  @Test
  @DisplayName("고치기: 맞는 칸만 바꾸고, 지난 계획은 오늘부터")
  void revisesValidFields() {
    LongPlanOutline revised =
        LongPlan.revised(
            current(),
            change(
                30,
                " 20:00 ",
                List.of("SAT", "SUN", "SAT"),
                "2026-11-05",
                List.of(new LongPlan.TopicItem("영어 본문 1과", 60, List.of("본문 읽기")))),
            TODAY.plusDays(2));

    assertThat(revised.startDate()).isEqualTo(TODAY.plusDays(2));
    assertThat(revised.dailyMinutes()).isEqualTo(30);
    assertThat(revised.startTime()).isEqualTo("20:00");
    assertThat(revised.restDays()).containsExactly("SAT", "SUN");
    assertThat(revised.endDate()).isEqualTo(LocalDate.of(2026, 11, 5));
    assertThat(revised.topics())
        .containsExactly(new LongPlanOutline.Topic("영어 본문 1과", 60, List.of("본문 읽기")));
    assertThat(revised.goal()).isEqualTo("중간고사 미적분 1~3단원");
  }

  @Test
  @DisplayName("고치기: 틀린 칸 · 빈 칸은 무시하고 그대로 둔다")
  void ignoresInvalidFields() {
    LongPlanOutline unchanged = current();

    // 하루 500분 · 25시 · 모든 요일 쉬기 · 31일 뒤 · 쓸 수 없는 할 일
    LongPlanOutline revised =
        LongPlan.revised(
            unchanged,
            change(
                500,
                "25:00",
                List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"),
                "2026-11-20",
                List.of(new LongPlan.TopicItem("", 60, List.of()))),
            TODAY);
    assertThat(revised).isEqualTo(unchanged);

    // 모르는 요일 · 날짜 모양이 아님 · 빈 할 일 목록
    assertThat(
            LongPlan.revised(
                unchanged, change(null, null, List.of("HOLIDAY"), "다음 주", List.of()), TODAY))
        .isEqualTo(unchanged);
    // 시작 날보다 이른 시험 날
    assertThat(LongPlan.revised(unchanged, change(null, null, null, "2026-10-19", null), TODAY))
        .isEqualTo(unchanged);
  }

  @Test
  @DisplayName("고치기: 쉬는 요일을 바꿨더니 공부 날이 없으면 쉬는 요일 · 시험 날은 그대로")
  void keepsStudyDays() {
    // 10-19(월) ~ 10-20(화) 하루짜리 계획에서 월요일을 쉬게 해 달라고 함
    LongPlanOutline oneDay =
        new LongPlanOutline(
            "수행평가",
            TODAY,
            TODAY.plusDays(1),
            60,
            "19:00",
            List.of(),
            List.of(new LongPlanOutline.Topic("보고서", 60, List.of())));

    LongPlanOutline revised =
        LongPlan.revised(oneDay, change(null, null, List.of("MON"), null, null), TODAY);
    assertThat(revised.restDays()).isEmpty();
    assertThat(revised.endDate()).isEqualTo(TODAY.plusDays(1));
  }

  @Test
  @DisplayName("한 줄: 비었거나 깎아내리는 말이면 기본 말")
  void reply() {
    assertThat(LongPlan.replyOf(" 3단원으로 나눴어. ", LongPlan.DEFAULT_REPLY)).isEqualTo("3단원으로 나눴어.");
    assertThat(LongPlan.replyOf(null, LongPlan.DEFAULT_REPLY)).isEqualTo("이렇게 나눠 봤어. 어때?");
    assertThat(LongPlan.replyOf("게으른 너를 위해 줄였어", LongPlan.DEFAULT_REVISE_REPLY))
        .isEqualTo("이렇게 고쳐 봤어. 어때?");
  }
}
