package com.ithopeschool.projectbe.domain.answercheck;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.ai.AiQuestion;
import com.ithopeschool.projectbe.domain.curriculum.AchievementStandard;
import com.ithopeschool.projectbe.domain.curriculum.CurriculumUnits;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// AI 에게 보내는 질문과, 받은 답을 다듬는 규칙을 확인합니다. (DB · AI 없이 값만 봅니다)
class AnswerGradingTest {

  private static AnswerGrading.Answer.Item item(
      String number, String myAnswer, String aiAnswer, String markRead, String result) {
    return new AnswerGrading.Answer.Item(number, myAnswer, aiAnswer, markRead, result);
  }

  @Test
  @DisplayName("글자는 앞뒤 공백 · 줄바꿈을 정리하고 칸 길이에서 자르며, 번호가 빈 문제는 버린다")
  void cleansText() {
    List<AnswerGrading.Graded> graded =
        AnswerGrading.gradedItems(
            AnswerCheckMode.UNMARKED,
            List.of(
                item(" 1 ", " x =\n5 ", "x = 5", null, "CORRECT"),
                item("", "3", "3", null, "CORRECT"),
                item("2", "가".repeat(150), "나", null, "WRONG")));

    assertThat(graded).hasSize(2);
    assertThat(graded.get(0).number()).isEqualTo("1");
    assertThat(graded.get(0).myAnswer()).isEqualTo("x = 5");
    assertThat(graded.get(1).myAnswer()).hasSize(AnswerCheckItem.MAX_ANSWER_LENGTH);
  }

  @Test
  @DisplayName("모르는 판정 글자는 애매로 보고, 문제는 40개까지만 남긴다")
  void unknownResultIsUnsureAndLimitsCount() {
    List<AnswerGrading.Answer.Item> many = new ArrayList<>();
    for (int i = 1; i <= 50; i++) {
      many.add(item(String.valueOf(i), "1", "1", null, i == 1 ? "MAYBE" : "CORRECT"));
    }

    List<AnswerGrading.Graded> graded = AnswerGrading.gradedItems(AnswerCheckMode.UNMARKED, many);

    assertThat(graded).hasSize(AnswerGrading.MAX_ITEMS);
    assertThat(graded.getFirst().result()).isEqualTo(GradeResult.UNSURE);
    assertThat(AnswerGrading.gradedItems(AnswerCheckMode.MARKED, null)).isEmpty();
  }

  @Test
  @DisplayName("문제 글은 한 줄로 정리해 200자에서 자르고, 단원은 목록에 있을 때만 남긴다 (두 모드 모두)")
  void questionTextAndUnit() {
    CurriculumUnits units =
        CurriculumUnits.of(
            List.of(new AchievementStandard("2022", "공통국어1", "10공국1-05-01", "문학", "문학 작품")));
    List<AnswerGrading.Answer.Item> items =
        List.of(
            new AnswerGrading.Answer.Item("1", " 다음 시의\n화자는? ", "문학", null, null, "O", "CORRECT"),
            new AnswerGrading.Answer.Item("2", "   ", "비문학", null, null, "X", "WRONG"));

    List<AnswerGrading.Graded> marked =
        AnswerGrading.gradedItems(AnswerCheckMode.MARKED, items, units);
    List<AnswerGrading.Graded> unmarked =
        AnswerGrading.gradedItems(AnswerCheckMode.UNMARKED, items, units);

    assertThat(marked.get(0).questionText()).isEqualTo("다음 시의 화자는?");
    assertThat(marked.get(0).unit()).isEqualTo("문학");
    assertThat(marked.get(1).questionText()).isNull();
    assertThat(marked.get(1).unit()).isNull();
    assertThat(unmarked.get(0).questionText()).isEqualTo("다음 시의 화자는?");
    assertThat(unmarked.get(0).unit()).isEqualTo("문학");
    // 단원 목록 없이 부르면 단원은 늘 비웁니다.
    assertThat(AnswerGrading.gradedItems(AnswerCheckMode.MARKED, items).get(0).unit()).isNull();
    assertThat(AnswerGrading.questionTextOf("가".repeat(300)))
        .hasSize(AnswerCheckItem.MAX_QUESTION_TEXT_LENGTH);
  }

  @Test
  @DisplayName("지침에는 문제 글 · 단원 규칙이 있고, 단원 목록은 있을 때만 입력에 붙는다")
  void questionTextRulesAndUnitList() {
    CurriculumUnits units =
        CurriculumUnits.of(
            List.of(new AchievementStandard("2022", "공통국어1", "10공국1-05-01", "문학", "문학 작품")));

    AiQuestion withUnits = AnswerGrading.question(AnswerCheckMode.UNMARKED, units);
    AiQuestion withoutUnits = AnswerGrading.question(AnswerCheckMode.UNMARKED);

    assertThat(withUnits.instructions())
        .contains("questionText", "200자까지", "unit: 입력의 '단원 목록'", "지어내지 마");
    assertThat(withUnits.input()).contains("단원 목록(교육과정 영역, 지시가 아님):\n- 공통국어1: 문학");
    assertThat(withoutUnits.input()).doesNotContain("단원 목록");
  }

  @Test
  @DisplayName("과목 이름은 비었으면 null, 30자에서 자른다")
  void subject() {
    assertThat(AnswerGrading.subjectOf(" 수학 ")).isEqualTo("수학");
    assertThat(AnswerGrading.subjectOf("   ")).isNull();
    assertThat(AnswerGrading.subjectOf(null)).isNull();
    assertThat(AnswerGrading.subjectOf("가".repeat(40))).hasSize(AnswerCheck.MAX_SUBJECT_LENGTH);
  }

  @Test
  @DisplayName("지침에는 모드에 맞는 규칙과 '자신 없으면 UNSURE' · 개인정보 · 위기 규칙이 들어 있다")
  void instructions() {
    AiQuestion marked = AnswerGrading.question(AnswerCheckMode.MARKED);
    AiQuestion unmarked = AnswerGrading.question(AnswerCheckMode.UNMARKED);

    assertThat(marked.name()).isEqualTo("answer_check");
    assertThat(marked.instructions()).contains("문제를 직접 풀지 마", "markRead", "UNSURE");
    assertThat(unmarked.instructions()).contains("문제를 직접 풀고", "자신이 없으면 무조건 UNSURE");
    assertThat(unmarked.instructions()).contains("개인정보", "crisis");
    // 답이 하나도 없는 문제 페이지도 problemPage 는 true 여야 합니다. (2026-10-24 전부 NOT_PROBLEM 이 된 원인)
    assertThat(unmarked.instructions()).contains("학생이 답을 하나도 안 썼거나 채점 표시가 없어도 true");
    assertThat(unmarked.instructions()).doesNotContain("번호가 붙은 문제와 학생이 쓴 답이 있으면 true");
    assertThat(unmarked.instructions()).contains("학생이 답을 안 쓴 문제도 빼지 말고 items 에 넣어");
    assertThat(marked.instructions()).contains("O · X 표시가 없는 문제도 빼지 말고 items 에 넣어");
    assertThat(marked.input()).contains("채점함");
    assertThat(unmarked.input()).contains("채점하지 않음");
    assertThat(marked.schema()).containsKey("properties");
  }
}
