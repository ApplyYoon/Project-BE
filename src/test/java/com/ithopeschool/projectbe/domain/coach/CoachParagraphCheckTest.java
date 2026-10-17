package com.ithopeschool.projectbe.domain.coach;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.coach.dto.WeeklyCoachRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 스프링을 띄우지 않는 단위 테스트입니다. AI 가 쓴 문단을 내보내도 되는지 가려내는 규칙을 봅니다.
class CoachParagraphCheckTest {

  // AI 에게 준 숫자: 64, 12, 55, 20, 40, 5
  private static final List<WeeklyCoachRequest.Number> NUMBERS =
      List.of(
          new WeeklyCoachRequest.Number("계획 대비 실제", "64%", "계획 12개"),
          new WeeklyCoachRequest.Number("완주율", "55%", "20번"),
          new WeeklyCoachRequest.Number("화요일 완주율", "40%", "5번"));

  @Test
  @DisplayName("준 숫자만 쓴 문단은 통과한다")
  void paragraphWithGivenNumbersIsSafe() {
    String paragraph =
        "이번 주는 계획의 64% 를 했어. 목표를 정한 타이머 20번 가운데 완주율은 55% 야."
            + " 화요일은 40% 로 조금 낮았어. 다음 주에는 목표 시간을 조금 줄여서 시작해 보자.";

    assertThat(CoachParagraphCheck.isSafe(paragraph, NUMBERS)).isTrue();
  }

  @Test
  @DisplayName("숫자가 하나도 없는 문단도 통과한다")
  void paragraphWithoutNumbersIsSafe() {
    assertThat(CoachParagraphCheck.isSafe("이번 주도 꾸준히 했어. 지금처럼 이어 가 보자.", NUMBERS)).isTrue();
  }

  @Test
  @DisplayName("준 적 없는 숫자가 하나라도 있으면 버린다 (AI 가 지어냈거나 계산해서 바꾼 숫자)")
  void inventedNumberIsRejected() {
    // 70 은 준 적이 없습니다.
    assertThat(CoachParagraphCheck.isSafe("이번 주는 계획의 70% 를 했어.", NUMBERS)).isFalse();
    // 64 - 55 = 9 를 계산해서 말한 경우도 준 적 없는 숫자입니다.
    assertThat(CoachParagraphCheck.isSafe("계획 대비는 64% 인데 완주율은 9%p 낮아.", NUMBERS)).isFalse();
    // 권하는 행동에 숫자를 붙인 경우
    assertThat(CoachParagraphCheck.isSafe("완주율은 55% 야. 다음 주엔 35분으로 시작해 보자.", NUMBERS)).isFalse();
  }

  @Test
  @DisplayName("숫자는 통째로 비교한다 (64 를 줬다고 6 이나 640 이 통과하지 않는다)")
  void numbersAreComparedAsWhole() {
    assertThat(CoachParagraphCheck.usesOnlyGivenNumbers("6번 했어.", NUMBERS)).isFalse();
    assertThat(CoachParagraphCheck.usesOnlyGivenNumbers("640% 야.", NUMBERS)).isFalse();
    assertThat(CoachParagraphCheck.usesOnlyGivenNumbers("64% 야.", NUMBERS)).isTrue();
    // 근거에 있던 숫자(계획 12개, 5번)도 준 숫자입니다.
    assertThat(CoachParagraphCheck.usesOnlyGivenNumbers("계획 12개 중 화요일은 5번이었어.", NUMBERS)).isTrue();
  }

  @Test
  @DisplayName("깎아내리거나 남과 비교하는 말이 있으면 버린다")
  void bannedWordsAreRejected() {
    assertThat(CoachParagraphCheck.isSafe("완주율이 55% 라니 한심하다.", NUMBERS)).isFalse();
    assertThat(CoachParagraphCheck.isSafe("이렇게 게으르면 안 돼.", NUMBERS)).isFalse();
    assertThat(CoachParagraphCheck.isSafe("다른 애들은 더 많이 해.", NUMBERS)).isFalse();
    assertThat(CoachParagraphCheck.isSafe("남들은 다 끝까지 하는데.", NUMBERS)).isFalse();

    assertThat(CoachParagraphCheck.hasBannedWord("화요일은 40% 로 조금 낮았어.")).isFalse();
  }

  @Test
  @DisplayName("비어 있거나 너무 긴 문단은 버린다")
  void blankOrTooLongIsRejected() {
    assertThat(CoachParagraphCheck.isSafe(null, NUMBERS)).isFalse();
    assertThat(CoachParagraphCheck.isSafe("", NUMBERS)).isFalse();
    assertThat(CoachParagraphCheck.isSafe("   ", NUMBERS)).isFalse();

    String atTheLimit = "가".repeat(CoachParagraphCheck.MAX_LENGTH);
    assertThat(CoachParagraphCheck.isSafe(atTheLimit, NUMBERS)).isTrue();
    assertThat(CoachParagraphCheck.isSafe(atTheLimit + "가", NUMBERS)).isFalse();
  }
}
