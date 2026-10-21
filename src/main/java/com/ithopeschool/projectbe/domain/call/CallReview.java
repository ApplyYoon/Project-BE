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
//   summary          앞뒤 빈칸을 지우고, 비었거나 깎아내리는 말이 있으면 비움. 300자를 넘으면 줄임 (끝의 '이해도: …' 는 지킴)
//   foundBias        생각 습관 목록(CognitiveBias)의 이름이 아니면 비움
//   foundEvidence    습관이 비었으면 같이 비움. 깎아내리는 말이 있으면 비움. 100자를 넘으면 줄임
//   actualQuestion   깎아내리는 말이 있으면 비움. 200자를 넘으면 줄임
//   missReason       FE 의 이유 글자('tooMuch' 등)가 아니면 비움
//   promiseIf·Then   각 50자까지. 넘으면 줄이지 않고 비움 (학생이 정한 약속이라, 자르면 뜻이 바뀝니다)
//                    둘 중 하나라도 비면 둘 다 비움 ("만약 ~하면 → ~한다" 가 다 있어야 약속)
//
// 왜 줄이나요? 예전에는 넘치면 비웠는데, 확인 통화는 요약 끝에 '이해도' 를 붙여서 300자를 넘기 쉬웠습니다.
// 그러면 통화를 마무리해도 요약이 통째로 사라졌습니다. 글자 수 한도는 DB 칸 길이(call_records)와 같습니다.
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
  // 확인 통화의 요약 끝에 붙는 이해도 글의 시작 (CallReviewPrompt 규칙 4)
  static final String UNDERSTANDING_MARK = "이해도:";
  // 줄인 글의 끝에 붙이는 표시
  static final String CUT_MARK = "…";

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
        summaryOf(raw.summary()),
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

  // 학생에게 보여 줄 글. 앞뒤 빈칸을 지우고, 비었거나 깎아내리는 말이 있으면 null 입니다.
  // 너무 길면 maxLength 에 맞게 줄입니다.   ("가" 120자, 100) → "가" 99자 + "…"
  static String safeText(String text, int maxLength) {
    if (text == null || text.isBlank() || ToneGuide.hasBannedExpression(text)) {
      return null;
    }
    return cut(text.strip(), maxLength);
  }

  // 통화 요약. safeText 와 같지만, 줄일 때 끝에 붙은 '이해도: …' 는 남기고 그 앞의 내용을 줄입니다.
  // 이해도는 기록 · 분석에서 읽는 값이라 잘리면 안 됩니다.
  //   ("공부 이야기" 350자 + " 이해도: 조금 헷갈림") → "공부 이야기" 앞부분 + "… 이해도: 조금 헷갈림" (모두 300자)
  static String summaryOf(String text) {
    String summary = safeText(text, Integer.MAX_VALUE);
    if (summary == null || summary.length() <= MAX_SUMMARY) {
      return summary;
    }
    int mark = summary.lastIndexOf(UNDERSTANDING_MARK);
    if (mark <= 0) {
      return cut(summary, MAX_SUMMARY);
    }
    String understanding = " " + summary.substring(mark);
    String body = summary.substring(0, mark).strip();
    // 이해도 글이 너무 길어서(이상한 답) 내용이 들어갈 자리가 없으면, 그냥 앞에서부터 줄입니다.
    int bodyLength = MAX_SUMMARY - understanding.length();
    if (bodyLength <= CUT_MARK.length()) {
      return cut(summary, MAX_SUMMARY);
    }
    return cut(body, bodyLength) + understanding;
  }

  // maxLength 자를 넘으면 앞에서부터 (maxLength - 1)자만 남기고 "…" 을 붙입니다. 안 넘으면 그대로입니다.
  static String cut(String text, int maxLength) {
    if (text.length() <= maxLength) {
      return text;
    }
    int end = maxLength - CUT_MARK.length();
    // 이모지는 자바 글자 2칸을 씁니다. 그 가운데에서 자르면 깨진 글자가 되므로 한 칸 앞에서 자릅니다.
    if (Character.isHighSurrogate(text.charAt(end - 1))) {
      end--;
    }
    return text.substring(0, end).strip() + CUT_MARK;
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
