package com.ithopeschool.projectbe.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 스프링을 띄우지 않는 단위 테스트입니다. new 로 직접 만들어서 결과만 확인합니다.
class PasswordHasherTest {

  // 실제 서버의 기본 설정(210000번)과 같은 횟수로 확인합니다.
  private final PasswordHasher passwordHasher =
      new PasswordHasher(PasswordHasher.DEFAULT_ITERATIONS);

  @Test
  @DisplayName("맞는 비밀번호는 통과하고, 한 글자라도 다르면 통과하지 못한다")
  void matchesOnlyTheSamePassword() {
    String stored = passwordHasher.hash("study2026");

    assertThat(passwordHasher.matches("study2026", stored)).isTrue();
    assertThat(passwordHasher.matches("study2025", stored)).isFalse();
    assertThat(passwordHasher.matches("Study2026", stored)).isFalse();
    assertThat(passwordHasher.matches("", stored)).isFalse();
  }

  @Test
  @DisplayName("저장하는 값에는 비밀번호가 그대로 들어 있지 않다")
  void storedValueDoesNotContainThePassword() {
    String stored = passwordHasher.hash("study2026");

    assertThat(stored).doesNotContain("study2026");
    // 모양: pbkdf2-sha512$되풀이 횟수$소금$해시
    assertThat(stored).startsWith("pbkdf2-sha512$210000$");
    assertThat(stored.split("\\$")).hasSize(4);
    // accounts.password_hash 컬럼(200자)에 들어가야 합니다.
    assertThat(stored.length()).isLessThanOrEqualTo(200);
  }

  @Test
  @DisplayName("같은 비밀번호여도 저장되는 값은 매번 다르다 (소금이 달라서)")
  void samePasswordGivesDifferentStoredValues() {
    String first = passwordHasher.hash("study2026");
    String second = passwordHasher.hash("study2026");

    assertThat(first).isNotEqualTo(second);
    // 값은 달라도 둘 다 같은 비밀번호로 통과합니다.
    assertThat(passwordHasher.matches("study2026", first)).isTrue();
    assertThat(passwordHasher.matches("study2026", second)).isTrue();
  }

  @Test
  @DisplayName("한글·공백·특수문자가 든 비밀번호도 구분한다")
  void unicodePassword() {
    String stored = passwordHasher.hash("공부 왕 2026!");

    assertThat(passwordHasher.matches("공부 왕 2026!", stored)).isTrue();
    assertThat(passwordHasher.matches("공부왕 2026!", stored)).isFalse();
  }

  @Test
  @DisplayName("저장된 값의 모양이 이상하면 에러를 내지 않고 '틀림' 으로 본다")
  void brokenStoredValueNeverMatches() {
    assertThat(passwordHasher.matches("study2026", null)).isFalse();
    assertThat(passwordHasher.matches(null, passwordHasher.hash("study2026"))).isFalse();
    assertThat(passwordHasher.matches("study2026", "")).isFalse();
    assertThat(passwordHasher.matches("study2026", "study2026")).isFalse();
    assertThat(passwordHasher.matches("study2026", "bcrypt$10$abc$def")).isFalse();
    assertThat(passwordHasher.matches("study2026", "pbkdf2-sha512$많이$abc$def")).isFalse();
    assertThat(passwordHasher.matches("study2026", "pbkdf2-sha512$10000$!!!$???")).isFalse();
  }

  @Test
  @DisplayName("되풀이 횟수가 다르게 저장된 옛 값도, 거기 적힌 횟수대로 계산해서 확인한다")
  void usesTheIterationCountWrittenInTheStoredValue() {
    // 횟수 10000 으로 "study2026" 을 저장한 값입니다. (소금: 0 이 16개)
    // 지금 설정(210000)과 횟수가 달라도 로그인할 수 있어야 합니다. 그래야 나중에 횟수를 바꿀 수 있습니다.
    String oldStored =
        "pbkdf2-sha512$10000$AAAAAAAAAAAAAAAAAAAAAA==$" + HASH_OF_STUDY2026_WITH_10000_ROUNDS;

    assertThat(passwordHasher.matches("study2026", oldStored)).isTrue();
    assertThat(passwordHasher.matches("study2025", oldStored)).isFalse();
  }

  @Test
  @DisplayName("설정한 횟수로 저장하고, 횟수 설정이 다른 서버에서도 그 값을 확인할 수 있다")
  void iterationsComeFromTheSetting() {
    PasswordHasher lighter = new PasswordHasher(20_000);

    String stored = lighter.hash("study2026");

    assertThat(stored).startsWith("pbkdf2-sha512$20000$");
    assertThat(passwordHasher.matches("study2026", stored)).isTrue();
  }

  @Test
  @DisplayName("횟수를 너무 작게 설정하면 서버가 켜지지 않는다 (약한 설정으로 조용히 도는 것을 막는다)")
  void tooFewIterationsAreRejected() {
    assertThatThrownBy(() -> new PasswordHasher(PasswordHasher.MIN_ITERATIONS - 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("password-hash-iterations");
    assertThatThrownBy(() -> new PasswordHasher(0)).isInstanceOf(IllegalArgumentException.class);

    // 딱 최소값은 됩니다.
    assertThat(new PasswordHasher(PasswordHasher.MIN_ITERATIONS).hash("study2026")).isNotBlank();
  }

  // 위 테스트에서 쓰는 값입니다. PBKDF2-HMAC-SHA512, 10000번, 소금 0×16, 512비트로 따로 계산했습니다.
  // (자바와 파이썬 hashlib.pbkdf2_hmac 두 가지로 계산해서 같은 값이 나오는 것을 확인했습니다)
  private static final String HASH_OF_STUDY2026_WITH_10000_ROUNDS =
      "/rnPQRr1jDBeCG5RLdb8kwEupCKLNtym3BjrNZGxMSF4/d2t+GEEM27WBIu/YO5xxtGeISlnsIrH1awL+Qh/lg==";
}
