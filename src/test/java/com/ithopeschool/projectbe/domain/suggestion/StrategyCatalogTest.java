package com.ithopeschool.projectbe.domain.suggestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.care.MissReason;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 전략 카탈로그 테스트입니다. (이슈 #50 의 완료 조건을 검사합니다) 문장을 고치거나 더할 때 모양이 틀어지지 않았는지 봅니다.
class StrategyCatalogTest {

  // 문장 한 부분의 최대 길이. 화면 카드 한 줄에 들어가게 짧게 둡니다.
  private static final int MAX_PART_LENGTH = 30;

  @Test
  @DisplayName("전략은 30 ~ 50개다")
  void hasThirtyToFiftyStrategies() {
    assertThat(StrategyCatalog.ALL).hasSizeBetween(30, 50);
  }

  @Test
  @DisplayName("아픔을 뺀 이유 6가지마다 쉬움 · 보통 · 강함이 1개 이상 있다")
  void everyCauseHasEveryLevel() {
    for (MissReason cause : MissReason.values()) {
      for (SuggestionLevel level : SuggestionLevel.values()) {
        List<Strategy> strategies = StrategyCatalog.of(cause, level);
        if (cause == MissReason.SICK) {
          assertThat(strategies).as("아픔 · %s", level).isEmpty();
        } else {
          assertThat(strategies).as("%s · %s", cause, level).isNotEmpty();
        }
      }
    }
  }

  @Test
  @DisplayName("모든 전략이 '만약 ~하면 → ~한다' 문장이고, 부분마다 30자 이내다")
  void everyStrategyIsIfThenSentence() {
    for (Strategy strategy : StrategyCatalog.ALL) {
      assertThat(strategy.ifText()).as("%s 의 ifText", strategy.key()).endsWith("면");
      assertThat(strategy.thenText()).as("%s 의 thenText", strategy.key()).endsWith("다");
      assertThat(strategy.ifText().length())
          .as("%s 의 ifText 길이", strategy.key())
          .isLessThanOrEqualTo(MAX_PART_LENGTH);
      assertThat(strategy.thenText().length())
          .as("%s 의 thenText 길이", strategy.key())
          .isLessThanOrEqualTo(MAX_PART_LENGTH);
    }
  }

  @Test
  @DisplayName("이름(key)은 '이유-강도-번호' 모양이고 겹치지 않는다")
  void keysAreUniqueAndFollowPattern() {
    Set<String> keys = new HashSet<>();
    for (Strategy strategy : StrategyCatalog.ALL) {
      assertThat(strategy.key())
          .startsWith(strategy.cause().toValue() + "-" + strategy.level().toValue() + "-");
      assertThat(keys.add(strategy.key())).as("겹친 이름 %s", strategy.key()).isTrue();
    }
  }

  @Test
  @DisplayName("같은 이유 안에서 같은 문장이 두 번 나오지 않는다")
  void noDuplicateSentenceWithinCause() {
    Set<String> sentences = new HashSet<>();
    for (Strategy strategy : StrategyCatalog.ALL) {
      String sentence = strategy.cause() + " " + strategy.ifText() + " → " + strategy.thenText();
      assertThat(sentences.add(sentence)).as("겹친 문장 %s", sentence).isTrue();
    }
  }

  @Test
  @DisplayName("이름으로 찾을 수 있고, 없는 이름이면 비어 있다")
  void findsByKey() {
    Strategy phone = StrategyCatalog.find("phone-easy-1").orElseThrow();
    assertThat(phone.cause()).isEqualTo(MissReason.PHONE);
    assertThat(phone.level()).isEqualTo(SuggestionLevel.EASY);
    assertThat(phone.thenText()).isEqualTo("알림을 모두 끈다");

    assertThat(StrategyCatalog.find("tooMuch-hard-1")).isPresent();
    assertThat(StrategyCatalog.find("sick-easy-1")).isEmpty();
  }
}
