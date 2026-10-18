package com.ithopeschool.projectbe.domain.ai;

// AI 가 만드는 공부 카드 한 장의 길이 기준입니다. (2026-10-18 멘토 결정: "누가 10분씩 해 공부를")
// 하루 계획 · 계획 고치기 · 긴 계획 · 한 칸 길이 · 채팅의 카드 버튼이 모두 이 숫자를 씁니다.
//
// 왜 30분부터인가요?
//   학교 한 교시가 50분이고, 공부는 집중이 붙는 데만 10 ~ 15분이 걸립니다.
//   그래서 30분보다 짧은 칸은 "공부 블록" 으로 보지 않습니다. 보통은 40 ~ 60분으로 잡습니다.
//   "줄여 줘" 처럼 양을 줄일 때는 한 장의 길이를 깎지 않고 카드 수를 줄입니다.
// ⚠️ 학생이 카드 폼에서 직접 정하는 길이는 이 기준과 상관없이 자유입니다. AI 가 만드는 카드에만 씁니다.
public final class StudyBlock {

  // 카드 한 장의 가장 짧은 길이 (분)
  public static final int MIN_MINUTES = 30;
  // 보통 길이 (분) — AI 지침에 "보통 40 ~ 60분" 으로 알려 줍니다
  public static final int USUAL_MIN_MINUTES = 40;
  public static final int USUAL_MAX_MINUTES = 60;
  // 아무 기록이 없을 때 쓰는 길이 (분) — 학교 한 교시
  public static final int DEFAULT_MINUTES = 50;
  // 카드 한 장의 가장 긴 길이 (분)
  public static final int MAX_MINUTES = 120;

  // AI 지침에 넣는 한 줄
  public static final String PROMPT_RULE =
      "공부 카드 한 장은 "
          + MIN_MINUTES
          + " ~ "
          + MAX_MINUTES
          + "분, 보통 "
          + USUAL_MIN_MINUTES
          + " ~ "
          + USUAL_MAX_MINUTES
          + "분(학교 한 교시 50분)으로 잡아. "
          + MIN_MINUTES
          + "분보다 짧은 카드는 만들지 마.";

  private StudyBlock() {}
}
