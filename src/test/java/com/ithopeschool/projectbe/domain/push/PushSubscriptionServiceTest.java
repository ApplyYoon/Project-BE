package com.ithopeschool.projectbe.domain.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.push.dto.PushSubscribeRequest;
import com.ithopeschool.projectbe.domain.push.dto.PushUnsubscribeRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// Service 단위 테스트: DB 와 보내기 도구를 가짜로 바꿔서 구독·해제·보내기 로직만 확인합니다.
class PushSubscriptionServiceTest {

  private static final Long USER_ID = 7L;
  private static final Instant NOW = Instant.parse("2026-10-15T09:00:00Z");
  private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc";

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);
  private final WebPushSender sender = mock(WebPushSender.class);
  private final PushSubscriptionService service =
      new PushSubscriptionService(
          currentUserProvider, repository, sender, Clock.fixed(NOW, ZoneOffset.UTC));

  private static final PushSubscribeRequest REQUEST =
      new PushSubscribeRequest(ENDPOINT, new PushSubscribeRequest.Keys("BNcRd", "tBHI"));

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
  }

  private static void assertError(Runnable action, ErrorCode code, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(code);
  }

  @Test
  @DisplayName("공개 키: 푸시가 켜진 서버면 돌려주고, 꺼진 서버(내 컴퓨터)면 503")
  void publicKey() {
    given(sender.isEnabled()).willReturn(true);
    given(sender.publicKey()).willReturn("BEl62");
    assertThat(service.getPublicKey().publicKey()).isEqualTo("BEl62");

    given(sender.isEnabled()).willReturn(false);
    assertError(service::getPublicKey, ErrorCode.SERVICE_UNAVAILABLE, "앱을 닫아도 오는 알림이 아직 켜져 있지 않아요");
  }

  @Test
  @DisplayName("처음 구독하면 내 번호로 한 줄을 만든다")
  void subscribeNew() {
    given(repository.findByEndpoint(ENDPOINT)).willReturn(Optional.empty());

    service.subscribe(REQUEST);

    ArgumentCaptor<PushSubscription> saved = ArgumentCaptor.forClass(PushSubscription.class);
    verify(repository).save(saved.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
    assertThat(saved.getValue().getEndpoint()).isEqualTo(ENDPOINT);
    assertThat(saved.getValue().getP256dh()).isEqualTo("BNcRd");
    assertThat(saved.getValue().getSubscribedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("같은 기기가 다시 구독하면 새 줄 없이 고치고, 다른 사람이 로그인했으면 그 사람의 구독이 된다")
  void subscribeAgain() {
    PushSubscription old =
        new PushSubscription(99L, ENDPOINT, "old", "old", NOW.minusSeconds(3600));
    given(repository.findByEndpoint(ENDPOINT)).willReturn(Optional.of(old));

    service.subscribe(REQUEST);

    verify(repository, never()).save(any());
    assertThat(old.getUserId()).isEqualTo(USER_ID);
    assertThat(old.getP256dh()).isEqualTo("BNcRd");
    assertThat(old.getSubscribedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("구독 해제는 내 구독만 지우고, 없으면 조용히 끝난다")
  void unsubscribe() {
    PushSubscription mine = new PushSubscription(USER_ID, ENDPOINT, "k", "a", NOW);
    given(repository.findByEndpointAndUserId(ENDPOINT, USER_ID)).willReturn(Optional.of(mine));
    service.unsubscribe(new PushUnsubscribeRequest(ENDPOINT));
    verify(repository).delete(mine);

    given(repository.findByEndpointAndUserId("https://other", USER_ID))
        .willReturn(Optional.empty());
    service.unsubscribe(new PushUnsubscribeRequest("https://other"));
  }

  @Test
  @DisplayName("한 사람에게 보내기: 기기마다 보내고, 끝난 구독(GONE)은 지우고, 보낸 수를 돌려준다")
  void sendToUser() {
    PushSubscription phone = new PushSubscription(USER_ID, "https://a", "k", "a", NOW);
    PushSubscription oldPc = new PushSubscription(USER_ID, "https://b", "k", "a", NOW);
    PushSubscription flaky = new PushSubscription(USER_ID, "https://c", "k", "a", NOW);
    given(repository.findByUserId(USER_ID)).willReturn(List.of(phone, oldPc, flaky));
    WebPushSender.Payload payload = new WebPushSender.Payload("제목", "글", "/");
    given(sender.send(phone, payload)).willReturn(WebPushSender.Result.SENT);
    given(sender.send(oldPc, payload)).willReturn(WebPushSender.Result.GONE);
    given(sender.send(flaky, payload)).willReturn(WebPushSender.Result.FAILED);

    assertThat(service.sendToUser(USER_ID, payload)).isEqualTo(1);
    verify(repository).delete(oldPc);
    verify(repository, never()).delete(flaky);
  }

  @Test
  @DisplayName("로그인하지 않았으면 셋 다 401")
  void requiresLogin() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    assertError(service::getPublicKey, ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
    assertError(() -> service.subscribe(REQUEST), ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
    assertError(
        () -> service.unsubscribe(new PushUnsubscribeRequest(ENDPOINT)),
        ErrorCode.UNAUTHORIZED,
        "로그인이 필요해요");
  }
}
