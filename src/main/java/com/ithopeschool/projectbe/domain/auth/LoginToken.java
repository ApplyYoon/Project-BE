package com.ithopeschool.projectbe.domain.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 로그인 토큰 테이블. 한 줄 = 로그인 한 번으로 내준 토큰 하나.
// 토큰은 "이 요청을 보낸 사람이 로그인한 그 사람" 임을 알려 주는 열쇠입니다. FE 가 요청마다 같이 보냅니다.
//
// ⚠️ 토큰 원문은 저장하지 않고 해시(알아볼 수 없게 바꾼 값)만 저장합니다. (TokenHasher)
//    DB 를 누가 들여다봐도 그 값으로는 로그인할 수 없게 하려는 것입니다. 비밀번호를 그대로 저장하지 않는 것과 같은 이유입니다.
@Entity
@Table(name = "login_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginToken {

  // 토큰을 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 토큰의 주인. 이 토큰을 들고 온 요청은 이 사용자의 요청으로 봅니다.
  @Column(nullable = false)
  private Long userId;

  // 토큰의 해시(SHA-256 을 16진수 64글자로 적은 것). 요청에 온 토큰을 같은 방법으로 바꿔서 이 값과 비교합니다.
  @Column(nullable = false, unique = true, length = 64)
  private String tokenHash;

  // 이 시각이 지나면 쓸 수 없습니다. 다시 로그인해야 합니다.
  @Column(nullable = false, updatable = false)
  private Instant expiresAt;

  // 토큰을 내준 시각 (= 로그인한 시각)
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  public LoginToken(Long userId, String tokenHash, Instant expiresAt, Instant createdAt) {
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.createdAt = createdAt;
  }
}
