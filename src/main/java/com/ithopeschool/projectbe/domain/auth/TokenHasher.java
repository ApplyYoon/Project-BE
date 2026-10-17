package com.ithopeschool.projectbe.domain.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

// 로그인 토큰을 새로 만들고, DB 에 넣을 모양(해시)으로 바꾸는 곳입니다.
//
//   newToken()  ->  "Zx3k…"  (무작위 43글자. FE 에게 주는 값)
//   hash(토큰)  ->  "9f2c…"  (64글자. DB 에 저장하는 값)
//
// 토큰은 길고 무작위라서, 비밀번호처럼 느린 계산(PasswordHasher)이 필요 없습니다. 빠른 SHA-256 한 번이면 충분합니다.
@Component
public class TokenHasher {

  // 토큰의 길이(바이트). 32바이트 = 256비트라서 맞혀 볼 수 없습니다.
  private static final int TOKEN_BYTES = 32;

  private final SecureRandom random = new SecureRandom();

  // 새 토큰을 만듭니다. 주소나 헤더에 넣어도 깨지지 않는 글자(영문·숫자·-·_)만 씁니다.
  public String newToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  // 토큰을 DB 에 넣을 해시로 바꿉니다. 같은 토큰은 항상 같은 해시가 됩니다.
  public String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      // HexFormat: 바이트를 16진수 글자로 적어 줍니다. (32바이트 → 64글자)
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 은 자바에 늘 들어 있어서 여기에 올 일이 없습니다.
      throw new IllegalStateException("SHA-256 을 쓸 수 없습니다", e);
    }
  }
}
