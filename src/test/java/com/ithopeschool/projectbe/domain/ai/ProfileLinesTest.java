package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.care.ToneGuide;
import com.ithopeschool.projectbe.domain.psychtest.PsychScale;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 성향 요약 3줄(#237 표 9번)의 척도 고르기 · GPT 에게 주는 글 · 답 검사를 확인합니다. GPT 를 부르지 않습니다.
class ProfileLinesTest {

  private static Map<PsychScale, Double> scores(double... values) {
    Map<PsychScale, Double> scores = new EnumMap<>(PsychScale.class);
    PsychScale[] scales = PsychScale.values();
    for (int i = 0; i < scales.length; i++) {
      scores.put(scales[i], values[i]);
    }
    return scores;
  }

  @Test
  @DisplayName("가운데(4)에서 가장 먼 척도 3개를 고르고, 같으면 척도 순서다 (주석의 예시)")
  void pick() {
    // 시작 저항 6 · 주의 분산 5 · 계획 낙관 2 · 나머지 4
    List<ProfileLines.Pick> picks = ProfileLines.pick(scores(6, 5, 2, 4, 4, 4, 4));

    assertThat(picks)
        .extracting(ProfileLines.Pick::scale)
        .containsExactly(
            PsychScale.START_RESISTANCE, PsychScale.PLAN_OPTIMISM, PsychScale.DISTRACTION);
  }

  @Test
  @DisplayName("모두 가운데면 척도 순서대로 앞의 3개다")
  void allMiddle() {
    assertThat(ProfileLines.pick(scores(4, 4, 4, 4, 4, 4, 4)))
        .extracting(ProfileLines.Pick::scale)
        .containsExactly(
            PsychScale.START_RESISTANCE, PsychScale.DISTRACTION, PsychScale.PLAN_OPTIMISM);
  }

  @Test
  @DisplayName("입력에는 척도 이름 · 높낮이 · 무엇을 재는지만 들어간다 (점수 숫자도 넣지 않음)")
  void input() {
    String input = ProfileLines.input(ProfileLines.pick(scores(6, 5, 2, 4, 4, 4, 4)));

    assertThat(input)
        .isEqualTo(
            """
            척도 (이 순서대로 한 줄씩):
            1. 시작 저항 — 높음 (마음먹고 시작하기까지 꾸물거림)
            2. 계획 낙관 — 낮음 (계획을 과하게 세움)
            3. 주의 분산 — 높음 (공부 중 폰·딴생각에 흔들림)""");
  }

  @Test
  @DisplayName("질문 이름은 profile_lines 이고, 지침에 가설로 쓰고 이름을 붙이지 말라는 규칙이 있다")
  void question() {
    AiQuestion question = ProfileLines.question(ProfileLines.pick(scores(4, 4, 4, 4, 4, 4, 4)));

    assertThat(question.name()).isEqualTo("profile_lines");
    assertThat(question.schema().get("required")).isEqualTo(List.of("lines"));
    assertThat(question.instructions())
        .contains(ToneGuide.NO_PUT_DOWN_RULE)
        .contains("가설로 써")
        .contains("이름(○○형")
        .contains("숫자는 쓰지 마");
  }

  @Test
  @DisplayName("3줄이 모두 쓸 수 있으면 공백을 지워 돌려주고, 줄 수가 다르거나 하나라도 비었거나 길거나 숫자 · 금지 표현이면 null")
  void from() {
    assertThat(ProfileLines.from(new ProfileLines.Answer(List.of(" 하나 ", "둘", "셋"))))
        .containsExactly("하나", "둘", "셋");
    assertThat(ProfileLines.from(new ProfileLines.Answer(List.of("가".repeat(60), "둘", "셋"))))
        .hasSize(3);

    assertThat(ProfileLines.from(new ProfileLines.Answer(List.of("하나", "둘")))).isNull();
    assertThat(ProfileLines.from(new ProfileLines.Answer(List.of("하나", " ", "셋")))).isNull();
    assertThat(ProfileLines.from(new ProfileLines.Answer(List.of("가".repeat(61), "둘", "셋"))))
        .isNull();
    assertThat(ProfileLines.from(new ProfileLines.Answer(List.of("상위 10% 의 집중력", "둘", "셋"))))
        .isNull();
    assertThat(ProfileLines.from(new ProfileLines.Answer(List.of("게으른 편이야", "둘", "셋")))).isNull();
    assertThat(ProfileLines.from(new ProfileLines.Answer(Arrays.asList("하나", null, "셋")))).isNull();
    assertThat(ProfileLines.from(new ProfileLines.Answer(null))).isNull();
    assertThat(ProfileLines.from(null)).isNull();
  }
}
