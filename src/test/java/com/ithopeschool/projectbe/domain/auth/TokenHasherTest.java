package com.ithopeschool.projectbe.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 스프링을 띄우지 않는 단위 테스트입니다.
class TokenHasherTest {

  private final TokenHasher tokenHasher = new TokenHasher();

  @Test
  @DisplayName("토큰은 43글자이고, 헤더에 넣어도 깨지지 않는 글자만 쓴다")
  void tokenShape() {
    String token = tokenHasher.newToken();

    assertThat(token).hasSize(43);
    assertThat(token).matches("[A-Za-z0-9_-]+");
  }

  @Test
  @DisplayName("토큰은 만들 때마다 다르다")
  void tokensAreDifferentEveryTime() {
    Set<String> tokens = new HashSet<>();
    for (int i = 0; i < 100; i++) {
      tokens.add(tokenHasher.newToken());
    }

    assertThat(tokens).hasSize(100);
  }

  @Test
  @DisplayName("해시는 64글자(16진수)이고, 토큰 원문과 다르다")
  void hashShape() {
    String token = tokenHasher.newToken();
    String hash = tokenHasher.hash(token);

    assertThat(hash).hasSize(64);
    assertThat(hash).matches("[0-9a-f]+");
    assertThat(hash).isNotEqualTo(token);
  }

  @Test
  @DisplayName("같은 토큰은 항상 같은 해시가 되고, 다른 토큰은 다른 해시가 된다")
  void sameTokenSameHash() {
    // SHA-256("abc") 는 널리 알려진 값이라, 계산이 맞는지 바로 확인할 수 있습니다.
    assertThat(tokenHasher.hash("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    assertThat(tokenHasher.hash("abc")).isEqualTo(tokenHasher.hash("abc"));
    assertThat(tokenHasher.hash("abc")).isNotEqualTo(tokenHasher.hash("abd"));
  }
}
