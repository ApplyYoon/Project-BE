package com.ithopeschool.projectbe.domain.push;

import com.ithopeschool.projectbe.domain.push.dto.PushPublicKeyResponse;
import com.ithopeschool.projectbe.domain.push.dto.PushSubscribeRequest;
import com.ithopeschool.projectbe.domain.push.dto.PushUnsubscribeRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 웹 푸시 구독을 저장·해제하고, 한 사람에게 알림을 보내는 로직입니다. (이슈 #32, docs/API.md "웹 푸시")
//
// 사용자가 부르는 메서드는 currentUserProvider.getCurrentUserId() 로 시작합니다. (로그인 안 했으면 401)
// ⚠️ 구독 주소(endpoint)와 알림 내용은 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

  private final CurrentUserProvider currentUserProvider;
  private final PushSubscriptionRepository pushSubscriptionRepository;
  private final WebPushSender webPushSender;
  private final Clock clock;

  // 구독할 때 쓰는 서버의 공개 키. 푸시가 꺼진 서버(내 컴퓨터)면 503 입니다.
  public PushPublicKeyResponse getPublicKey() {
    currentUserProvider.getCurrentUserId();
    if (!webPushSender.isEnabled()) {
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "앱을 닫아도 오는 알림이 아직 켜져 있지 않아요");
    }
    return new PushPublicKeyResponse(webPushSender.publicKey());
  }

  // 구독 저장. 같은 기기(endpoint)가 다시 구독하면 새 줄을 만들지 않고 고칩니다.
  // 같은 기기에서 다른 사람이 로그인해 구독하면, 그 기기의 알림은 새 사람에게 갑니다. (한 기기는 한 사람 것)
  @Transactional
  public void subscribe(PushSubscribeRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    Optional<PushSubscription> existing =
        pushSubscriptionRepository.findByEndpoint(request.endpoint());
    if (existing.isPresent()) {
      existing.get().renew(userId, request.keys().p256dh(), request.keys().auth(), clock.instant());
      return;
    }
    pushSubscriptionRepository.save(
        new PushSubscription(
            userId,
            request.endpoint(),
            request.keys().p256dh(),
            request.keys().auth(),
            clock.instant()));
  }

  // 구독 해제. 내 구독이 아니거나 이미 없어도 에러 없이 끝납니다. (남의 구독이 있는지 알려 주지 않게)
  @Transactional
  public void unsubscribe(PushUnsubscribeRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    pushSubscriptionRepository
        .findByEndpointAndUserId(request.endpoint(), userId)
        .ifPresent(pushSubscriptionRepository::delete);
  }

  // 한 사람의 모든 기기에 알림을 보냅니다. 보낸 기기 수를 돌려줍니다. (예약 작업이 부릅니다. 로그인과 상관없음)
  // 끝난 구독(GONE: 알림을 껐거나 앱을 지움)은 지웁니다.
  @Transactional
  public int sendToUser(Long userId, WebPushSender.Payload payload) {
    int sent = 0;
    List<PushSubscription> subscriptions = pushSubscriptionRepository.findByUserId(userId);
    for (PushSubscription subscription : subscriptions) {
      WebPushSender.Result result = webPushSender.send(subscription, payload);
      if (result == WebPushSender.Result.SENT) {
        sent++;
      } else if (result == WebPushSender.Result.GONE) {
        pushSubscriptionRepository.delete(subscription);
      }
    }
    return sent;
  }
}
