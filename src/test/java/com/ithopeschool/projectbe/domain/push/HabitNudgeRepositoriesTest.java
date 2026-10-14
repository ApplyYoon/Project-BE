package com.ithopeschool.projectbe.domain.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.message.Message;
import com.ithopeschool.projectbe.domain.message.MessageKind;
import com.ithopeschool.projectbe.domain.message.MessageRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

// "오늘의 작전" 작업이 쓰는 찾기 두 개를 실제 DB(H2)로 확인합니다.
@DataJpaTest
class HabitNudgeRepositoriesTest {

  private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");

  @Autowired private PushSubscriptionRepository pushSubscriptionRepository;
  @Autowired private MessageRepository messageRepository;

  @Test
  @DisplayName("알림을 켠 사람의 번호는 기기가 여러 개여도 한 번씩, 번호 순서로 나온다")
  void subscribedUserIdsAreDistinct() {
    pushSubscriptionRepository.saveAndFlush(new PushSubscription(8L, "https://a", "k", "a", NOW));
    pushSubscriptionRepository.saveAndFlush(new PushSubscription(7L, "https://b", "k", "a", NOW));
    pushSubscriptionRepository.saveAndFlush(new PushSubscription(7L, "https://c", "k", "a", NOW));

    assertThat(pushSubscriptionRepository.findSubscribedUserIds()).containsExactly(7L, 8L);
  }

  @Test
  @DisplayName("그 시각 이후에 그 종류의 메시지를 보냈는지 알 수 있다 (사람·종류·시각 셋 다 맞아야 함)")
  void existsSince() {
    messageRepository.saveAndFlush(new Message(7L, MessageKind.NUDGE, "작전", NOW));

    assertThat(
            messageRepository.existsByUserIdAndKindAndSentAtGreaterThanEqual(
                7L, MessageKind.NUDGE, NOW))
        .isTrue();
    assertThat(
            messageRepository.existsByUserIdAndKindAndSentAtGreaterThanEqual(
                7L, MessageKind.NUDGE, NOW.plusSeconds(1)))
        .isFalse();
    assertThat(
            messageRepository.existsByUserIdAndKindAndSentAtGreaterThanEqual(
                7L, MessageKind.CARROT, NOW))
        .isFalse();
    assertThat(
            messageRepository.existsByUserIdAndKindAndSentAtGreaterThanEqual(
                8L, MessageKind.NUDGE, NOW))
        .isFalse();
  }
}
