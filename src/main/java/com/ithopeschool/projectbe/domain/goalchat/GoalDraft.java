package com.ithopeschool.projectbe.domain.goalchat;

import com.ithopeschool.projectbe.domain.care.ToneGuide;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

// '목표 만들기' 대화가 끝나고 GPT 가 채운 목표 초안을 검사한 결과입니다. (이슈 #239)
// 칸 이름 · 뜻 · 글자 수는 목표 저장(BE #221 POST /api/v1/goals)과 FE Goal 과 같습니다. FE 는 이 값을 저장 화면에 채워 확인받습니다.
// GPT 도 틀릴 수 있어서 그대로 쓰지 않습니다. 규칙에 안 맞는 칸은 버리고(null) 학생이 직접 쓰게 합니다.
//
// 칸마다 검사 (앞뒤 공백은 지웁니다)
//   kind                 "score" 또는 "completion" 만. 아니면 null
//   title                1 ~ 40자
//   dDay                 'YYYY-MM-DD' 이고 오늘(새벽 4시 기준)보다 뒤. 오늘이거나 지난 날이면 null
//   currentScore         점수형일 때 1 ~ 20자 글자 (예: "5등급", "62점")
//   totalAmount          완주형일 때 1 ~ 100000 숫자 (예: 300)
//   amountUnit           완주형일 때 1 ~ 5자 글자 (예: "쪽", "강")
//   benefit · cost · obstacle · plan   1 ~ 100자
// 그리고 글 칸에 깎아내리는 말(ToneGuide)이 있으면 그 칸도 버립니다.
// kind 를 모르면 점수형 칸과 완주형 칸을 둘 다 남깁니다. (학생이 유형을 고르면 FE 가 맞는 쪽만 씁니다)
// 칸이 하나도 남지 않으면 초안 자체가 없습니다. (from 이 null)
//
//   { kind "score", title "11월 학평 수학 3등급", dDay "2026-11-19", currentScore "5등급", totalAmount 300 }
//        →  totalAmount 는 null (점수형이라 버림), 나머지는 그대로
//   { kind "goal", title "", dDay "2026-10-17" (오늘) }   →  kind · title · dDay 모두 null
public record GoalDraft(
    String kind,
    String title,
    String dDay,
    String currentScore,
    Integer totalAmount,
    String amountUnit,
    String benefit,
    String cost,
    String obstacle,
    String plan) {

  // 글자 수 · 숫자 한도 (목표 저장 BE #221 의 검사와 같음)
  public static final int MAX_TITLE_LENGTH = 40;
  public static final int MAX_SCORE_LENGTH = 20;
  public static final int MAX_UNIT_LENGTH = 5;
  public static final int MAX_WOOP_LENGTH = 100;
  public static final int MAX_TOTAL_AMOUNT = 100000;

  // GPT 의 답을 검사해서 쓸 수 있는 칸만 남깁니다. 남은 칸이 없으면 null 입니다.
  public static GoalDraft from(GoalChatPrompt.Answer answer, LocalDate today) {
    if (answer == null) {
      return null;
    }
    String kind = kindOf(answer.kind());
    boolean mayBeScore = !"completion".equals(kind);
    boolean mayBeCompletion = !"score".equals(kind);

    GoalDraft draft =
        new GoalDraft(
            kind,
            textOf(answer.title(), MAX_TITLE_LENGTH),
            dDayOf(answer.dDay(), today),
            mayBeScore ? textOf(answer.currentScore(), MAX_SCORE_LENGTH) : null,
            mayBeCompletion ? amountOf(answer.totalAmount()) : null,
            mayBeCompletion ? textOf(answer.amountUnit(), MAX_UNIT_LENGTH) : null,
            textOf(answer.benefit(), MAX_WOOP_LENGTH),
            textOf(answer.cost(), MAX_WOOP_LENGTH),
            textOf(answer.obstacle(), MAX_WOOP_LENGTH),
            textOf(answer.plan(), MAX_WOOP_LENGTH));
    return draft.isEmpty() ? null : draft;
  }

  // 칸이 하나도 없는지
  private boolean isEmpty() {
    return kind == null
        && title == null
        && dDay == null
        && currentScore == null
        && totalAmount == null
        && amountUnit == null
        && benefit == null
        && cost == null
        && obstacle == null
        && plan == null;
  }

  private static String kindOf(String value) {
    if ("score".equals(value) || "completion".equals(value)) {
      return value;
    }
    return null;
  }

  // 1 ~ maxLength 자이고 깎아내리는 말이 없는 글. 아니면 null
  private static String textOf(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    String text = value.strip();
    if (text.isEmpty() || text.length() > maxLength || ToneGuide.hasBannedExpression(text)) {
      return null;
    }
    return text;
  }

  // 'YYYY-MM-DD' 이고 오늘보다 뒤인 날짜. 아니면 null (목표 저장도 "내일 이후" 만 받습니다)
  private static String dDayOf(String value, LocalDate today) {
    if (value == null) {
      return null;
    }
    try {
      LocalDate date = LocalDate.parse(value.strip());
      return date.isAfter(today) ? date.toString() : null;
    } catch (DateTimeParseException e) {
      // 날짜 모양이 아니면 비워 두고 학생이 고르게 합니다.
      return null;
    }
  }

  private static Integer amountOf(Integer value) {
    if (value == null || value < 1 || value > MAX_TOTAL_AMOUNT) {
      return null;
    }
    return value;
  }
}
