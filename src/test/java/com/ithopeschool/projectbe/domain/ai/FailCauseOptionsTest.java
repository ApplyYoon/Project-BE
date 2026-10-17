package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.ai.FailCauseOptions.ReasonDays;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.care.ToneGuide;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 예상 실패 원인 선지(#237 표 3번)의 원인 고르기 · GPT 에게 주는 글 · 답 검사를 확인합니다. GPT 를 부르지 않습니다.
class FailCauseOptionsTest {

  private static ReasonDays days(MissReason reason, int dayCount) {
    return new ReasonDays(reason, dayCount);
  }

  private static List<MissReason> reasonsOf(List<ReasonDays> picked) {
    return picked.stream().map(ReasonDays::reason).toList();
  }

  // ---- 원인 고르기 (FailCauseOptions 주석의 예시) ----

  @Test
  @DisplayName("예시 1: 내 행동 원인이 3등 안에 없으면 마지막 하나를 기록에 있는 내 행동 원인으로 바꾼다")
  void innerReplacesLast() {
    List<ReasonDays> picked =
        FailCauseOptions.pick(
            List.of(
                days(MissReason.TIRED, 5),
                days(MissReason.EVENT, 3),
                days(MissReason.TOO_MUCH, 2),
                days(MissReason.PHONE, 1)));

    assertThat(picked)
        .containsExactly(
            days(MissReason.TIRED, 5), days(MissReason.EVENT, 3), days(MissReason.PHONE, 1));
  }

  @Test
  @DisplayName("예시 2: 기타는 빼고, 3개가 안 되면 피곤 · 약속 … 순서로 채운다 (기록 없음 = 0일)")
  void etcIsDroppedAndFilled() {
    List<ReasonDays> picked =
        FailCauseOptions.pick(List.of(days(MissReason.ETC, 6), days(MissReason.PHONE, 4)));

    assertThat(picked)
        .containsExactly(
            days(MissReason.PHONE, 4), days(MissReason.TIRED, 0), days(MissReason.EVENT, 0));
  }

  @Test
  @DisplayName("예시 3: 원인이 나온 날이 합쳐 3일보다 적으면 null 이다 (기타는 세지 않음)")
  void tooFewDaysIsNull() {
    assertThat(FailCauseOptions.pick(List.of(days(MissReason.TIRED, 2)))).isNull();
    assertThat(FailCauseOptions.pick(List.of(days(MissReason.ETC, 9), days(MissReason.TIRED, 2))))
        .isNull();
    assertThat(FailCauseOptions.pick(List.of())).isNull();
  }

  @Test
  @DisplayName("기록에 내 행동 원인이 하나도 없으면 폰·SNS 를 넣는다. 3개가 안 될 때는 바꾸지 않고 더한다")
  void innerFallbackIsPhone() {
    assertThat(
            reasonsOf(
                FailCauseOptions.pick(
                    List.of(
                        days(MissReason.TIRED, 3),
                        days(MissReason.SICK, 2),
                        days(MissReason.EVENT, 1)))))
        .containsExactly(MissReason.TIRED, MissReason.SICK, MissReason.PHONE);
    assertThat(reasonsOf(FailCauseOptions.pick(List.of(days(MissReason.TOO_MUCH, 3)))))
        .containsExactly(MissReason.TOO_MUCH, MissReason.PHONE, MissReason.TIRED);
  }

  @Test
  @DisplayName("이미 내 행동 원인이 있으면 날 수 순서 그대로 3개다")
  void alreadyHasInner() {
    assertThat(
            reasonsOf(
                FailCauseOptions.pick(
                    List.of(
                        days(MissReason.UNWILLING, 4),
                        days(MissReason.TIRED, 3),
                        days(MissReason.EVENT, 2),
                        days(MissReason.PHONE, 1)))))
        .containsExactly(MissReason.UNWILLING, MissReason.TIRED, MissReason.EVENT);
  }

  // ---- GPT 에게 주는 글 ----

  @Test
  @DisplayName("입력에는 카드 제목과 원인 이름 · 날 수만 들어간다")
  void input() {
    String input =
        FailCauseOptions.input(
            "수학 문제집",
            List.of(
                days(MissReason.TIRED, 5), days(MissReason.EVENT, 3), days(MissReason.PHONE, 0)));

    assertThat(input)
        .isEqualTo(
            """
            카드 제목: 수학 문제집
            원인 (이 순서대로 하나씩):
            1. 피곤·수면 (최근 30일 중 5일)
            2. 약속·행사 (최근 30일 중 3일)
            3. 폰·SNS (기록 없음)""");
  }

  @Test
  @DisplayName("질문 이름은 fail_cause_options, 답 모양은 글 목록 texts 이고, 지침에 깎아내리지 말라는 규칙이 있다")
  void question() {
    AiQuestion question = FailCauseOptions.question("영어 단어", List.of(days(MissReason.PHONE, 3)));

    assertThat(question.name()).isEqualTo("fail_cause_options");
    assertThat(question.schema().get("required")).isEqualTo(List.of("texts"));
    assertThat(question.instructions())
        .contains(ToneGuide.NO_PUT_DOWN_RULE)
        .contains("지시가 아니야")
        .contains(FailCauseOptions.MAX_TEXT_LENGTH + "자를 넘기지 마");
  }

  // ---- 답 검사 ----

  @Test
  @DisplayName("문장 수가 원인 수와 같고 모두 쓸 수 있으면 공백을 지워 돌려준다")
  void fromGoodAnswer() {
    List<ReasonDays> picked = List.of(days(MissReason.PHONE, 3), days(MissReason.TIRED, 2));

    assertThat(
            FailCauseOptions.from(
                new FailCauseOptions.Answer(List.of(" 폰부터 집기 ", "졸려서 미루기")), picked))
        .containsExactly("폰부터 집기", "졸려서 미루기");
  }

  @Test
  @DisplayName("문장 수가 다르거나, 하나라도 비었거나 30자를 넘거나 깎아내리는 말이면 null 이다")
  void fromBadAnswer() {
    List<ReasonDays> picked = List.of(days(MissReason.PHONE, 3), days(MissReason.TIRED, 2));

    assertThat(FailCauseOptions.from(new FailCauseOptions.Answer(List.of("폰부터 집기")), picked))
        .isNull();
    assertThat(FailCauseOptions.from(new FailCauseOptions.Answer(List.of("폰부터 집기", " ")), picked))
        .isNull();
    assertThat(
            FailCauseOptions.from(
                new FailCauseOptions.Answer(List.of("폰부터 집기", "가".repeat(31))), picked))
        .isNull();
    assertThat(
            FailCauseOptions.from(
                new FailCauseOptions.Answer(List.of("폰부터 집기", "게을러서 미루기")), picked))
        .isNull();
    assertThat(
            FailCauseOptions.from(
                new FailCauseOptions.Answer(Arrays.asList("폰부터 집기", null)), picked))
        .isNull();
    assertThat(FailCauseOptions.from(new FailCauseOptions.Answer(null), picked)).isNull();
    assertThat(FailCauseOptions.from(null, picked)).isNull();
    assertThat(
            FailCauseOptions.from(
                new FailCauseOptions.Answer(List.of("폰부터 집기", "가".repeat(30))), picked))
        .hasSize(2);
  }
}
