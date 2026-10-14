package com.ithopeschool.projectbe.domain.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 웹 푸시 구독 테이블. 한 줄 = 알림을 받겠다고 한 기기(브라우저) 하나. (이슈 #32)
// 한 사람이 휴대폰과 PC 에서 각각 켜면 두 줄입니다.
//
// 브라우저가 알림을 켜면 "이 주소(endpoint)로, 이 열쇠(p256dh·auth)로 잠가서 보내 줘" 라는 구독 정보를 줍니다.
// 서버는 이 정보로 푸시를 보냅니다. 열쇠가 없으면 알림 내용을 잠글 수 없어서 보낼 수 없습니다.
//
// ⚠️ endpoint 를 아는 사람은 그 기기로 (잠긴) 알림을 보낼 수 있어서, 로그에 찍지 않고 응답으로도 돌려주지 않습니다.
@Entity
@Table(
    name = "push_subscriptions",
    indexes = @Index(name = "idx_push_subscriptions_user_id", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription {

  // 구독을 구분하는 고유 번호
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 알림을 받을 사람(users.id)
  @Column(nullable = false)
  private Long userId;

  // 푸시를 보낼 주소. 브라우저 회사(구글·애플·모질라)의 주소입니다. 기기마다 달라서 겹칠 수 없습니다.
  @Column(nullable = false, unique = true, length = 500)
  private String endpoint;

  // 알림 내용을 잠그는 데 쓰는 브라우저의 공개 열쇠
  @Column(nullable = false, length = 200)
  private String p256dh;

  // 알림 내용을 잠그는 데 쓰는 비밀 값 (브라우저와 서버만 압니다)
  @Column(nullable = false, length = 100)
  private String auth;

  // 구독을 저장한(또는 다시 저장한) 시각
  @Column(nullable = false)
  private Instant subscribedAt;

  public PushSubscription(
      Long userId, String endpoint, String p256dh, String auth, Instant subscribedAt) {
    this.userId = userId;
    this.endpoint = endpoint;
    this.p256dh = p256dh;
    this.auth = auth;
    this.subscribedAt = subscribedAt;
  }

  // 같은 기기가 다시 구독했을 때 바꿉니다. 같은 기기에서 다른 사람이 로그인했으면 그 사람의 구독이 됩니다.
  public void renew(Long userId, String p256dh, String auth, Instant subscribedAt) {
    this.userId = userId;
    this.p256dh = p256dh;
    this.auth = auth;
    this.subscribedAt = subscribedAt;
  }
}
