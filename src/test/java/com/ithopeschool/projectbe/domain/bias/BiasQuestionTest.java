package com.ithopeschool.projectbe.domain.bias;

import static com.ithopeschool.projectbe.domain.bias.CognitiveBias.EMOTIONAL_REASONING;
import static com.ithopeschool.projectbe.domain.bias.CognitiveBias.EXTERNAL_BLAME;
import static com.ithopeschool.projectbe.domain.bias.CognitiveBias.FUTURE_SELF;
import static com.ithopeschool.projectbe.domain.bias.CognitiveBias.PLANNING_FALLACY;
import static com.ithopeschool.projectbe.domain.bias.CognitiveBias.WHAT_THE_HELL;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 오늘 통화에서 할 생각 습관 질문을 고르는 규칙(이슈 #28)을 확인합니다. 테스트 이름의 번호는 BiasQuestion 주석의 규칙 번호입니다.
class BiasQuestionTest {

  // 최근 기록이 없을 때
  private static final List<CognitiveBias> NO_FOUND = List.of();
  private static final List<String> NO_ASKED = List.of();

  @Test
  @DisplayName("0) 위로하는 통화에서는 무엇이 있어도 묻지 않는다")
  void supportiveCallAsksNothing() {
    assertThat(
            BiasQuestion.pick(
                true, "tooMuch", 60, List.of(PLANNING_FALLACY, PLANNING_FALLACY), NO_ASKED))
        .isNull();
  }

  @Test
  @DisplayName("1) 최근에 2번 이상 보인 습관이 어제 이유보다 먼저다")
  void repeatedBiasComesFirst() {
    BiasQuestion question =
        BiasQuestion.pick(
            false,
            "phone",
            60,
            List.of(PLANNING_FALLACY, EXTERNAL_BLAME, PLANNING_FALLACY),
            NO_ASKED);

    assertThat(question.bias()).isEqualTo(PLANNING_FALLACY);
  }

  @Test
  @DisplayName("1) 2번 이상 보인 습관이 여럿이면 더 많이 보인 것, 횟수가 같으면 더 최근에 보인 것")
  void mostRepeatedThenMostRecent() {
    // 상황 탓하기 3번 > 계획 오류 2번
    assertThat(
            BiasQuestion.mostRepeated(
                List.of(
                    PLANNING_FALLACY,
                    EXTERNAL_BLAME,
                    PLANNING_FALLACY,
                    EXTERNAL_BLAME,
                    EXTERNAL_BLAME)))
        .isEqualTo(EXTERNAL_BLAME);
    // 둘 다 2번이면 목록 앞(더 최근)에 있는 계획 오류
    assertThat(
            BiasQuestion.mostRepeated(
                List.of(PLANNING_FALLACY, EXTERNAL_BLAME, EXTERNAL_BLAME, PLANNING_FALLACY)))
        .isEqualTo(PLANNING_FALLACY);
    // 1번씩만 보였으면 "자주 보인 습관" 이 없습니다.
    assertThat(BiasQuestion.mostRepeated(List.of(PLANNING_FALLACY, EXTERNAL_BLAME))).isNull();
  }

  @Test
  @DisplayName("2) 어제 이유가 계획 과다·하기 싫음·폰이면 이어지는 습관을 고른다")
  void missReasonLeadsToABias() {
    assertThat(BiasQuestion.pick(false, "tooMuch", null, NO_FOUND, NO_ASKED).bias())
        .isEqualTo(PLANNING_FALLACY);
    assertThat(BiasQuestion.pick(false, "unwilling", null, NO_FOUND, NO_ASKED).bias())
        .isEqualTo(EMOTIONAL_REASONING);
    assertThat(BiasQuestion.pick(false, "phone", null, NO_FOUND, NO_ASKED).bias())
        .isEqualTo(FUTURE_SELF);
  }

  @Test
  @DisplayName("2) 피곤·아픔·약속·기타는 정말 어쩔 수 없던 일일 수 있어서 습관으로 잇지 않는다")
  void unavoidableReasonsAreNotBiases() {
    for (String reason : List.of("tired", "sick", "event", "etc")) {
      assertThat(BiasQuestion.pick(false, reason, null, NO_FOUND, NO_ASKED)).as(reason).isNull();
    }
  }

  @Test
  @DisplayName("3) 요즘 시작이 15분 넘게 늦으면 '미래의 나 믿기' (15분까지는 아님)")
  void lateStartLeadsToFutureSelf() {
    assertThat(BiasQuestion.pick(false, "tired", 16, NO_FOUND, NO_ASKED).bias())
        .isEqualTo(FUTURE_SELF);
    assertThat(BiasQuestion.pick(false, "tired", 15, NO_FOUND, NO_ASKED)).isNull();
    assertThat(BiasQuestion.pick(false, null, null, NO_FOUND, NO_ASKED)).isNull();
  }

  @Test
  @DisplayName("4) 한 번만 보인 습관은 어제 이유·시작 지연이 없을 때 가장 최근 것을 고른다")
  void onceSeenBiasIsTheLastChoice() {
    List<CognitiveBias> seenOnce = List.of(WHAT_THE_HELL, EXTERNAL_BLAME);

    assertThat(BiasQuestion.pick(false, null, null, seenOnce, NO_ASKED).bias())
        .isEqualTo(WHAT_THE_HELL);
    // 어제 이유가 있으면 그쪽이 먼저입니다.
    assertThat(BiasQuestion.pick(false, "tooMuch", null, seenOnce, NO_ASKED).bias())
        .isEqualTo(PLANNING_FALLACY);
  }

  @Test
  @DisplayName("같은 질문이 연달아 나오지 않게, 최근에 안 한 질문을 고른다")
  void questionsRotate() {
    BiasQuestion first = BiasQuestion.pick(false, "tooMuch", null, NO_FOUND, NO_ASKED);
    BiasQuestion second = BiasQuestion.pick(false, "tooMuch", null, NO_FOUND, List.of(first.key()));

    assertThat(first.index()).isZero();
    assertThat(second.index()).isEqualTo(1);
    assertThat(second.text()).isNotEqualTo(first.text());
  }

  @Test
  @DisplayName("질문을 전부 했으면 가장 오래전에 한 것을 다시 쓴다")
  void allAskedMeansTheOldestOne() {
    // 최근 것이 앞입니다. 1번 질문을 방금, 0번 질문을 그 전에 했습니다.
    List<String> asked = List.of("PLANNING_FALLACY-1", "FUTURE_SELF-0", "PLANNING_FALLACY-0");

    assertThat(BiasQuestion.questionOf(PLANNING_FALLACY, asked).index()).isZero();
  }

  @Test
  @DisplayName("다른 습관의 질문을 한 것은 이 습관의 순서에 영향을 주지 않는다")
  void otherBiasesQuestionsDoNotCount() {
    assertThat(
            BiasQuestion.questionOf(PLANNING_FALLACY, List.of("FUTURE_SELF-0", "FUTURE_SELF-1"))
                .index())
        .isZero();
  }

  @Test
  @DisplayName("기록에 남길 이름(key)은 '습관-번호' 모양이고, 문장은 목록의 질문이다")
  void keyAndText() {
    BiasQuestion question = new BiasQuestion(PLANNING_FALLACY, 1);

    assertThat(question.key()).isEqualTo("PLANNING_FALLACY-1");
    assertThat(question.text()).isEqualTo(PLANNING_FALLACY.getQuestions().get(1));
  }

  @Test
  @DisplayName("5) 실제 기록이 아무것도 없으면 가입 테스트에서 가장 많이 고른 습관을 확인한다")
  void signupTestIsTheFirstGuess() {
    List<CognitiveBias> selfReported = List.of(CognitiveBias.ALL_OR_NOTHING, PLANNING_FALLACY);

    assertThat(BiasQuestion.pick(false, null, null, NO_FOUND, NO_ASKED, selfReported).bias())
        .isEqualTo(CognitiveBias.ALL_OR_NOTHING);
  }

  @Test
  @DisplayName("5) 가입 테스트는 실제 기록(보인 습관·어제 이유·시작 지연)보다 뒤다")
  void realRecordsComeFirst() {
    List<CognitiveBias> selfReported = List.of(CognitiveBias.ALL_OR_NOTHING);

    assertThat(BiasQuestion.pick(false, "tooMuch", null, NO_FOUND, NO_ASKED, selfReported).bias())
        .isEqualTo(PLANNING_FALLACY);
    assertThat(BiasQuestion.pick(false, null, 30, NO_FOUND, NO_ASKED, selfReported).bias())
        .isEqualTo(FUTURE_SELF);
    assertThat(
            BiasQuestion.pick(false, null, null, List.of(WHAT_THE_HELL), NO_ASKED, selfReported)
                .bias())
        .isEqualTo(WHAT_THE_HELL);
  }

  @Test
  @DisplayName("0) 위로하는 통화에서는 가입 테스트가 있어도 묻지 않는다")
  void supportiveIgnoresTheSignupTest() {
    assertThat(
            BiasQuestion.pick(
                true, null, null, NO_FOUND, NO_ASKED, List.of(CognitiveBias.ALL_OR_NOTHING)))
        .isNull();
  }
}
