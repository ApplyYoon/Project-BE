package com.ithopeschool.projectbe.domain.push;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

// push_subscriptions 테이블의 창구입니다.
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

  // 주소로 구독 하나를 찾습니다. (같은 기기가 다시 구독했는지 볼 때)
  Optional<PushSubscription> findByEndpoint(String endpoint);

  // 내 구독 중에서 주소로 하나를 찾습니다. 남의 구독이면 빈 Optional 입니다. (구독 해제할 때)
  Optional<PushSubscription> findByEndpointAndUserId(String endpoint, Long userId);

  // 한 사람의 구독 전부 (기기마다 하나씩)
  List<PushSubscription> findByUserId(Long userId);

  // 알림을 켠 사람들의 번호 (한 사람이 기기가 여러 개여도 한 번만)
  @Query("select distinct p.userId from PushSubscription p order by p.userId")
  List<Long> findSubscribedUserIds();
}
