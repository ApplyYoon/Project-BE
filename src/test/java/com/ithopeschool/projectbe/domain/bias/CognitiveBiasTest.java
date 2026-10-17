package com.ithopeschool.projectbe.domain.bias;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.care.ToneGuide;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 생각 습관(인지편향) 목록(이슈 #28)이 빠진 칸 없이, 학생에게 해도 되는 말로만 채워져 있는지 확인합니다.
// 목록에 습관을 더하거나 문장을 고치면 이 테스트가 먼저 알려 줍니다.
class CognitiveBiasTest {

  @Test
  @DisplayName("모든 생각 습관에 이름·설명·신호 단어·질문 2개 이상·해 볼 행동이 있다")
  void everyBiasIsFilledIn() {
    for (CognitiveBias bias : CognitiveBias.values()) {
      assertThat(bias.getLabel()).as(bias.name()).isNotBlank();
      assertThat(bias.getDescription()).as(bias.name()).isNotBlank();
      assertThat(bias.getSignalWords()).as(bias.name()).hasSizeGreaterThanOrEqualTo(2);
      // 질문을 돌아가며 쓰려면 두 개 이상이어야 합니다. (BiasQuestion)
      assertThat(bias.getQuestions()).as(bias.name()).hasSizeGreaterThanOrEqualTo(2);
      assertThat(bias.getSolution()).as(bias.name()).isNotBlank();
    }
  }

  @Test
  @DisplayName("질문·설명·해 볼 행동에 깎아내리거나 남과 비교하는 말이 없다 (말투 지침의 금지 표현 검사)")
  void noPutDownWords() {
    for (CognitiveBias bias : CognitiveBias.values()) {
      assertThat(ToneGuide.hasBannedExpression(bias.getDescription())).as(bias.name()).isFalse();
      assertThat(ToneGuide.hasBannedExpression(bias.getSolution())).as(bias.name()).isFalse();
      for (String question : bias.getQuestions()) {
        assertThat(ToneGuide.hasBannedExpression(question)).as(question).isFalse();
      }
    }
  }

  @Test
  @DisplayName("학생에게 보여 주는 이름은 서로 다르다")
  void labelsAreUnique() {
    Set<String> labels = new HashSet<>();
    for (CognitiveBias bias : CognitiveBias.values()) {
      assertThat(labels.add(bias.getLabel())).as(bias.getLabel()).isTrue();
    }
  }

  @Test
  @DisplayName("같은 신호 단어가 두 습관에 들어 있지 않다 (한 말이 두 질문으로 갈라지지 않게)")
  void signalWordsAreUnique() {
    Set<String> words = new HashSet<>();
    for (CognitiveBias bias : CognitiveBias.values()) {
      for (String word : bias.getSignalWords()) {
        assertThat(words.add(word)).as(word).isTrue();
      }
    }
  }

  @Test
  @DisplayName("질문은 학생에게 묻는 말이라 물음표로 끝난다")
  void questionsAreQuestions() {
    for (CognitiveBias bias : CognitiveBias.values()) {
      for (String question : bias.getQuestions()) {
        assertThat(question).as(question).endsWith("?");
      }
    }
  }
}
