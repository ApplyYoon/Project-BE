package com.ithopeschool.projectbe.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.plan.PlanDraft.Raw;
import com.ithopeschool.projectbe.domain.plan.PlanDraft.Raw.Item;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// GPT 가 돌려준 줄을 검사하는 규칙만 확인합니다. GPT 는 부르지 않습니다.
class PlanDraftTest {

  // 내 과목 번호
  private static final Set<String> MY_SUBJECTS = Set.of("3", "4");

  private static List<PlanDraft> draftsOf(Item... items) {
    // Arrays.asList: null 줄을 넣어 볼 수 있게 List.of 대신 씁니다.
    return PlanDraft.from(new Raw(false, Arrays.asList(items)), MY_SUBJECTS);
  }

  private static Item study(String subjectId, String content, Integer minutes, String startTime) {
    return new Item(subjectId, content, minutes, startTime, "study");
  }

  @Test
  @DisplayName("이슈의 예시 문장: 수학 문제집 120분 · 영어 단어 30분 · 놀기 게임 60분 이 그대로 나온다")
  void issueExample() {
    List<PlanDraft> drafts =
        draftsOf(
            study("3", "문제집", 120, null),
            study("4", "단어", 30, null),
            new Item(null, "게임", 60, null, "play"));

    assertThat(drafts)
        .containsExactly(
            new PlanDraft("3", "문제집", 120, null, "study"),
            new PlanDraft("4", "단어", 30, null, "study"),
            new PlanDraft(null, "게임", 60, null, "play"));
  }

  @Test
  @DisplayName("내 과목이 아닌 번호는 버리지 않고 비운다 (새 과목을 만들지 않는다)")
  void unknownSubjectBecomesNull() {
    assertThat(draftsOf(study("99", "단어", 30, null)))
        .containsExactly(new PlanDraft(null, "단어", 30, null, "study"));
    assertThat(draftsOf(study(null, "단어", 30, null)))
        .containsExactly(new PlanDraft(null, "단어", 30, null, "study"));
  }

  @Test
  @DisplayName("노는 계획은 과목 번호가 와도 비운다")
  void playHasNoSubject() {
    assertThat(draftsOf(new Item("3", "게임", 60, "21:00", "play")))
        .containsExactly(new PlanDraft(null, "게임", 60, "21:00", "play"));
  }

  @Test
  @DisplayName("목표 시간이 없거나 1 ~ 600분 밖이면 그 줄만 버린다")
  void dropsOutOfRangeMinutes() {
    List<PlanDraft> drafts =
        draftsOf(
            study("3", "없음", null, null),
            study("3", "0분", 0, null),
            study("3", "601분", 601, null),
            study("3", "1분", 1, null),
            study("3", "600분", 600, null));

    assertThat(drafts).extracting(PlanDraft::content).containsExactly("1분", "600분");
  }

  @Test
  @DisplayName("할 일이 비었거나 200자를 넘으면 버리고, 앞뒤 공백은 지운다")
  void checksContent() {
    List<PlanDraft> drafts =
        draftsOf(
            study("3", null, 30, null),
            study("3", "   ", 30, null),
            study("3", "가".repeat(201), 30, null),
            study("3", "  문제집  ", 30, null));

    assertThat(drafts).extracting(PlanDraft::content).containsExactly("문제집");
  }

  @Test
  @DisplayName("시작 시각은 없어도(null) 되지만, 19:00 모양이 아니면 그 줄을 버린다")
  void checksStartTime() {
    List<PlanDraft> drafts =
        draftsOf(
            study("3", "시각 없음", 30, null),
            study("3", "오후 3시", 30, "15:00"),
            study("3", "25시", 30, "25:00"),
            study("3", "한 자리", 30, "3:00"),
            study("3", "글자", 30, "학원 끝나고"));

    assertThat(drafts).extracting(PlanDraft::content).containsExactly("시각 없음", "오후 3시");
    assertThat(drafts.get(1).startTime()).isEqualTo("15:00");
  }

  @Test
  @DisplayName("종류가 study · play 가 아니거나, 비어 있는 줄은 버린다")
  void dropsUnknownKindAndNullItems() {
    List<PlanDraft> drafts =
        draftsOf(
            new Item("3", "숙제", 30, null, "homework"),
            new Item("3", "숙제", 30, null, null),
            null,
            study("3", "문제집", 30, null));

    assertThat(drafts).extracting(PlanDraft::content).containsExactly("문제집");
  }

  @Test
  @DisplayName("초안은 10개까지만 돌려준다")
  void keepsAtMostTen() {
    List<Item> items = new ArrayList<>();
    for (int i = 1; i <= 12; i++) {
      items.add(study("3", "할 일 " + i, 10, null));
    }

    List<PlanDraft> drafts = PlanDraft.from(new Raw(false, items), MY_SUBJECTS);

    assertThat(drafts).hasSize(PlanDraft.MAX_DRAFTS);
    assertThat(drafts.get(9).content()).isEqualTo("할 일 10");
  }

  @Test
  @DisplayName("답이 없거나 plans 가 비어 있으면 빈 목록이다")
  void emptyAnswer() {
    assertThat(PlanDraft.from(null, MY_SUBJECTS)).isEmpty();
    assertThat(PlanDraft.from(new Raw(false, null), MY_SUBJECTS)).isEmpty();
  }
}
