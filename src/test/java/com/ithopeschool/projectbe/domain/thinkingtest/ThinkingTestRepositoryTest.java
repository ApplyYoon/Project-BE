package com.ithopeschool.projectbe.domain.thinkingtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.bias.Weakness;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

// 생각 습관 테스트 결과를 실제 DB(H2)에 넣고 꺼내 봅니다. (딸린 테이블 두 개까지)
@DataJpaTest
class ThinkingTestRepositoryTest {

  @Autowired private ThinkingTestRepository thinkingTestRepository;

  private ThinkingTest save(Long userId, double planning, Weakness... weaknesses) {
    Map<CognitiveBias, Double> scores = new EnumMap<>(CognitiveBias.class);
    for (CognitiveBias bias : CognitiveBias.values()) {
      scores.put(bias, 4.0);
    }
    scores.put(CognitiveBias.PLANNING_FALLACY, planning);
    EnumSet<Weakness> chips = EnumSet.noneOf(Weakness.class);
    chips.addAll(java.util.List.of(weaknesses));
    return thinkingTestRepository.saveAndFlush(
        new ThinkingTest(userId, Instant.parse("2026-10-15T09:00:00Z"), 2, scores, chips));
  }

  @Test
  @DisplayName("습관 8개의 평균과 약점 칩이 그대로 저장되고 꺼내진다")
  void savesScoresAndWeaknesses() {
    Long id = save(1L, 6.3, Weakness.GAME, Weakness.SLEEP).getId();

    ThinkingTest found = thinkingTestRepository.findById(id).orElseThrow();

    assertThat(found.getScores()).hasSize(8).containsEntry(CognitiveBias.PLANNING_FALLACY, 6.3);
    assertThat(found.getWeaknesses()).containsExactlyInAnyOrder(Weakness.GAME, Weakness.SLEEP);
    assertThat(found.getTestVersion()).isEqualTo(2);
  }

  @Test
  @DisplayName("다시 하면 새 줄이 쌓이고, 가장 최근 것은 내 것 중 번호가 가장 큰 것이다")
  void latestIsMyNewest() {
    save(1L, 6.0);
    ThinkingTest second = save(1L, 3.0);
    save(2L, 7.0);

    assertThat(thinkingTestRepository.findFirstByUserIdOrderByIdDesc(1L).orElseThrow().getId())
        .isEqualTo(second.getId());
    assertThat(thinkingTestRepository.findByUserIdOrderByIdDesc(1L)).hasSize(2);
    assertThat(thinkingTestRepository.findFirstByUserIdOrderByIdDesc(3L)).isEmpty();
  }

  @Test
  @DisplayName("약점을 하나도 안 골라도 저장된다")
  void noWeaknesses() {
    Long id = save(1L, 4.0).getId();

    assertThat(thinkingTestRepository.findById(id).orElseThrow().getWeaknesses()).isEmpty();
  }
}
