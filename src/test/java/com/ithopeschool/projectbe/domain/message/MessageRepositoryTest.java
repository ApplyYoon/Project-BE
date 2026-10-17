package com.ithopeschool.projectbe.domain.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

// MessageRepository 의 찾기 메서드를 실제 DB(H2)에 넣고 꺼내 보며 확인합니다.
@DataJpaTest
class MessageRepositoryTest {

  private static final Instant SENT = Instant.parse("2026-10-12T01:00:00Z");
  private static final Pageable FIRST_TEN = PageRequest.of(0, 10);

  @Autowired private MessageRepository messageRepository;

  // 한 사람에게 메시지 count 개를 차례로 보냅니다. 번호는 보낸 순서대로 커집니다.
  private List<Message> sendMessages(Long userId, int count) {
    List<Message> sent = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      sent.add(
          messageRepository.save(
              new Message(userId, MessageKind.NUDGE, userId + "번 사람의 " + i + "번째", SENT)));
    }
    return sent;
  }

  private List<Long> idsOf(List<Message> messages) {
    return messages.stream().map(Message::getId).toList();
  }

  private List<Message> find(Long userId, Long after, Long before, Pageable pageable) {
    return messageRepository.findByUserIdAndIdGreaterThanAndIdLessThanOrderByIdDesc(
        userId, after, before, pageable);
  }

  @Test
  @DisplayName("번호가 큰 순서(최신순)로 돌려준다")
  void newestFirst() {
    List<Message> sent = sendMessages(1L, 3);

    List<Message> found = find(1L, 0L, Long.MAX_VALUE, FIRST_TEN);

    assertThat(idsOf(found))
        .containsExactly(sent.get(2).getId(), sent.get(1).getId(), sent.get(0).getId());
  }

  @Test
  @DisplayName("내 메시지만 돌려주고 다른 사람의 것은 섞이지 않는다")
  void onlyMine() {
    sendMessages(1L, 2);
    sendMessages(2L, 3);

    assertThat(find(1L, 0L, Long.MAX_VALUE, FIRST_TEN)).hasSize(2);
    assertThat(find(1L, 0L, Long.MAX_VALUE, FIRST_TEN))
        .extracting(Message::getUserId)
        .containsOnly(1L);
    assertThat(find(3L, 0L, Long.MAX_VALUE, FIRST_TEN)).isEmpty();
  }

  @Test
  @DisplayName("pageable 에서 정한 개수만큼만 돌려준다")
  void limitedByPageSize() {
    List<Message> sent = sendMessages(1L, 25);

    List<Message> found = find(1L, 0L, Long.MAX_VALUE, PageRequest.of(0, 20));

    assertThat(found).hasSize(20);
    // 가장 최근 20개 = 6번째 ~ 25번째
    assertThat(found.get(0).getId()).isEqualTo(sent.get(24).getId());
    assertThat(found.get(19).getId()).isEqualTo(sent.get(5).getId());
  }

  @Test
  @DisplayName("after 보다 번호가 큰(나중에 온) 메시지만 돌려준다. after 자신은 빠진다")
  void afterIsExclusive() {
    List<Message> sent = sendMessages(1L, 5);

    List<Message> found = find(1L, sent.get(2).getId(), Long.MAX_VALUE, FIRST_TEN);

    assertThat(idsOf(found)).containsExactly(sent.get(4).getId(), sent.get(3).getId());
  }

  @Test
  @DisplayName("before 보다 번호가 작은(먼저 온) 메시지만 돌려준다. before 자신은 빠진다")
  void beforeIsExclusive() {
    List<Message> sent = sendMessages(1L, 5);

    List<Message> found = find(1L, 0L, sent.get(2).getId(), FIRST_TEN);

    assertThat(idsOf(found)).containsExactly(sent.get(1).getId(), sent.get(0).getId());
  }

  @Test
  @DisplayName("after 와 before 를 같이 주면 그 사이 메시지만 돌려준다")
  void afterAndBefore() {
    List<Message> sent = sendMessages(1L, 6);

    List<Message> found = find(1L, sent.get(1).getId(), sent.get(4).getId(), FIRST_TEN);

    assertThat(idsOf(found)).containsExactly(sent.get(3).getId(), sent.get(2).getId());
  }

  @Test
  @DisplayName("after 범위 안에 다른 사람의 메시지가 끼어 있어도 내 것만 나온다")
  void othersInsideRangeAreSkipped() {
    List<Message> others = sendMessages(2L, 2);
    List<Message> mine = sendMessages(1L, 2);

    // 다른 사람의 첫 메시지 번호를 after 로 쓰면, 범위 안에 다른 사람의 두 번째 메시지도 들어옵니다.
    List<Message> found = find(1L, others.get(0).getId(), Long.MAX_VALUE, FIRST_TEN);

    assertThat(idsOf(found)).containsExactly(mine.get(1).getId(), mine.get(0).getId());
  }

  @Test
  @DisplayName("메시지가 하나도 없으면 빈 목록이다")
  void emptyList() {
    assertThat(find(1L, 0L, Long.MAX_VALUE, FIRST_TEN)).isEmpty();
  }

  @Test
  @DisplayName("번호와 사용자로 내 메시지 하나를 찾는다")
  void findByIdAndUserId() {
    Message mine = sendMessages(1L, 1).get(0);

    assertThat(messageRepository.findByIdAndUserId(mine.getId(), 1L)).contains(mine);
  }

  @Test
  @DisplayName("다른 사람의 메시지는 번호를 알아도 찾아지지 않는다")
  void othersMessageIsNotFound() {
    Message others = sendMessages(2L, 1).get(0);

    assertThat(messageRepository.findByIdAndUserId(others.getId(), 1L)).isEmpty();
  }

  @Test
  @DisplayName("없는 번호는 찾아지지 않는다")
  void missingId() {
    assertThat(messageRepository.findByIdAndUserId(999_999L, 1L)).isEmpty();
  }
}
