package com.ithopeschool.projectbe.domain.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

// 구독 테이블을 실제 DB(H2)에 넣고 꺼내 봅니다.
@DataJpaTest
class PushSubscriptionRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-10-15T09:00:00Z");

  @Autowired private PushSubscriptionRepository repository;

  @Test
  @DisplayName("주소로 찾고, 내 것만 찾고, 한 사람의 기기를 모두 찾는다")
  void finds() {
    repository.saveAndFlush(new PushSubscription(1L, "https://a", "k", "a", NOW));
    repository.saveAndFlush(new PushSubscription(1L, "https://b", "k", "a", NOW));
    repository.saveAndFlush(new PushSubscription(2L, "https://c", "k", "a", NOW));

    assertThat(repository.findByEndpoint("https://c")).isPresent();
    assertThat(repository.findByEndpointAndUserId("https://c", 1L)).isEmpty();
    assertThat(repository.findByEndpointAndUserId("https://a", 1L)).isPresent();
    assertThat(repository.findByUserId(1L)).hasSize(2);
  }

  @Test
  @DisplayName("같은 주소(기기)는 두 줄이 될 수 없다")
  void endpointIsUnique() {
    repository.saveAndFlush(new PushSubscription(1L, "https://same", "k", "a", NOW));

    assertThatThrownBy(
            () -> repository.saveAndFlush(new PushSubscription(2L, "https://same", "k", "a", NOW)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
