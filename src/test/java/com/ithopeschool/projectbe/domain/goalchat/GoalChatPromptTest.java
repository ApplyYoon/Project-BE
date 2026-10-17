package com.ithopeschool.projectbe.domain.goalchat;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.ai.AiQuestion;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest.Line;
import com.ithopeschool.projectbe.domain.care.ToneGuide;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// '목표 만들기' 대화에서 GPT 에게 주는 글(GoalChatPrompt)을 확인합니다. (이슈 #239)
class GoalChatPromptTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

  @Test
  @DisplayName("대화 지침: 오늘 날짜 · WOOP 순서 · 바깥 원인이면 다시 묻기 · 깎아내리지 않기가 들어 있다")
  void instructions() {
    String instructions = GoalChatPrompt.instructions(TODAY);

    assertThat(instructions).contains("오늘은 2026-10-17");
    assertThat(instructions.indexOf("1. 소망"))
        .isLessThan(instructions.indexOf("2. D-day"))
        .isLessThan(instructions.indexOf("4. 결과"))
        .isLessThan(instructions.indexOf("5. 방해물"))
        .isLessThan(instructions.indexOf("6. 대처"));
    assertThat(instructions).contains("그중 내가 바꿀 수 있는 건 뭐야?");
    assertThat(instructions).contains(ToneGuide.NO_PUT_DOWN_RULE);
  }

  @Test
  @DisplayName("첫 질문에는 깎아내리는 말이 없다")
  void openingIsSafe() {
    assertThat(ToneGuide.hasBannedExpression(GoalChatPrompt.OPENING)).isFalse();
  }

  @Test
  @DisplayName("초안 질문: 목표 저장 칸 10개를 모두 null 이 될 수 있는 칸으로 묻고, 대화를 코치/학생 줄로 준다")
  void draftQuestion() {
    List<Line> lines = List.of(new Line("ai", "이루고 싶은 게 뭐야?"), new Line("me", "수학 3등급"));

    AiQuestion question = GoalChatPrompt.draftQuestion(lines, TODAY);

    assertThat(question.name()).isEqualTo("goal_draft");
    assertThat(question.input()).isEqualTo("코치: 이루고 싶은 게 뭐야?\n학생: 수학 3등급");
    assertThat(question.instructions()).contains("오늘은 2026-10-17").contains("40자");
    @SuppressWarnings("unchecked")
    Map<String, Object> properties = (Map<String, Object>) question.schema().get("properties");
    assertThat(properties)
        .containsOnlyKeys(
            "kind",
            "title",
            "dDay",
            "currentScore",
            "totalAmount",
            "amountUnit",
            "benefit",
            "cost",
            "obstacle",
            "plan");
  }
}
