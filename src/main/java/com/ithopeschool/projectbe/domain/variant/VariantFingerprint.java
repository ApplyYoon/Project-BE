package com.ithopeschool.projectbe.domain.variant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

// 문제 글의 "지문(fingerprint)" 을 만듭니다. 띄어쓰기나 LaTeX 서식만 다른 두 문제를 같은 문제로 보려고 씁니다.
// (docs/VARIANT-SPEC.md 4장 끝, G6 중복 검사)
//
//   1) LaTeX 서식 명령(\left, \right, \, \; \! \quad 등)을 지웁니다.
//   2) 공백을 모두 지웁니다.
//   3) 소문자로 바꿉니다.
//   4) SHA-256 으로 64글자 16진수를 만듭니다.
//
//   "$\left( x+1 \right)^2$"  와  "$(x+1)^2$"  →  같은 지문
public final class VariantFingerprint {

  // 지울 LaTeX 서식 명령. 글자의 뜻은 바꾸지 않고 모양만 바꾸는 명령들입니다.
  private static final String FORMAT_COMMANDS =
      "\\\\left|\\\\right|\\\\displaystyle|\\\\quad|\\\\qquad|\\\\[,;:!]";

  private VariantFingerprint() {}

  public static String of(String text) {
    String normalized =
        (text == null ? "" : text)
            .replaceAll(FORMAT_COMMANDS, "")
            .replaceAll("\\s+", "")
            .toLowerCase(Locale.ROOT);
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(sha256.digest(normalized.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      // 자바에는 SHA-256 이 항상 들어 있어서 여기 올 일은 없습니다.
      throw new IllegalStateException("SHA-256 을 쓸 수 없어요", e);
    }
  }
}
