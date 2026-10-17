package com.ithopeschool.projectbe.domain.goalchat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 열린 '목표 만들기' 대화를 기억하는 곳(GoalChatStore)을 확인합니다. (이슈 #239)
class GoalChatStoreTest {

  private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");

  // 지금 시각을 바꿀 수 있는 시계 (2시간이 지난 뒤를 흉내 냅니다, ChatScriptStoreTest 와 같음)
  private static final class MovableClock extends Clock {
    private Instant now = NOW;

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private final MovableClock clock = new MovableClock();
  private final GoalChatStore store = new GoalChatStore(clock);

  @Test
  @DisplayName("연 사람만 이어갈 수 있고, 닫으면 끝난다")
  void ownerOnly() {
    String chatId = store.open(7L);

    assertThat(store.isOpen(chatId, 7L)).isTrue();
    assertThat(store.isOpen(chatId, 8L)).isFalse();
    assertThat(store.isOpen("없는-번호", 7L)).isFalse();
    store.close(chatId);
    assertThat(store.isOpen(chatId, 7L)).isFalse();
  }

  @Test
  @DisplayName("대화 번호는 열 때마다 다르다 (짐작할 수 없는 무작위 글자)")
  void idsDiffer() {
    assertThat(store.open(7L)).isNotEqualTo(store.open(7L));
  }

  @Test
  @DisplayName("2시간까지는 이어갈 수 있고, 그 뒤에는 사라진다")
  void expiresAfterTwoHours() {
    String chatId = store.open(7L);

    clock.now = NOW.plus(GoalChatStore.KEEP);
    assertThat(store.isOpen(chatId, 7L)).isTrue();

    clock.now = NOW.plus(GoalChatStore.KEEP).plusSeconds(1);
    assertThat(store.isOpen(chatId, 7L)).isFalse();
  }
}
