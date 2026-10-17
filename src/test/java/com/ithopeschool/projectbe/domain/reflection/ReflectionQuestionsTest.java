package com.ithopeschool.projectbe.domain.reflection;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.care.MissReason;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 성찰일기 질문 세트 테스트입니다. 문장을 고치거나 더할 때 모양이 틀어지지 않았는지 봅니다.
class ReflectionQuestionsTest {

  // 테이블의 질문 칸 길이 (question1 ~ question3)
  private static final int MAX_LENGTH = 100;

  @Test
  @DisplayName("아픔을 뺀 이유마다 질문이 3개이고, 모두 100자 안의 물음표로 끝나는 문장이다")
  void everyReasonButSickHasThreeQuestions() {
    for (MissReason reason : MissReason.values()) {
      if (reason == MissReason.SICK) {
        continue;
      }
      List<String> questions = ReflectionQuestions.of(reason);
      assertThat(questions).as("%s 의 질문 수", reason).hasSize(ReflectionQuestions.COUNT);
      for (String question : questions) {
        assertThat(question)
            .as("%s 의 질문", reason)
            .endsWith("?")
            .hasSizeLessThanOrEqualTo(MAX_LENGTH);
      }
      // 같은 질문이 두 번 들어가면 답 칸이 겹칩니다.
      assertThat(questions).as("%s 의 질문", reason).doesNotHaveDuplicates();
    }
  }

  @Test
  @DisplayName("아픔은 질문 세트가 없다 (처방하지 않는다)")
  void sickHasNoQuestions() {
    assertThat(ReflectionQuestions.of(MissReason.SICK)).isEmpty();
  }
}
