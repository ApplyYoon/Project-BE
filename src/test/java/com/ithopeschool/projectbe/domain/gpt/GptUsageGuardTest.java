package com.ithopeschool.projectbe.domain.gpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import jakarta.persistence.PersistenceException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// GPT 비용 지키기(이슈 #33)를 진짜 DB(H2)에 기록하며 확인합니다.
// 한도: 하루 통화 3번 · 하루 통화 15분 · 통화 한 번 최대 300초 · 하루 글 호출 2번 · 분당 400원 · 글 한 번 5원
@DataJpaTest
class GptUsageGuardTest {

  @Autowired private GptUsageRepository gptUsageRepository;
  @Autowired private TestEntityManager em;

  private static final Long ME = 1L;
  private static final Long OTHER = 2L;

  // 2026-10-17 낮 12시(한국 시간) → 오늘은 10/17
  private static final Instant NOON = Instant.parse("2026-10-17T03:00:00Z");
  // 2026-10-18 새벽 3시 59분(한국 시간) → 하루의 기준으로는 아직 10/17
  private static final Instant BEFORE_FOUR = Instant.parse("2026-10-17T18:59:00Z");
  // 2026-10-18 새벽 4시(한국 시간) → 10/18 (새 날)
  private static final Instant FOUR_AM = Instant.parse("2026-10-17T19:00:00Z");

  private GptUsageGuard guardAt(Instant now) {
    return new GptUsageGuard(
        gptUsageRepository, Clock.fixed(now, ZoneOffset.UTC), 3, 15, 300, 2, 400, 5);
  }

  // 통화를 시작하고 seconds 초 뒤에 끝낸 것으로 기록합니다. (callRecordId 는 통화마다 다르게)
  private void finishedCall(GptUsageGuard guard, Long userId, long callRecordId, int seconds) {
    guard.recordCallStart(userId, callRecordId);
    guard.recordCallFinish(userId, callRecordId, NOON, NOON.plusSeconds(seconds));
  }

  // ---- 통화 ----

  @Test
  @DisplayName("오늘 아직 통화하지 않았으면 한 번 최대 길이(300초)까지 할 수 있다")
  void firstCall() {
    assertThat(guardAt(NOON).allowedCallSeconds(ME)).isEqualTo(300);
  }

  @Test
  @DisplayName("하루 3번을 다 걸면 0 이다 (짧게 통화했어도)")
  void callCountLimit() {
    GptUsageGuard guard = guardAt(NOON);
    finishedCall(guard, ME, 1, 10);
    finishedCall(guard, ME, 2, 10);
    assertThat(guard.allowedCallSeconds(ME)).isEqualTo(300);

    finishedCall(guard, ME, 3, 10);

    assertThat(guard.allowedCallSeconds(ME)).isZero();
  }

  @Test
  @DisplayName("오늘 14분을 썼으면 남은 1분(60초)만 할 수 있고, 15분을 다 썼으면 0 이다")
  void callMinutesLimit() {
    GptUsageGuard guard = guardAt(NOON);
    finishedCall(guard, ME, 1, 300);
    finishedCall(guard, ME, 2, 240);
    // 300 + 240 = 540초(9분) → 남은 6분이지만 한 번 최대 5분
    assertThat(guard.allowedCallSeconds(ME)).isEqualTo(300);

    GptUsageGuard bigLimit =
        new GptUsageGuard(
            gptUsageRepository, Clock.fixed(NOON, ZoneOffset.UTC), 10, 15, 300, 2, 400, 5);
    finishedCall(bigLimit, ME, 3, 300);
    // 840초(14분) → 남은 60초
    assertThat(bigLimit.allowedCallSeconds(ME)).isEqualTo(60);

    finishedCall(bigLimit, ME, 4, 60);
    // 900초(15분) → 0
    assertThat(bigLimit.allowedCallSeconds(ME)).isZero();
  }

  @Test
  @DisplayName("끝나지 않은 통화(마무리를 안 부름)는 최대 길이 300초를 쓴 것으로 센다")
  void unfinishedCallCountsAsMax() {
    GptUsageGuard bigLimit =
        new GptUsageGuard(
            gptUsageRepository, Clock.fixed(NOON, ZoneOffset.UTC), 10, 15, 300, 2, 400, 5);
    bigLimit.recordCallStart(ME, 1L);
    bigLimit.recordCallStart(ME, 2L);

    // 600초를 쓴 것으로 봐서 남은 300초
    assertThat(bigLimit.allowedCallSeconds(ME)).isEqualTo(300);
    bigLimit.recordCallStart(ME, 3L);
    assertThat(bigLimit.allowedCallSeconds(ME)).isZero();
  }

  @Test
  @DisplayName("날짜는 새벽 4시에 바뀐다: 3시 59분까지는 어제 통화가 세지고, 4시부터는 다시 처음이다")
  void dayChangesAtFourAm() {
    GptUsageGuard today = guardAt(NOON);
    finishedCall(today, ME, 1, 10);
    finishedCall(today, ME, 2, 10);
    finishedCall(today, ME, 3, 10);

    assertThat(guardAt(BEFORE_FOUR).allowedCallSeconds(ME)).isZero();
    assertThat(guardAt(FOUR_AM).allowedCallSeconds(ME)).isEqualTo(300);
  }

  @Test
  @DisplayName("한도는 사람마다 따로 센다")
  void perUser() {
    GptUsageGuard guard = guardAt(NOON);
    finishedCall(guard, ME, 1, 10);
    finishedCall(guard, ME, 2, 10);
    finishedCall(guard, ME, 3, 10);

    assertThat(guard.allowedCallSeconds(ME)).isZero();
    assertThat(guard.allowedCallSeconds(OTHER)).isEqualTo(300);
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
    // 20분이라 오늘 통화 시간(15분)을 다 썼습니다.
    assertThat(guard.allowedCallSeconds(ME)).isZero();
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

  // ---- 글 호출 (통화 요약 · 코치) ----

  @Test
  @DisplayName("글 호출은 통화 요약과 코치를 합쳐 하루 2번까지이고, 부를 때마다 5원으로 기록한다")
  void textLimitIsShared() {
    GptUsageGuard guard = guardAt(NOON);

    assertThat(guard.tryUseText(ME, GptUsageKind.COACH)).isTrue();
    assertThat(guard.tryUseText(ME, GptUsageKind.CALL_REVIEW)).isTrue();
    assertThat(guard.tryUseText(ME, GptUsageKind.COACH)).isFalse();
    assertThat(guard.tryUseText(ME, GptUsageKind.CALL_REVIEW)).isFalse();

    assertThat(gptUsageRepository.findByUserIdOrderById(ME))
        .extracting(GptUsage::getKind, GptUsage::getCostWon, GptUsage::getCallRecordId)
        .containsExactly(
            tuple(GptUsageKind.COACH, 5, null), tuple(GptUsageKind.CALL_REVIEW, 5, null));
  }

  @Test
  @DisplayName("통화는 글 호출 한도에 세지 않는다. 글 호출 한도는 다음 날(새벽 4시) 다시 생긴다. 다른 사람은 따로다")
  void textLimitIsSeparateFromCallsAndResets() {
    GptUsageGuard guard = guardAt(NOON);
    guard.recordCallStart(ME, 1L);
    assertThat(guard.tryUseText(ME, GptUsageKind.COACH)).isTrue();
    assertThat(guard.tryUseText(ME, GptUsageKind.COACH)).isTrue();
    assertThat(guard.tryUseText(ME, GptUsageKind.COACH)).isFalse();

    assertThat(guard.tryUseText(OTHER, GptUsageKind.COACH)).isTrue();
    assertThat(guardAt(BEFORE_FOUR).tryUseText(ME, GptUsageKind.COACH)).isFalse();
    assertThat(guardAt(FOUR_AM).tryUseText(ME, GptUsageKind.COACH)).isTrue();
  }

  @Test
  @DisplayName("통화를 글 호출로 기록하려 하면 막는다 (통화는 시간을 따로 세야 해서)")
  void callIsNotATextCall() {
    assertThatThrownBy(() -> guardAt(NOON).tryUseText(ME, GptUsageKind.CALL))
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
