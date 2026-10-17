package com.ithopeschool.projectbe.domain.goalchat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// '목표 만들기' 대화의 초안 검사(GoalDraft 주석의 규칙)를 확인합니다. (이슈 #239)
// 글자 수 · 날짜 규칙은 목표 저장(BE #221 POST /goals)과 같습니다.
class GoalDraftTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

  private static GoalChatPrompt.Answer score(String title, String dDay, String currentScore) {
    return new GoalChatPrompt.Answer(
        "score",
        title,
        dDay,
        currentScore,
        300,
        "쪽",
        "등급이 오르면 자신감이 생김",
        "또 포기하게 됨",
        "폰을 자꾸 봄",
        "폰이 보이면 다른 방에 둔다");
  }

  @Test
  @DisplayName("점수형: 완주형 칸(totalAmount · amountUnit)은 버리고 나머지는 그대로")
  void scoreGoal() {
    GoalDraft draft = GoalDraft.from(score(" 11월 학평 수학 3등급 ", "2026-11-19", "5등급"), TODAY);

    assertThat(draft)
        .isEqualTo(
            new GoalDraft(
                "score",
                "11월 학평 수학 3등급",
                "2026-11-19",
                "5등급",
                null,
                null,
                "등급이 오르면 자신감이 생김",
                "또 포기하게 됨",
                "폰을 자꾸 봄",
                "폰이 보이면 다른 방에 둔다"));
  }

  @Test
  @DisplayName("완주형: 점수 칸(currentScore)은 버리고 분량 · 단위만")
  void completionGoal() {
    GoalChatPrompt.Answer answer =
        new GoalChatPrompt.Answer(
            "completion", "영단어장 1회독", "2026-11-30", "5등급", 300, "쪽", "좋은 점", "안 하면", "방해물", "대처");

    GoalDraft draft = GoalDraft.from(answer, TODAY);

    assertThat(draft.currentScore()).isNull();
    assertThat(draft.totalAmount()).isEqualTo(300);
    assertThat(draft.amountUnit()).isEqualTo("쪽");
  }

  @Test
  @DisplayName("유형을 모르면 kind 는 null 이고, 점수형 칸과 완주형 칸을 둘 다 남긴다")
  void unknownKindKeepsBoth() {
    GoalChatPrompt.Answer answer =
        new GoalChatPrompt.Answer("goal", "수학", null, "5등급", 300, "쪽", null, null, null, null);

    GoalDraft draft = GoalDraft.from(answer, TODAY);

    assertThat(draft.kind()).isNull();
    assertThat(draft.currentScore()).isEqualTo("5등급");
    assertThat(draft.totalAmount()).isEqualTo(300);
  }

  @Test
  @DisplayName("D-day 는 오늘보다 뒤인 'YYYY-MM-DD' 만. 오늘 · 지난 날 · 모양이 다르면 null")
  void dDayRules() {
    assertThat(GoalDraft.from(score("목표", "2026-10-18", "5등급"), TODAY).dDay())
        .isEqualTo("2026-10-18");
    assertThat(GoalDraft.from(score("목표", "2026-10-17", "5등급"), TODAY).dDay()).isNull();
    assertThat(GoalDraft.from(score("목표", "2026-09-01", "5등급"), TODAY).dDay()).isNull();
    assertThat(GoalDraft.from(score("목표", "11월 19일", "5등급"), TODAY).dDay()).isNull();
  }

  @Test
  @DisplayName("글자 수를 넘거나 비었거나 깎아내리는 말이 든 칸은 버린다")
  void textRules() {
    String title41 = "가".repeat(41);
    String score21 = "가".repeat(21);
    GoalChatPrompt.Answer answer =
        new GoalChatPrompt.Answer(
            "completion",
            title41,
            "2026-11-30",
            score21,
            100001,
            "페이지씩 넘기기",
            "   ",
            "가".repeat(101),
            "한심하게 폰만 봄",
            "가".repeat(100));

    GoalDraft draft = GoalDraft.from(answer, TODAY);

    assertThat(draft.title()).isNull();
    assertThat(draft.totalAmount()).isNull();
    assertThat(draft.amountUnit()).isNull();
    assertThat(draft.benefit()).isNull();
    assertThat(draft.cost()).isNull();
    assertThat(draft.obstacle()).isNull();
    assertThat(draft.plan()).hasSize(100);
  }

  @Test
  @DisplayName("남은 칸이 하나도 없거나 답이 없으면 초안 자체가 null")
  void emptyDraftIsNull() {
    GoalChatPrompt.Answer empty =
        new GoalChatPrompt.Answer(null, " ", "어제", null, 0, "", null, null, null, null);

    assertThat(GoalDraft.from(empty, TODAY)).isNull();
    assertThat(GoalDraft.from(null, TODAY)).isNull();
  }
}
