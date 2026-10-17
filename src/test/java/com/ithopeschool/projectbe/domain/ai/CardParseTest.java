package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// "말로 카드 만들기" 의 입력 글과, AI 의 답을 카드 초안으로 바꾸는 검사 규칙을 확인합니다.
class CardParseTest {

  // 2026-10-17 은 토요일
  private static final LocalDate BASE = LocalDate.of(2026, 10, 17);
  private static final Set<String> MY_GOALS = Set.of("4");

  private static CardParse.Answer.Item item(
      Integer offset,
      String time,
      Integer minutes,
      String title,
      List<String> checklist,
      String goal) {
    return new CardParse.Answer.Item(offset, time, minutes, title, checklist, goal);
  }

  private static List<CardParse.Draft> from(CardParse.Answer.Item... items) {
    return CardParse.from(new CardParse.Answer(false, List.of(items)), BASE, MY_GOALS, 25);
  }

  @Test
  @DisplayName("AI 에게 기준 날짜(요일) · 내 진행 중 목표 · 최근 카드 제목 · 문장을 준다 (이름 · 학교는 없음)")
  void input() {
    String input =
        CardParse.input(
            BASE,
            "내일 7시에 수학 문제집 2장",
            List.of(new CardParse.GoalHint("4", "11월 학평 수학 3등급", LocalDate.of(2026, 11, 19))),
            List.of("수학 문제집", "영어 단어"));

    assertThat(input)
        .isEqualTo(
            """
            기준 날짜: 2026-10-17 (토)
            내 진행 중 목표:
            - 4: 11월 학평 수학 3등급 (D-day 2026-11-19)
            최근 카드 제목: 수학 문제집 / 영어 단어

            학생이 말한 문장:
            내일 7시에 수학 문제집 2장""");
    assertThat(CardParse.input(BASE, "수학", List.of(), List.of()))
        .contains("내 진행 중 목표:\n(없음)\n최근 카드 제목: (없음)");
  }

  @Test
  @DisplayName("날짜는 코드가 기준 날짜 + dayOffset 으로 세고, 말한 길이와 시각은 그대로 쓴다")
  void dateTimeAndLength() {
    List<CardParse.Draft> drafts =
        from(item(1, "19:00", 120, " 수학 문제집 ", List.of("문제집 2장 풀기"), "4"));

    assertThat(drafts)
        .containsExactly(
            new CardParse.Draft(
                LocalDate.of(2026, 10, 18),
                "19:00",
                120,
                false,
                "수학 문제집",
                List.of("문제집 2장 풀기"),
                "4"));
  }

  @Test
  @DisplayName("길이를 말하지 않았거나 범위 밖이면 내 기록의 한 칸 길이로 채우고 표시한다")
  void lengthFromRecord() {
    List<CardParse.Draft> drafts =
        from(
            item(0, null, null, "영어 단어", List.of("단어 30개"), null),
            item(0, null, 700, "국어", List.of(), null));

    assertThat(drafts).extracting(CardParse.Draft::durationMinutes).containsExactly(25, 25);
    assertThat(drafts).allMatch(CardParse.Draft::durationFromRecord);
  }

  @Test
  @DisplayName("틀린 시각은 비우고, 내 진행 중 목표가 아닌 번호도 비운다 (카드는 버리지 않음)")
  void clearsBadTimeAndGoal() {
    CardParse.Draft draft = from(item(0, "25:00", 30, "수학", List.of("오답"), "99")).get(0);

    assertThat(draft.startTime()).isNull();
    assertThat(draft.goalId()).isNull();
  }

  @Test
  @DisplayName("제목이 비었거나 30자를 넘거나 깎아내리는 말이 있거나, 날짜가 2주 밖이면 그 카드는 버린다")
  void dropsBadCards() {
    List<CardParse.Draft> drafts =
        from(
            item(0, null, 30, " ", List.of(), null),
            item(0, null, 30, "가".repeat(31), List.of(), null),
            item(0, null, 30, "게으른 나를 위한 수학", List.of(), null),
            item(14, null, 30, "영어", List.of(), null),
            item(-1, null, 30, "영어", List.of(), null),
            item(13, null, 30, "과학", List.of(), null));

    assertThat(drafts).extracting(CardParse.Draft::title).containsExactly("과학");
    assertThat(drafts.get(0).date()).isEqualTo(LocalDate.of(2026, 10, 30));
  }

  @Test
  @DisplayName("체크리스트는 빈 · 긴 · 겹치는 항목을 버리고 5개까지, 남은 게 없으면 제목 하나로 채운다")
  void checklist() {
    List<String> many =
        new ArrayList<>(List.of(" 1쪽 ", "1쪽", "", "가".repeat(51), "2쪽", "3쪽", "4쪽", "5쪽", "6쪽"));

    assertThat(from(item(0, null, 30, "수학", many, null)).get(0).checklist())
        .containsExactly("1쪽", "2쪽", "3쪽", "4쪽", "5쪽");
    assertThat(from(item(0, null, 30, "수학", null, null)).get(0).checklist()).containsExactly("수학");
  }

  @Test
  @DisplayName("초안은 10장까지만")
  void maxDrafts() {
    List<CardParse.Answer.Item> items = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      items.add(item(0, null, 30, "카드 " + i, List.of("하기"), null));
    }

    assertThat(CardParse.from(new CardParse.Answer(false, items), BASE, MY_GOALS, 25)).hasSize(10);
    assertThat(CardParse.from(new CardParse.Answer(false, null), BASE, MY_GOALS, 25)).isEmpty();
  }
}
