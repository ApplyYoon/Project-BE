package com.ithopeschool.projectbe.domain.psychtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// 심리검사 테이블(#219)을 진짜 DB(H2)에 넣고 꺼내 봅니다.
@DataJpaTest
class PsychTestRepositoryTest {

  @Autowired private PsychTestRepository psychTestRepository;
  @Autowired private TestEntityManager em;

  private static Map<PsychItem, Integer> allAnswers(int value) {
    Map<PsychItem, Integer> answers = new EnumMap<>(PsychItem.class);
    for (PsychItem item : PsychItem.values()) {
      answers.put(item, value);
    }
    return answers;
  }

  @Test
  @DisplayName("답 35개가 딸린 테이블에 저장되고, 가장 최근 검사는 내 것 중 번호가 가장 큰 것이다")
  void savesAnswersAndFindsLatest() {
    Instant now = Instant.parse("2026-10-17T09:00:00Z");
    psychTestRepository.save(new PsychTest(1L, now, allAnswers(3)));
    psychTestRepository.save(new PsychTest(1L, now.plusSeconds(60), allAnswers(6)));
    psychTestRepository.save(new PsychTest(2L, now.plusSeconds(120), allAnswers(1)));
    em.flush();
    em.clear();

    PsychTest latest = psychTestRepository.findFirstByUserIdOrderByIdDesc(1L).orElseThrow();
    assertThat(latest.getAnswers()).hasSize(35).containsEntry(PsychItem.C5, 6);
    assertThat(psychTestRepository.findFirstByUserIdOrderByIdDesc(3L)).isEmpty();
    Number rows =
        (Number)
            em.getEntityManager()
                .createNativeQuery("select count(*) from psych_test_answers")
                .getSingleResult();
    assertThat(rows.intValue()).isEqualTo(105);
  }
}
