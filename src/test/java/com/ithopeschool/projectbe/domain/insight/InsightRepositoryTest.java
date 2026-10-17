package com.ithopeschool.projectbe.domain.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// 인사이트를 실제 DB(H2)에 넣고 꺼내 봅니다.
@DataJpaTest
class InsightRepositoryTest {

  @Autowired private InsightRepository insightRepository;
  @Autowired private TestEntityManager em;

  private static final LocalDate OCT_3 = LocalDate.of(2026, 10, 3);
  private static final LocalDate OCT_10 = LocalDate.of(2026, 10, 10);

  private Insight save(Long userId, String key, LocalDate shownDate, InsightResponse reaction) {
    Insight insight =
        new Insight(userId, key + " 문장", key, false, shownDate, List.of(OCT_3, OCT_10));
    insight.react(reaction);
    return insightRepository.saveAndFlush(insight);
  }

  @Test
  @DisplayName("문장·종류·가설 여부·보여 준 날과 근거 날짜 여러 개가 저장되고, 반응은 비어 있다")
  void savesWithEvidenceDates() {
    Insight saved =
        insightRepository.saveAndFlush(
            new Insight(
                1L, "오전에 더 오래 집중했어요", "morning-vs-night", true, OCT_10, List.of(OCT_3, OCT_10)));
    em.clear();

    Insight found = insightRepository.findById(saved.getId()).orElseThrow();
    assertThat(found.getSentence()).isEqualTo("오전에 더 오래 집중했어요");
    assertThat(found.getInsightKey()).isEqualTo("morning-vs-night");
    assertThat(found.isHypothesis()).isTrue();
    assertThat(found.getShownDate()).isEqualTo(OCT_10);
    assertThat(found.getEvidenceDates()).containsExactlyInAnyOrder(OCT_3, OCT_10);
    assertThat(found.getReaction()).isNull();
  }

  @Test
  @DisplayName("근거 날짜가 없으면 만들 수 없다")
  void needsEvidenceDates() {
    assertThatThrownBy(() -> new Insight(1L, "문장", "k", false, OCT_10, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("반응을 다시 누르면 바뀐다")
  void reactionCanChange() {
    Insight insight = save(1L, "k", OCT_10, InsightResponse.WRONG);
    insight.react(InsightResponse.SURPRISED);
    insightRepository.saveAndFlush(insight);
    em.clear();

    assertThat(insightRepository.findById(insight.getId()).orElseThrow().getReaction())
        .isEqualTo(InsightResponse.SURPRISED);
  }

  @Test
  @DisplayName("내 번호로 찾으면 내 것만 나오고, 남의 인사이트는 없다고 나온다")
  void findsOnlyMine() {
    Insight mine = save(1L, "k", OCT_10, null);
    Insight others = save(2L, "k", OCT_10, null);

    assertThat(insightRepository.findByIdAndUserId(mine.getId(), 1L)).isPresent();
    assertThat(insightRepository.findByIdAndUserId(others.getId(), 1L)).isEmpty();
  }

  @Test
  @DisplayName("반응 수는 내 것만 센다. 안 누른 것은 반응 수에 들지 않는다")
  void countsMyReactions() {
    save(1L, "a", OCT_10, InsightResponse.SURPRISED);
    save(1L, "b", OCT_10, InsightResponse.SURPRISED);
    save(1L, "c", OCT_10, InsightResponse.WRONG);
    save(1L, "d", OCT_10, null);
    save(2L, "e", OCT_10, InsightResponse.SURPRISED);

    assertThat(insightRepository.countByUserIdAndReactionIsNotNull(1L)).isEqualTo(3);
    assertThat(insightRepository.countByUserIdAndReaction(1L, InsightResponse.SURPRISED))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("내 인사이트 목록은 최근에 보여 준 것이 앞이다")
  void listsNewestFirst() {
    save(1L, "old", OCT_3, null);
    save(1L, "new", OCT_10, null);
    save(2L, "others", OCT_10, null);

    assertThat(insightRepository.findByUserIdOrderByShownDateDescIdDesc(1L))
        .extracting(Insight::getInsightKey)
        .containsExactly("new", "old");
  }
}
