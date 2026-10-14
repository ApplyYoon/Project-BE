package com.ithopeschool.projectbe.domain.call;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.care.ToneGuide;

// GPT 가 정리한 통화(Raw)를 검사해서, 저장하고 보여 줘도 되는 값만 남긴 결과입니다. (이슈 #29)
// "GPT 가 정해진 값이 아닌 답을 주면 그 항목은 비워 둔다" 는 완료 조건을 여기서 지킵니다.
//
// 칸마다 검사하는 것
//   crisis           true 일 때만 true. 빠졌거나 이상하면 false (위기 표현은 단어 목록으로도 따로 검사합니다)
//   summary          앞뒤 빈칸을 지우고, 비었거나 300자를 넘거나 깎아내리는 말이 있으면 비움
//   foundBias        생각 습관 목록(CognitiveBias)의 이름이 아니면 비움
//   foundEvidence    습관이 비었으면 같이 비움. 100자를 넘거나 깎아내리는 말이 있으면 비움
//   actualQuestion   200자를 넘거나 깎아내리는 말이 있으면 비움
//   missReason       FE 의 이유 글자('tooMuch' 등)가 아니면 비움
//   promiseIf·Then   각 50자까지. 둘 중 하나라도 비면 둘 다 비움 ("만약 ~하면 → ~한다" 가 다 있어야 약속)
//   promiseKept      true · false · 비움 그대로
//   mood             1 ~ 5 가 아니면 비움
//   expectedMinutes  0 ~ 1440 이 아니면 비움
//
// 예시
//   foundBias "PLANNING_FALLACY"  →  계획 오류        foundBias "LAZY"  →  비움 (목록에 없음)
//   promiseIf "학원에서 오면", promiseThen ""  →  약속 없음
//   summary "계획을 또 못 지켜서 한심하다고 함."  →  비움 (깎아내리는 말)
public record CallReview(
    boolean crisis,
    String summary,
    CognitiveBias foundBias,
    String foundEvidence,
    String actualQuestion,
    MissReason missReason,
    String promiseIf,
    String promiseThen,
    Boolean promiseKept,
    Integer mood,
    Integer expectedMinutes) {

  static final int MAX_SUMMARY = 300;
  static final int MAX_EVIDENCE = 100;
  static final int MAX_QUESTION = 200;
  static final int MAX_PROMISE = 50;
  static final int MAX_MINUTES = 1440;

  // GPT 가 돌려준 그대로의 값입니다. (검사 전) 칸 이름은 CallReviewPrompt.schema 와 같습니다.
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Raw(
      Boolean crisis,
      String summary,
      String foundBias,
      String foundEvidence,
      String actualQuestion,
      String missReason,
      String promiseIf,
      String promiseThen,
      Boolean promiseKept,
      Integer mood,
      Integer expectedMinutes) {}

  // 아무것도 정리하지 못한 결과입니다. (학생이 한마디도 하지 않은 통화 등)
  public static CallReview empty() {
    return new CallReview(false, null, null, null, null, null, null, null, null, null, null);
  }

  // GPT 의 답을 칸마다 검사합니다.
  public static CallReview from(Raw raw) {
    CognitiveBias foundBias = biasOf(raw.foundBias());
    String promiseIf = plainText(raw.promiseIf(), MAX_PROMISE);
    String promiseThen = plainText(raw.promiseThen(), MAX_PROMISE);
    boolean hasPromise = promiseIf != null && promiseThen != null;

    return new CallReview(
        Boolean.TRUE.equals(raw.crisis()),
        safeText(raw.summary(), MAX_SUMMARY),
        foundBias,
        foundBias == null ? null : safeText(raw.foundEvidence(), MAX_EVIDENCE),
        safeText(raw.actualQuestion(), MAX_QUESTION),
        missReasonOf(raw.missReason()),
        hasPromise ? promiseIf : null,
        hasPromise ? promiseThen : null,
        raw.promiseKept(),
        inRange(raw.mood(), 1, 5),
        inRange(raw.expectedMinutes(), 0, MAX_MINUTES));
  }

  // 학생에게 보여 줄 글. 앞뒤 빈칸을 지우고, 비었거나 너무 길거나 깎아내리는 말이 있으면 null 입니다.
  static String safeText(String text, int maxLength) {
    String trimmed = plainText(text, maxLength);
    if (trimmed == null || ToneGuide.hasBannedExpression(trimmed)) {
      return null;
    }
    return trimmed;
  }

  // 학생이 한 말(약속). 앞뒤 빈칸을 지우고, 비었거나 너무 길면 null 입니다.
  static String plainText(String text, int maxLength) {
    if (text == null) {
      return null;
    }
    String trimmed = text.strip();
    if (trimmed.isEmpty() || trimmed.length() > maxLength) {
      return null;
    }
    return trimmed;
  }

  // 생각 습관 이름("PLANNING_FALLACY")을 목록에서 찾습니다. 없으면 null 입니다.
  static CognitiveBias biasOf(String name) {
    for (CognitiveBias bias : CognitiveBias.values()) {
      if (bias.name().equals(name)) {
        return bias;
      }
    }
    return null;
  }

  // FE 의 이유 글자('tooMuch')를 MissReason 으로 바꿉니다. 정해진 글자가 아니면 null 입니다.
  // 글자 목록은 학생 카드와 같은 것(CallCard.missReasonCode)을 씁니다.
  static MissReason missReasonOf(String value) {
    for (MissReason reason : MissReason.values()) {
      if (CallCard.missReasonCode(reason).equals(value)) {
        return reason;
      }
    }
    return null;
  }

  // min ~ max 안이면 그대로, 아니면 null 입니다.
  static Integer inRange(Integer value, int min, int max) {
    if (value == null || value < min || value > max) {
      return null;
    }
    return value;
  }
}
