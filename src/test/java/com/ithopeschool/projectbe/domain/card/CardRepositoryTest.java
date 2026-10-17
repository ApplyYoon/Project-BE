package com.ithopeschool.projectbe.domain.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// CardRepository 가 진짜 DB(H2)에서 "내 것만, 그날 후보만" 정확히 찾는지,
// 딸린 테이블(체크리스트 · 예상 실패 원인)이 보낸 순서 그대로 저장되는지 확인합니다.
@DataJpaTest
class CardRepositoryTest {

  @Autowired private CardRepository cardRepository;
  @Autowired private TestEntityManager em;

  // 2026-10-12 는 월요일입니다.
  private static final LocalDate MON_OCT_12 = LocalDate.of(2026, 10, 12);
  private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

  private Card save(Long userId, String title, LocalDate date, List<Integer> repeatDays) {
    Card card = new Card(userId, title, date, 60, List.of(new ChecklistItem("i1", title)), NOW);
    card.changeRepeatDays(repeatDays);
    return em.persistAndFlush(card);
  }

  @Test
  @DisplayName("내 카드는 번호로 찾아지고, 남의 카드 번호나 없는 번호는 빈 Optional")
  void findByIdAndUserId() {
    Card mine = save(1L, "수학", MON_OCT_12, List.of());
    Card others = save(2L, "남의 카드", MON_OCT_12, List.of());

    assertThat(cardRepository.findByIdAndUserId(mine.getId(), 1L)).contains(mine);
    // 번호는 실제로 있어도, 내 것이 아니면 없는 것과 똑같이 취급됩니다. (수정·삭제가 404 가 되는 이유)
    assertThat(cardRepository.findByIdAndUserId(others.getId(), 1L)).isEmpty();
    assertThat(cardRepository.findByIdAndUserId(99999L, 1L)).isEmpty();
  }

  @Test
  @DisplayName("10-12 의 후보: 그날 1회성 카드 + 이미 시작한 반복 카드 (다른 날 1회성 · 아직 시작 안 한 반복 · 남의 카드는 빠짐)")
  void findCandidatesOn() {
    save(1L, "그날 1회성", MON_OCT_12, List.of());
    save(1L, "다른 날 1회성", LocalDate.of(2026, 10, 13), List.of());
    // FE 주석의 예시: 10-01 시작 월·수 반복 카드, 10-20 시작 월·수 반복 카드
    save(1L, "10-01 시작 월수", LocalDate.of(2026, 10, 1), List.of(1, 3));
    save(1L, "10-20 시작 월수", LocalDate.of(2026, 10, 20), List.of(1, 3));
    // 화요일만 하는 반복 카드도 후보에는 들어옵니다. 요일은 Service 가 Card.isOnDate 로 거릅니다.
    save(1L, "10-01 시작 화", LocalDate.of(2026, 10, 1), List.of(2));
    save(2L, "남의 카드", MON_OCT_12, List.of());
    em.clear();

    List<Card> candidates = cardRepository.findCandidatesOn(1L, MON_OCT_12);

    assertThat(candidates)
        .extracting(Card::getTitle)
        .containsExactly("그날 1회성", "10-01 시작 월수", "10-01 시작 화");
    // 후보 중 그날 하는 카드만 남기면 FE 의 isCardOnDate 와 같은 결과입니다.
    assertThat(candidates.stream().filter(card -> card.isOnDate(MON_OCT_12)))
        .extracting(Card::getTitle)
        .containsExactly("그날 1회성", "10-01 시작 월수");
  }

  @Test
  @DisplayName("내 카드 전부는 만든 순서대로 나오고, 남의 카드는 섞이지 않는다")
  void findByUserIdOrderById() {
    save(1L, "수학", MON_OCT_12, List.of());
    save(2L, "남의 카드", MON_OCT_12, List.of());
    save(1L, "영어", LocalDate.of(2026, 10, 1), List.of(1));
    em.clear();

    assertThat(cardRepository.findByUserIdOrderById(1L))
        .extracting(Card::getTitle)
        .containsExactly("수학", "영어");
    assertThat(cardRepository.findByUserIdOrderById(999L)).isEmpty();
  }

  @Test
  @DisplayName("체크리스트와 예상 실패 원인은 보낸 순서 그대로 저장되고, 바꾸면 통째로 바뀐다")
  void collectionsKeepOrder() {
    Card card =
        new Card(
            1L,
            "수학",
            MON_OCT_12,
            60,
            List.of(
                new ChecklistItem("i2", "둘째로 적은 항목"),
                new ChecklistItem("i1", "첫째로 적은 항목"),
                new ChecklistItem("i3", "셋째")),
            NOW);
    card.changeExpectedFailCauses(List.of("폰을 볼 것 같다", "졸릴 것 같다"));
    Long id = em.persistAndFlush(card).getId();
    em.clear();

    Card saved = cardRepository.findById(id).orElseThrow();
    assertThat(saved.getChecklist())
        .extracting(ChecklistItem::getItemKey)
        .containsExactly("i2", "i1", "i3");
    assertThat(saved.getExpectedFailCauses()).containsExactly("폰을 볼 것 같다", "졸릴 것 같다");

    saved.changeChecklist(List.of(new ChecklistItem("i9", "하나만 남김")));
    saved.changeExpectedFailCauses(List.of());
    em.flush();
    em.clear();

    Card changed = cardRepository.findById(id).orElseThrow();
    assertThat(changed.getChecklist()).extracting(ChecklistItem::getText).containsExactly("하나만 남김");
    assertThat(changed.getExpectedFailCauses()).isEmpty();
  }
}
