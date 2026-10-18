package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.ai.dto.CoachNudgeRequest;
import com.ithopeschool.projectbe.domain.ai.dto.DayPlanRequest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 값 계산 테스트: AI 에게 주는 입력과, AI 답의 검사(길이 · 말투 · 위기 표현 · 지어낸 숫자)를 봅니다.
class CoachNudgeTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

  private static final CoachNudgeRequest FULL =
      new CoachNudgeRequest(
          MONDAY,
          List.of("국어", "영어Ⅰ", "수학Ⅰ", "한국사", "체육", "미적분"),
          List.of(new CoachNudgeRequest.Card("19:00", "미적분 문제집", 40)),
          new CoachNudgeRequest.Yesterday(3, 2, 95, 82, 4),
          List.of("수열의 극한"),
          new CoachNudgeRequest.Exam("2학기 중간고사", 14),
          new DayPlanRequest.Focus("SCHOOL_EXAM", null));

  private static CoachNudge.Answer answer(String title, String body) {
    return new CoachNudge.Answer(
        new CoachNudge.Answer.Item(title, body),
        new CoachNudge.Answer.Item("오늘 카드 어땠어?", "미적분 문제집 해 봤어? 내일 계획도 같이 정하자."));
  }

  @Test
  @DisplayName("입력: 보낸 칸만 줄로 넣고, 시간표는 1교시부터 번호를 붙인다")
  void inputHasOnlySentLines() {
    String input = CoachNudge.input(FULL);

    assertThat(input)
        .isEqualTo(
            "날짜: 2026-10-19 (월)\n"
                + "시간표: 1교시 국어 · 2교시 영어Ⅰ · 3교시 수학Ⅰ · 4교시 한국사 · 5교시 체육 · 6교시 미적분\n"
                + "계획 카드: 19:00 미적분 문제집 40분\n"
                + "전날(10-18) 기록: 계획 카드 3개 중 2개 끝냄 · 순공 95분 · 집중도 82점 · 폰 든 횟수 4번\n"
                + "최근 틀린 확인 문제: 수열의 극한\n"
                + "가까운 시험: 2학기 중간고사 D-14\n"
                + "준비하는 것: 내신(학교 시험)");

    CoachNudgeRequest onlyDate = new CoachNudgeRequest(MONDAY, null, null, null, null, null, null);
    assertThat(CoachNudge.input(onlyDate)).isEqualTo("날짜: 2026-10-19 (월)");
  }

  @Test
  @DisplayName("입력: 시험이 오늘이면 D-0 대신 (오늘), 집중도 · 폰 든 횟수는 보낸 날만")
  void inputExamTodayAndOptionalNumbers() {
    CoachNudgeRequest request =
        new CoachNudgeRequest(
            MONDAY,
            null,
            null,
            new CoachNudgeRequest.Yesterday(2, 0, 0, null, null),
            null,
            new CoachNudgeRequest.Exam("수행평가", 0),
            null);

    assertThat(CoachNudge.input(request))
        .isEqualTo(
            "날짜: 2026-10-19 (월)\n"
                + "전날(10-18) 기록: 계획 카드 2개 중 0개 끝냄 · 순공 0분\n"
                + "가까운 시험: 수행평가 (오늘)");
  }

  @Test
  @DisplayName("검사: 입력에 있는 숫자(6교시 · D-14 · 95분)는 쓸 수 있고, 앞뒤 공백을 지운다")
  void keepsNumbersFromInput() {
    String input = CoachNudge.input(FULL);

    CoachNudge.Result result =
        CoachNudge.from(answer(" 오늘 6교시 미적분 ", " 어제 95분 했네. 시험 D-14, 틀린 극한 문제 한 번만 보자. "), input);

    assertThat(result.morning())
        .isEqualTo(new CoachNudge.Nudge("오늘 6교시 미적분", "어제 95분 했네. 시험 D-14, 틀린 극한 문제 한 번만 보자."));
    assertThat(result.evening())
        .isEqualTo(new CoachNudge.Nudge("오늘 카드 어땠어?", "미적분 문제집 해 봤어? 내일 계획도 같이 정하자."));
  }

  @Test
  @DisplayName("검사: 입력에 없는 숫자를 쓰면(지어낸 기록 · '15분만' 같은 제안) 그 칸만 null")
  void dropsMadeUpNumbers() {
    String input = CoachNudge.input(FULL);

    assertThat(CoachNudge.from(answer("오늘 7교시 미적분", "쉬는 시간에 보자."), input).morning()).isNull();
    assertThat(CoachNudge.from(answer("오늘 미적분", "어제 120분 했네!"), input).morning()).isNull();
    // 10 은 날짜(10-19)에 있어서 통과하므로, 입력에 없는 15 로 봅니다.
    CoachNudge.Result result = CoachNudge.from(answer("오늘 미적분", "딱 15분만 해 볼까?"), input);
    assertThat(result.morning()).isNull();
    assertThat(result.evening()).isNotNull();
  }

  @Test
  @DisplayName("검사: 제목 20자 · 글 70자를 넘거나 비었으면 그 칸은 null (경계값은 통과)")
  void checksLength() {
    String input = CoachNudge.input(FULL);

    assertThat(CoachNudge.from(answer("가".repeat(20), "나".repeat(70)), input).morning())
        .isNotNull();
    assertThat(CoachNudge.from(answer("가".repeat(21), "나"), input).morning()).isNull();
    assertThat(CoachNudge.from(answer("가", "나".repeat(71)), input).morning()).isNull();
    assertThat(CoachNudge.from(answer(" ", "나"), input).morning()).isNull();
    assertThat(CoachNudge.from(answer("가", null), input).morning()).isNull();
  }

  @Test
  @DisplayName("검사: 깎아내리는 말이나 위기 표현이 있으면 그 칸은 null")
  void dropsBannedAndCrisisText() {
    String input = CoachNudge.input(FULL);

    assertThat(CoachNudge.from(answer("오늘 미적분", "어제도 안 하다니 한심해."), input).morning()).isNull();
    assertThat(CoachNudge.from(answer("오늘 미적분", "죽고 싶을 만큼 힘들어도 해."), input).morning()).isNull();
  }

  @Test
  @DisplayName("검사: 답이 없거나 칸이 비었으면 null")
  void nullAnswer() {
    assertThat(CoachNudge.from(null, "날짜")).isEqualTo(new CoachNudge.Result(null, null));
    assertThat(CoachNudge.from(new CoachNudge.Answer(null, null), "날짜"))
        .isEqualTo(new CoachNudge.Result(null, null));
  }

  @Test
  @DisplayName("숫자 찾기: 07 과 7 은 같은 숫자")
  void numbersIn() {
    assertThat(CoachNudge.numbersIn("07:30 에 3교시")).containsExactlyInAnyOrder(7L, 30L, 3L);
    assertThat(CoachNudge.numbersIn("숫자 없음")).isEmpty();
  }
}
