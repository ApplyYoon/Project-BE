package com.ithopeschool.projectbe.domain.psychtest;

// 온보딩 심리검사의 문장 35개의 코드입니다. (이슈 #219, FE src/lib/psychTest.ts 의 psychTestItems)
// 척도마다 문장이 5개씩입니다. (S1 ~ S5 … C1 ~ C5) 문장 글은 FE 에만 있고, 서버는 코드와 척도만 압니다.
// 처음에는 척도마다 2개(14문항, 기획서 6.2)였다가 2026-10-17 에 35문항으로 늘렸습니다. 옛 14문항은 앞의 두 코드(S1 · S2 …)입니다.
// (문장을 다듬어도 코드는 바꾸지 않습니다. 코드를 바꾸면 FE 와 같이 바꿔야 합니다)
public enum PsychItem {
  S1(PsychScale.START_RESISTANCE),
  S2(PsychScale.START_RESISTANCE),
  S3(PsychScale.START_RESISTANCE),
  S4(PsychScale.START_RESISTANCE),
  S5(PsychScale.START_RESISTANCE),
  A1(PsychScale.DISTRACTION),
  A2(PsychScale.DISTRACTION),
  A3(PsychScale.DISTRACTION),
  A4(PsychScale.DISTRACTION),
  A5(PsychScale.DISTRACTION),
  P1(PsychScale.PLAN_OPTIMISM),
  P2(PsychScale.PLAN_OPTIMISM),
  P3(PsychScale.PLAN_OPTIMISM),
  P4(PsychScale.PLAN_OPTIMISM),
  P5(PsychScale.PLAN_OPTIMISM),
  R1(PsychScale.RECORD_BURDEN),
  R2(PsychScale.RECORD_BURDEN),
  R3(PsychScale.RECORD_BURDEN),
  R4(PsychScale.RECORD_BURDEN),
  R5(PsychScale.RECORD_BURDEN),
  F1(PsychScale.FAIL_SENSITIVITY),
  F2(PsychScale.FAIL_SENSITIVITY),
  F3(PsychScale.FAIL_SENSITIVITY),
  F4(PsychScale.FAIL_SENSITIVITY),
  F5(PsychScale.FAIL_SENSITIVITY),
  E1(PsychScale.EXTERNAL_ATTRIBUTION),
  E2(PsychScale.EXTERNAL_ATTRIBUTION),
  E3(PsychScale.EXTERNAL_ATTRIBUTION),
  E4(PsychScale.EXTERNAL_ATTRIBUTION),
  E5(PsychScale.EXTERNAL_ATTRIBUTION),
  C1(PsychScale.CARE_PREFERENCE),
  C2(PsychScale.CARE_PREFERENCE),
  C3(PsychScale.CARE_PREFERENCE),
  C4(PsychScale.CARE_PREFERENCE),
  C5(PsychScale.CARE_PREFERENCE);

  // 이 문장이 재는 척도
  private final PsychScale scale;

  PsychItem(PsychScale scale) {
    this.scale = scale;
  }

  public PsychScale scale() {
    return scale;
  }

  // FE 가 보낸 코드를 문항으로 바꿉니다. 없는 코드면 null 입니다.   "S1" -> S1
  public static PsychItem fromCode(String code) {
    for (PsychItem item : values()) {
      if (item.name().equals(code)) {
        return item;
      }
    }
    return null;
  }

  // 옛 14문항 검사의 코드인지 (척도마다 앞의 두 문장: S1 · S2 · A1 · A2 …)
  public boolean isInOldTest() {
    return name().endsWith("1") || name().endsWith("2");
  }
}
