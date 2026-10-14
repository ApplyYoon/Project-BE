package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.care.MissReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// GPT 가 정리한 통화를 칸마다 검사하는 규칙(이슈 #29 "정해진 값이 아니면 그 칸은 비워 둔다")을 확인합니다.
class CallReviewTest {

  // 모든 칸이 제대로 채워진 답. 테스트마다 한 칸만 바꿔서 씁니다.
  private static CallReview.Raw raw(
      String summary, String bias, String evidence, String promiseIf, String promiseThen) {
    return new CallReview.Raw(
        false,
        summary,
        bias,
        evidence,
        "처음에 몇 분이면 될 거라고 생각했어?",
        "tooMuch",
        promiseIf,
        promiseThen,
        true,
        3,
        120);
  }

  private static final CallReview.Raw GOOD =
      raw("계획이 많았다고 함.", "PLANNING_FALLACY", "금방 할 줄 알았다고 함", "학원에서 오면", "수학 한 단원");

  @Test
  @DisplayName("제대로 된 답은 그대로 옮긴다 (습관 이름 → 목록의 습관, 이유 글자 → MissReason)")
  void goodAnswerIsKept() {
    CallReview review = CallReview.from(GOOD);

    assertThat(review.crisis()).isFalse();
    assertThat(review.summary()).isEqualTo("계획이 많았다고 함.");
    assertThat(review.foundBias()).isEqualTo(CognitiveBias.PLANNING_FALLACY);
    assertThat(review.foundEvidence()).isEqualTo("금방 할 줄 알았다고 함");
    assertThat(review.actualQuestion()).isEqualTo("처음에 몇 분이면 될 거라고 생각했어?");
    assertThat(review.missReason()).isEqualTo(MissReason.TOO_MUCH);
    assertThat(review.promiseIf()).isEqualTo("학원에서 오면");
    assertThat(review.promiseThen()).isEqualTo("수학 한 단원");
    assertThat(review.promiseKept()).isTrue();
    assertThat(review.mood()).isEqualTo(3);
    assertThat(review.expectedMinutes()).isEqualTo(120);
  }

  @Test
  @DisplayName("목록에 없는 습관 이름이면 습관과 근거를 같이 비운다")
  void unknownBiasIsDropped() {
    CallReview review = CallReview.from(raw("요약", "LAZY", "근거", "A", "B"));

    assertThat(review.foundBias()).isNull();
    assertThat(review.foundEvidence()).isNull();
  }

  @Test
  @DisplayName("습관이 없으면 근거만 따로 남기지 않는다")
  void evidenceNeedsABias() {
    assertThat(CallReview.from(raw("요약", null, "근거", "A", "B")).foundEvidence()).isNull();
  }

  @Test
  @DisplayName("정해진 이유 글자가 아니면 이유를 비운다")
  void unknownMissReasonIsDropped() {
    assertThat(CallReview.missReasonOf("tooMuch")).isEqualTo(MissReason.TOO_MUCH);
    assertThat(CallReview.missReasonOf("phone")).isEqualTo(MissReason.PHONE);
    assertThat(CallReview.missReasonOf("TOO_MUCH")).isNull();
    assertThat(CallReview.missReasonOf("lazy")).isNull();
    assertThat(CallReview.missReasonOf(null)).isNull();
  }

  @Test
  @DisplayName("약속은 '만약' 과 '한다' 가 다 있어야 남는다")
  void promiseNeedsBothParts() {
    CallReview onlyIf = CallReview.from(raw("요약", null, null, "학원에서 오면", "  "));
    CallReview onlyThen = CallReview.from(raw("요약", null, null, null, "수학 한 단원"));

    assertThat(onlyIf.promiseIf()).isNull();
    assertThat(onlyIf.promiseThen()).isNull();
    assertThat(onlyThen.promiseIf()).isNull();
    assertThat(onlyThen.promiseThen()).isNull();
  }

  @Test
  @DisplayName("글은 앞뒤 빈칸을 지우고, 비었거나 너무 길면 비운다 (요약 300 · 근거 100 · 약속 50자)")
  void textsAreTrimmedAndLimited() {
    assertThat(CallReview.from(raw("  요약  ", null, null, "A", "B")).summary()).isEqualTo("요약");
    assertThat(CallReview.from(raw("   ", null, null, "A", "B")).summary()).isNull();
    assertThat(CallReview.from(raw("가".repeat(300), null, null, "A", "B")).summary()).hasSize(300);
    assertThat(CallReview.from(raw("가".repeat(301), null, null, "A", "B")).summary()).isNull();
    assertThat(
            CallReview.from(raw("요약", "PLANNING_FALLACY", "가".repeat(101), "A", "B"))
                .foundEvidence())
        .isNull();
    assertThat(CallReview.from(raw("요약", null, null, "가".repeat(51), "B")).promiseIf()).isNull();
  }

  @Test
  @DisplayName("요약·근거·질문에 깎아내리는 말이 있으면 그 칸을 비운다 (학생에게 보여 주는 글)")
  void putDownsAreDropped() {
    CallReview review =
        CallReview.from(raw("계획을 못 지켜서 한심하다고 함.", "OVERGENERALIZING", "자기가 게으르다고 함", "A", "B"));

    assertThat(review.summary()).isNull();
    assertThat(review.foundEvidence()).isNull();
    // 습관 자체는 남깁니다. 다음 통화에서 다시 확인하는 데 씁니다.
    assertThat(review.foundBias()).isEqualTo(CognitiveBias.OVERGENERALIZING);
  }

  @Test
  @DisplayName("기분은 1 ~ 5, 예상 순공은 0 ~ 1440분이 아니면 비운다")
  void numbersMustBeInRange() {
    assertThat(CallReview.inRange(0, 1, 5)).isNull();
    assertThat(CallReview.inRange(6, 1, 5)).isNull();
    assertThat(CallReview.inRange(5, 1, 5)).isEqualTo(5);
    assertThat(CallReview.inRange(1441, 0, 1440)).isNull();
    assertThat(CallReview.inRange(null, 0, 1440)).isNull();
  }

  @Test
  @DisplayName("위기 표현 칸은 true 일 때만 true 다 (빠졌으면 false)")
  void crisisOnlyWhenTrue() {
    assertThat(
            CallReview.from(
                    new CallReview.Raw(
                        true, null, null, null, null, null, null, null, null, null, null))
                .crisis())
        .isTrue();
    assertThat(
            CallReview.from(
                    new CallReview.Raw(
                        null, null, null, null, null, null, null, null, null, null, null))
                .crisis())
        .isFalse();
  }
}
