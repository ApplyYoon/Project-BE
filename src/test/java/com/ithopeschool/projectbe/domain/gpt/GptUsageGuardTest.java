package com.ithopeschool.projectbe.domain.gpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.PersistenceException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// GPT 호출 기록(이슈 #33)을 진짜 DB(H2)에 남기며 확인합니다.
// 하루 한도는 2026-10-24 에 없앴습니다. 그래서 몇 번을 불러도 막지 않고 기록만 하는지 봅니다.
// 설정: 통화 한 번 최대 300초 · 분당 400원 · 글 한 번 5원
@DataJpaTest
class GptUsageGuardTest {

  @Autowired private GptUsageRepository gptUsageRepository;
  @Autowired private TestEntityManager em;

  private static final Long ME = 1L;
  private static final Long OTHER = 2L;

  // 2026-10-17 낮 12시(한국 시간)
  private static final Instant NOON = Instant.parse("2026-10-17T03:00:00Z");

  private GptUsageGuard guardAt(Instant now) {
    return new GptUsageGuard(gptUsageRepository, Clock.fixed(now, ZoneOffset.UTC), 300, 400, 5);
  }

  // ---- 통화 ----

  @Test
  @DisplayName("통화 한 번 최대 길이는 설정값(300초) 그대로다")
  void maxCallSeconds() {
    assertThat(guardAt(NOON).maxCallSeconds()).isEqualTo(300);
  }

  @Test
  @DisplayName("하루에 통화를 여러 번 시작해도 막지 않고 모두 기록한다 (하루 한도 없음)")
  void callsAreNotLimited() {
    GptUsageGuard guard = guardAt(NOON);
    for (long callRecordId = 1; callRecordId <= 10; callRecordId++) {
      guard.recordCallStart(ME, callRecordId);
    }

    assertThat(gptUsageRepository.findByUserIdOrderById(ME))
        .hasSize(10)
        .allMatch(usage -> usage.getKind() == GptUsageKind.CALL);
  }

  @Test
  @DisplayName("통화가 끝나면 통화 시간(초)과 대략적인 비용(분당 400원, 올림)을 채운다")
  void recordsCallTimeAndCost() {
    GptUsageGuard guard = guardAt(NOON);
    guard.recordCallStart(ME, 7L);

    // 4분 1초 → 241초, 241 × 400 / 60 = 1606.67 → 1607원
    guard.recordCallFinish(ME, 7L, NOON, NOON.plusSeconds(241));
    em.flush();
    em.clear();

    GptUsage usage = gptUsageRepository.findByCallRecordIdAndUserId(7L, ME).orElseThrow();
    assertThat(usage.getKind()).isEqualTo(GptUsageKind.CALL);
    assertThat(usage.getCalledAt()).isEqualTo(NOON);
    assertThat(usage.getCallSeconds()).isEqualTo(241);
    assertThat(usage.getCostWon()).isEqualTo(1607);
  }

  @Test
  @DisplayName("FE 가 끊지 않아 5분보다 길게 통화했으면 그대로 기록한다 (비용을 숨기지 않음)")
  void longCallIsNotHidden() {
    GptUsageGuard guard = guardAt(NOON);
    guard.recordCallStart(ME, 7L);

    // 20분 통화 → 1200초, 1200 × 400 / 60 = 8000원
    guard.recordCallFinish(ME, 7L, NOON, NOON.plusSeconds(1200));
    em.flush();
    em.clear();

    GptUsage usage = gptUsageRepository.findByCallRecordIdAndUserId(7L, ME).orElseThrow();
    assertThat(usage.getCallSeconds()).isEqualTo(1200);
    assertThat(usage.getCostWon()).isEqualTo(8000);
  }

  @Test
  @DisplayName("통화 시간은 OpenAI 가 끊는 최대 길이(60분)로만 자르고, 두 번째 마무리는 값을 바꾸지 않는다")
  void callTimeIsCappedAtOneHourAndRecordedOnce() {
    GptUsageGuard guard = guardAt(NOON);
    guard.recordCallStart(ME, 7L);

    // 마무리를 3시간 뒤에 불렀어도 60분까지만
    guard.recordCallFinish(ME, 7L, NOON, NOON.plusSeconds(3 * 3600));
    guard.recordCallFinish(ME, 7L, NOON, NOON.plusSeconds(10));
    em.flush();
    em.clear();

    GptUsage usage = gptUsageRepository.findByCallRecordIdAndUserId(7L, ME).orElseThrow();
    assertThat(usage.getCallSeconds()).isEqualTo(3600);
    assertThat(usage.getCostWon()).isEqualTo(24000);
  }

  @Test
  @DisplayName("남의 통화 번호로 마무리해도 남의 기록은 바뀌지 않는다")
  void cannotFinishOthersCall() {
    GptUsageGuard guard = guardAt(NOON);
    guard.recordCallStart(OTHER, 7L);

    guard.recordCallFinish(ME, 7L, NOON, NOON.plusSeconds(60));
    em.flush();
    em.clear();

    assertThat(
            gptUsageRepository
                .findByCallRecordIdAndUserId(7L, OTHER)
                .orElseThrow()
                .getCallSeconds())
        .isNull();
  }

  // ---- 글 호출 (통화 요약 · 코치 · 계획 초안 · 상담 채팅 · 작은 AI 기능) ----

  @Test
  @DisplayName("글 호출은 몇 번을 불러도 막지 않고, 부를 때마다 5원으로 기록한다 (하루 한도 없음)")
  void textCallsAreNotLimited() {
    GptUsageGuard guard = guardAt(NOON);
    for (int i = 0; i < 30; i++) {
      guard.recordText(ME, GptUsageKind.CHAT);
    }
    guard.recordText(ME, GptUsageKind.CHECKLIST);

    assertThat(gptUsageRepository.findByUserIdOrderById(ME))
        .hasSize(31)
        .allMatch(usage -> usage.getCostWon() == 5 && usage.getCallRecordId() == null);
  }

  @Test
  @DisplayName("통화를 글 호출로 기록하려 하면 막는다 (통화는 시간을 따로 기록해야 해서)")
  void callIsNotATextCall() {
    assertThatThrownBy(() -> guardAt(NOON).recordText(ME, GptUsageKind.CALL))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ---- DB 가 한 번 더 막는 것 ----

  @Test
  @DisplayName("비용이 음수면 DB 가 막는다")
  void negativeCostIsRejected() {
    assertThatThrownBy(
            () -> em.persistAndFlush(GptUsage.textCall(ME, GptUsageKind.COACH, NOON, -1)))
        .isInstanceOf(PersistenceException.class);
  }
}
