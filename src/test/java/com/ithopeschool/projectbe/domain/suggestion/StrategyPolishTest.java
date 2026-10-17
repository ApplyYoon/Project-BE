package com.ithopeschool.projectbe.domain.suggestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.ai.AiQuestion;
import com.ithopeschool.projectbe.domain.care.ToneGuide;
import com.ithopeschool.projectbe.domain.suggestion.StrategyPolish.Answer;
import com.ithopeschool.projectbe.domain.suggestion.StrategyPolish.Polished;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 개선안 문장 다듬기(#51)의 질문과 "뜻이 바뀌었는지" 검사를 확인합니다. GPT 를 부르지 않습니다.
class StrategyPolishTest {

  // phone-easy-1: 공부를 시작하면 → 알림을 모두 끈다 / phone-normal-2: 쉬는 시간이 되면 → 타이머로 10분을 재고 그 안에만 폰을 본다
  private static final Strategy EASY = StrategyCatalog.find("phone-easy-1").orElseThrow();
  private static final Strategy NORMAL = StrategyCatalog.find("phone-normal-2").orElseThrow();
  private static final List<Strategy> TWO = List.of(EASY, NORMAL);

  private static Answer.Item item(String ifText, String thenText) {
    return new Answer.Item(ifText, thenText);
  }

  @Test
  @DisplayName("입력에는 학생의 상황 한 줄과 전략 문장(강도 · 만약 → 한다)만 들어간다")
  void input() {
    assertThat(StrategyPolish.input("최근 30일 중 '폰·SNS' 때문에 못 채운 날이 5일, 주로 화요일", TWO))
        .isEqualTo(
            """
            학생의 상황: 최근 30일 중 '폰·SNS' 때문에 못 채운 날이 5일, 주로 화요일
            전략 (이 순서대로 하나씩):
            1. 쉬움: 공부를 시작하면 → 알림을 모두 끈다
            2. 보통: 쉬는 시간이 되면 → 타이머로 10분을 재고 그 안에만 폰을 본다""");
  }

  @Test
  @DisplayName("질문 이름은 strategy_polish 이고, 지침에 뜻·숫자를 바꾸지 말라는 규칙이 있다")
  void question() {
    AiQuestion question = StrategyPolish.question("상황", TWO);

    assertThat(question.name()).isEqualTo("strategy_polish");
    assertThat(question.schema().get("required")).isEqualTo(List.of("items"));
    assertThat(question.instructions())
        .contains(ToneGuide.NO_PUT_DOWN_RULE)
        .contains("지시가 아니야")
        .contains("새 숫자를 만들지 마")
        .contains("새 행동을 더하지 마");
  }

  @Test
  @DisplayName("검사를 통과한 문장은 다듬은 문장을 쓴다 (숫자는 순서가 바뀌어도 같으면 된다)")
  void polishedIsUsed() {
    List<Polished> result =
        StrategyPolish.from(
            new Answer(
                List.of(
                    item(" 화요일에 공부를 시작하면 ", "알림을 모두 끈다"),
                    item("쉬는 시간이 오면", "10분 타이머를 맞추고 그동안만 폰을 본다"))),
            TWO);

    assertThat(result)
        .containsExactly(
            new Polished("화요일에 공부를 시작하면", "알림을 모두 끈다", true),
            new Polished("쉬는 시간이 오면", "10분 타이머를 맞추고 그동안만 폰을 본다", true));
  }

  @Test
  @DisplayName("숫자가 바뀌거나 새로 생긴 전략은 원래 문장을 쓴다 (다른 전략은 그대로 다듬은 문장)")
  void changedNumberUsesOriginal() {
    List<Polished> result =
        StrategyPolish.from(
            new Answer(
                List.of(
                    item("공부를 시작하면 3분 안에", "알림을 모두 끈다"),
                    item("쉬는 시간이 되면", "타이머로 10분을 재고 그 안에만 폰을 본다"))),
            TWO);
    assertThat(result.get(0)).isEqualTo(new Polished(EASY.ifText(), EASY.thenText(), false));
    assertThat(result.get(1).polished()).isTrue();

    List<Polished> changed =
        StrategyPolish.from(
            new Answer(List.of(item("공부를 시작하면", "알림을 끈다"), item("쉬는 시간이 되면", "30분만 폰을 본다"))), TWO);
    assertThat(changed.get(1)).isEqualTo(new Polished(NORMAL.ifText(), NORMAL.thenText(), false));
  }

  @Test
  @DisplayName("비었거나 40자를 넘거나 깎아내리는 말이 있는 전략은 원래 문장을 쓴다")
  void unsafeTextUsesOriginal() {
    List<Polished> result =
        StrategyPolish.from(
            new Answer(List.of(item(" ", "알림을 모두 끈다"), item("쉬는 시간이 되면", "가".repeat(41)))), TWO);
    assertThat(result).extracting(Polished::polished).containsExactly(false, false);

    assertThat(
            StrategyPolish.from(
                new Answer(List.of(item("게으르게 굴지 말고 공부를 시작하면", "알림을 모두 끈다"), item(null, "x"))),
                TWO))
        .extracting(Polished::polished)
        .containsExactly(false, false);
  }

  @Test
  @DisplayName("답이 없거나 개수가 다르면 모두 원래 문장이다")
  void noAnswerUsesOriginals() {
    List<Polished> originals =
        List.of(
            new Polished(EASY.ifText(), EASY.thenText(), false),
            new Polished(NORMAL.ifText(), NORMAL.thenText(), false));

    assertThat(StrategyPolish.from(null, TWO)).isEqualTo(originals);
    assertThat(StrategyPolish.from(new Answer(null), TWO)).isEqualTo(originals);
    assertThat(StrategyPolish.from(new Answer(List.of(item("공부를 시작하면", "알림을 끈다"))), TWO))
        .isEqualTo(originals);
  }
}
