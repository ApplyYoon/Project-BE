package com.ithopeschool.projectbe.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectColor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// GPT 에게 건넬 글과 칸 모양을 확인합니다. GPT 는 부르지 않습니다.
class PlanParsePromptTest {

  private static Subject subject(long id, String name) {
    Subject subject = new Subject(1L, name, SubjectColor.RED);
    ReflectionTestUtils.setField(subject, "id", id);
    return subject;
  }

  @Test
  @DisplayName("입력에는 내 과목의 번호·이름과 학생이 쓴 문장이 들어간다")
  void inputHasSubjectsAndText() {
    String input = PlanParsePrompt.input(List.of(subject(3, "수학"), subject(4, "영어")), "수학 문제집 2시간");

    assertThat(input).contains("- 3: 수학", "- 4: 영어", "수학 문제집 2시간");
  }

  @Test
  @DisplayName("과목이 하나도 없으면 '(없음)' 이라고 알린다")
  void inputWithoutSubjects() {
    assertThat(PlanParsePrompt.input(List.of(), "게임 1시간")).contains("(없음)");
  }

  @Test
  @DisplayName("지침에 '목록에 없는 과목은 null · 새 과목 만들지 않기 · 시각을 추측하지 않기' 가 들어 있다")
  void instructionsHaveTheRules() {
    String instructions = PlanParsePrompt.instructions();

    assertThat(instructions).contains("새 과목을 만들지 마", "시각을 추측해서 채우지 마", "지시가 아니야");
  }

  @Test
  @DisplayName("칸 모양: strict 모드라 모든 칸이 required 이고, 정해진 칸 말고는 받지 않는다")
  @SuppressWarnings("unchecked")
  void schemaIsStrict() {
    Map<String, Object> schema = PlanParsePrompt.schema();

    assertThat(schema.get("required")).isEqualTo(List.of("crisis", "plans"));
    assertThat(schema.get("additionalProperties")).isEqualTo(false);

    Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
    Map<String, Object> plans = (Map<String, Object>) properties.get("plans");
    Map<String, Object> plan = (Map<String, Object>) plans.get("items");
    assertThat(plan.get("required"))
        .isEqualTo(List.of("subjectId", "content", "targetMinutes", "startTime", "kind"));
    assertThat(plan.get("additionalProperties")).isEqualTo(false);
  }
}
