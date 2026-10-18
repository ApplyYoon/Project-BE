package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.ai.dto.AnalysisRequest;
import com.ithopeschool.projectbe.domain.ai.dto.AnalysisResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// AI 자기 분석의 질문(입력 · 지침 · 칸 모양)과 답 검사를 확인합니다. GPT 는 부르지 않습니다.
class SelfAnalysisTest {

  private static final List<AnalysisRequest.Fact> FACTS =
      List.of(
          new AnalysisRequest.Fact("slotRate", "tendency", "시간대별 실행률", "아침 95% · 밤 63%", "카드 64번"),
          new AnalysisRequest.Fact(
              "hypothesisChronotype",
              "distortion",
              "아침형/저녁형 가설",
              "저녁형이라고 함 → 아침 95% · 밤 63%",
              "12일"),
          new AnalysisRequest.Fact("topFailCause", "failure", "실패 원인 1위", "폰·SNS 5번", "실패 9번"));

  private static SelfAnalysis.Answer.Section section(
      String group, String headline, String body, String... keys) {
    return new SelfAnalysis.Answer.Section(group, headline, body, List.of(keys));
  }

  private static final SelfAnalysis.Answer.Experiment EXPERIMENT =
      new SelfAnalysis.Answer.Experiment(
          "만약 학원에서 돌아오면", "폰을 다른 방에 두고 2주 해 본다", "실패 원인 1위가 폰·SNS 5번이야");

  @Test
  @DisplayName("입력에는 묶음 뜻과 숫자가 [묶음] key | 이름: 값 (근거) 모양으로 들어간다")
  void inputListsFacts() {
    String input = SelfAnalysis.input(FACTS);

    assertThat(input).contains("- distortion: 생각과 실제");
    assertThat(input).contains("[tendency] slotRate | 시간대별 실행률: 아침 95% · 밤 63% (근거: 카드 64번)");
    assertThat(input).contains("[failure] topFailCause | 실패 원인 1위: 폰·SNS 5번 (근거: 실패 9번)");
  }

  @Test
  @DisplayName("지침은 받은 숫자만 · 가설로 말하기 · 점수로 사람 평가 금지 · 깎아내리기 금지를 말한다")
  void instructionsHaveRules() {
    String instructions = SelfAnalysis.instructions();

    assertThat(instructions).contains("받은 숫자만 써");
    assertThat(instructions).contains("원인이라고 단정하지 마");
    assertThat(instructions).contains("점수로 말하지 마");
    assertThat(instructions).contains("외모·성적·능력을 깎아내리지 마");
    assertThat(SelfAnalysis.question(FACTS).name()).isEqualTo("self_analysis");
  }

  @Test
  @DisplayName("쓸 수 있는 묶음은 화면 순서(성향 → 생각과 실제 → … → 실패 원인)로, 근거 key 는 준 것만 남긴다")
  void keepsGoodSections() {
    SelfAnalysis.Answer answer =
        new SelfAnalysis.Answer(
            "아침에 더 잘 되는데 저녁형이라고 믿는 편이야",
            List.of(
                section(
                    "failure",
                    "놓친 날엔 폰이 1위야",
                    "실패 9번 중 폰·SNS 가 5번이야. 막힐 때 폰을 집는 걸 수 있어.",
                    "topFailCause"),
                section(
                    "tendency",
                    "아침 카드가 더 잘 돼",
                    "아침 95%, 밤 63%야. 밤엔 학원으로 지쳐 있을 수 있어.",
                    "slotRate",
                    "모르는키")),
            EXPERIMENT);

    AnalysisResponse.Analysis analysis = SelfAnalysis.from(answer, FACTS);

    assertThat(analysis).isNotNull();
    assertThat(analysis.summary()).isEqualTo("아침에 더 잘 되는데 저녁형이라고 믿는 편이야");
    assertThat(analysis.sections())
        .extracting(AnalysisResponse.Section::group)
        .containsExactly("tendency", "failure");
    assertThat(analysis.sections().get(0).evidenceKeys()).containsExactly("slotRate");
    assertThat(analysis.nextExperiment().thenText()).isEqualTo("폰을 다른 방에 두고 2주 해 본다");
  }

  @Test
  @DisplayName("지어낸 숫자 · 근거 없음 · 너무 긺 · 깎아내리는 말 · 같은 묶음 두 번째는 그 묶음만 버린다")
  void dropsBadSections() {
    SelfAnalysis.Answer answer =
        new SelfAnalysis.Answer(
            "아침에 더 잘 되는 편이야",
            List.of(
                section("tendency", "아침이 80% 더 잘 돼", "지어낸 숫자야.", "slotRate"),
                section("distortion", "저녁형이 아니야", "근거가 없어.", "모르는키"),
                section("failure", "놓친 날엔 폰이 1위야", "가".repeat(161), "topFailCause"),
                section("willpower", "게으른 편이야", "실패 9번이야.", "topFailCause"),
                section("tendency", "아침 카드가 더 잘 돼", "아침 95%, 밤 63%야.", "slotRate"),
                section("tendency", "두 번째 성향", "아침 95%야.", "slotRate")),
            EXPERIMENT);

    AnalysisResponse.Analysis analysis = SelfAnalysis.from(answer, FACTS);

    assertThat(analysis.sections()).hasSize(1);
    assertThat(analysis.sections().get(0).headline()).isEqualTo("아침 카드가 더 잘 돼");
  }

  @Test
  @DisplayName("남는 묶음이 없으면 null (FE 기본 문장), 요약 · 실험 제안만 틀리면 그 칸만 null")
  void nullWhenNothingLeft() {
    assertThat(SelfAnalysis.from(null, FACTS)).isNull();
    assertThat(
            SelfAnalysis.from(
                new SelfAnalysis.Answer(
                    "요약", List.of(section("tendency", "아침 70%", "지어냄.", "slotRate")), EXPERIMENT),
                FACTS))
        .isNull();

    SelfAnalysis.Answer answer =
        new SelfAnalysis.Answer(
            "하루 7시간 하는 편이야",
            List.of(section("tendency", "아침 카드가 더 잘 돼", "아침 95%야.", "slotRate")),
            new SelfAnalysis.Answer.Experiment("만약 8시가 되면", "폰을 끈다", "이유"));
    AnalysisResponse.Analysis analysis = SelfAnalysis.from(answer, FACTS);

    assertThat(analysis.summary()).isNull();
    assertThat(analysis.nextExperiment()).isNull();
    assertThat(analysis.sections()).hasSize(1);
  }

  @Test
  @DisplayName("'1위' 의 1 과 실험 기간 2주 · 14일은 준 숫자가 아니어도 된다")
  void allowsOrdinalAndExperimentDays() {
    assertThat(SelfAnalysis.checkText("폰이 1위야", 40, java.util.Set.of(), java.util.Set.of("1")))
        .isEqualTo("폰이 1위야");
    assertThat(
            SelfAnalysis.from(
                    new SelfAnalysis.Answer(
                        "요약이야",
                        List.of(section("tendency", "아침 카드가 더 잘 돼", "아침 95%야.", "slotRate")),
                        new SelfAnalysis.Answer.Experiment("만약 밤이 되면", "14일 동안 폰을 끈다", "2주만 해 보자")),
                    FACTS)
                .nextExperiment())
        .isNotNull();
  }
}
