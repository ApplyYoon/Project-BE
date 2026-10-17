package com.ithopeschool.projectbe.domain.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 비밀번호를 "되돌릴 수 없는 값(해시)" 으로 바꾸고, 로그인할 때 맞는지 확인하는 곳입니다.
//
// 왜 그대로 저장하지 않나요?
//   DB 가 새어 나가면 모든 사람의 비밀번호가 그대로 보이기 때문입니다. 해시는 원래 비밀번호로 되돌릴 수 없습니다.
// 왜 일부러 느리게 계산하나요?
//   한 번 계산하는 데 시간이 걸리면, 훔친 해시에 비밀번호를 수없이 넣어 보는 공격이 그만큼 느려집니다.
//   그래서 같은 계산을 21만 번 되풀이합니다. (PBKDF2 라는 방법이고, 자바에 기본으로 들어 있어 라이브러리가 필요 없습니다)
//   대신 가입·로그인도 그만큼 걸립니다. 서버가 느리면 횟수를 설정으로 낮출 수 있습니다. (docs/DEPLOY.md "로그인이 느릴 때")
// 소금(salt)은 무엇인가요?
//   계정마다 다르게 섞어 넣는 무작위 값입니다. 두 사람의 비밀번호가 같아도 저장되는 값은 달라집니다.
//
// 저장하는 모양:  pbkdf2-sha512$210000$소금$해시   (소금·해시는 Base64 글자)
//   되풀이 횟수를 같이 적어 두어서, 나중에 횟수를 바꿔도 예전에 가입한 사람이 그대로 로그인할 수 있습니다.
//
// ⚠️ 비밀번호는 어떤 경우에도 로그에 찍지 않습니다.
@Component
public class PasswordHasher {

  private static final String ALGORITHM = "PBKDF2WithHmacSHA512";
  private static final String PREFIX = "pbkdf2-sha512";

  // 되풀이 횟수의 기본값. OWASP(웹 보안 권장 기준)가 이 방법에 권하는 값입니다. (2023년 기준 210,000)
  public static final int DEFAULT_ITERATIONS = 210_000;
  // 이보다 작게는 설정할 수 없습니다. 실수로 1 같은 값을 넣어 비밀번호가 쉽게 풀리는 일을 막습니다.
  public static final int MIN_ITERATIONS = 10_000;
  // 소금의 길이(바이트)
  private static final int SALT_BYTES = 16;
  // 만들어지는 해시의 길이(비트)
  private static final int HASH_BITS = 512;

  // SecureRandom: 예측할 수 없는 무작위 값을 만듭니다. (그냥 Random 은 예측이 가능해서 보안에 쓰면 안 됩니다)
  private final SecureRandom random = new SecureRandom();

  // 새로 저장할 때 쓰는 되풀이 횟수. 설정 app.auth.password-hash-iterations 에서 받습니다. (application.yml)
  private final int iterations;

  public PasswordHasher(
      @Value("${app.auth.password-hash-iterations:" + DEFAULT_ITERATIONS + "}") int iterations) {
    if (iterations < MIN_ITERATIONS) {
      // 서버가 켜지다가 여기서 멈춥니다. 약한 설정으로 조용히 돌아가는 것보다 바로 알게 되는 쪽이 안전합니다.
      throw new IllegalArgumentException(
          "app.auth.password-hash-iterations 는 " + MIN_ITERATIONS + " 이상이어야 합니다");
    }
    this.iterations = iterations;
  }

  // 비밀번호를 저장할 모양으로 바꿉니다. 같은 비밀번호여도 부를 때마다 결과가 다릅니다(소금이 달라서).
  public String hash(String rawPassword) {
    byte[] salt = new byte[SALT_BYTES];
    random.nextBytes(salt);
    byte[] hash = pbkdf2(rawPassword, salt, iterations);

    Base64.Encoder encoder = Base64.getEncoder();
    return PREFIX
        + "$"
        + iterations
        + "$"
        + encoder.encodeToString(salt)
        + "$"
        + encoder.encodeToString(hash);
  }

  // 입력한 비밀번호가 저장된 값과 맞는지 확인합니다. 저장된 값의 모양이 이상하면 false 입니다.
  public boolean matches(String rawPassword, String storedHash) {
    if (rawPassword == null || storedHash == null) {
      return false;
    }
    // 저장된 값을 "$" 로 나눠서 횟수·소금·해시를 꺼냅니다.
    String[] parts = storedHash.split("\\$");
    if (parts.length != 4 || !PREFIX.equals(parts[0])) {
      return false;
    }

    try {
      // 지금 설정이 아니라 "저장할 때 쓴 횟수" 로 계산합니다. 그래서 설정을 바꿔도 예전 계정이 로그인됩니다.
      int storedIterations = Integer.parseInt(parts[1]);
      byte[] salt = Base64.getDecoder().decode(parts[2]);
      byte[] expected = Base64.getDecoder().decode(parts[3]);
      // 저장할 때와 같은 소금·횟수로 다시 계산해서 결과가 같은지 봅니다.
      byte[] actual = pbkdf2(rawPassword, salt, storedIterations);
      // 그냥 비교하면 "몇 글자까지 맞았는지" 가 응답 시간에 드러납니다. isEqual 은 어디가 틀려도 같은 시간이 걸립니다.
      return MessageDigest.isEqual(actual, expected);
    } catch (IllegalArgumentException e) {
      // 숫자나 Base64 가 아닌 글자가 들어 있는 경우입니다. (NumberFormatException 도 여기에 포함됩니다)
      return false;
    }
  }

  private static byte[] pbkdf2(String rawPassword, byte[] salt, int iterations) {
    PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, HASH_BITS);
    try {
      return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      // 이 방법은 자바에 늘 들어 있어서 여기에 올 일이 없습니다.
      throw new IllegalStateException("비밀번호 해시를 계산할 수 없습니다", e);
    } finally {
      // 계산이 끝나면 메모리에 남은 비밀번호를 지웁니다.
      spec.clearPassword();
    }
  }
}
