package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.call.dto.ChatAbout;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// "코치랑 이야기하기" 주제 목록이 요청 검사(ChatAbout.KINDS) · FE 의 이유 값과 맞는지 확인합니다. (2026-10-18)
class ChatTopicTest {

  @Test
  @DisplayName("요청에서 받는 주제 이름(ChatAbout.KINDS)은 ChatTopic 의 이름과 같다")
  void kindsMatchTopics() {
    String names =
        Arrays.stream(ChatTopic.values()).map(Enum::name).collect(Collectors.joining("|"));

    assertThat(ChatAbout.KINDS).isEqualTo(names);
  }

  @Test
  @DisplayName("글자로 주제를 찾고, 모르는 글자는 빈 값이다")
  void findsTopicByName() {
    assertThat(ChatTopic.from("CARD_FAIL")).contains(ChatTopic.CARD_FAIL);
    assertThat(ChatTopic.from("card_fail")).isEmpty();
    assertThat(ChatTopic.from(null)).isEmpty();
  }

  @Test
  @DisplayName("후기의 이유 값은 FE ShortfallReason, 못 한 카드의 이유 값은 FE CardFailCause 와 같다")
  void tagsMatchFrontEndValues() {
    assertThat(ChatTopic.SESSION_REVIEW.tags().keySet())
        .containsExactly("tooMuch", "stuck", "phone", "mindWander", "tired", "external", "etc");
    assertThat(ChatTopic.CARD_FAIL.tags().keySet())
        .containsExactly(
            "tired", "sick", "event", "tooMuch", "phone", "unwilling", "procrastinated", "etc");
  }

  @Test
  @DisplayName("후기 · 못 한 카드 말고는 이유 값을 쓰지 않는다")
  void otherTopicsHaveNoTags() {
    assertThat(ChatTopic.WEEKLY_REPORT.tags()).isEmpty();
    assertThat(ChatTopic.QUIZ_WRONG.tags()).isEmpty();
    assertThat(ChatTopic.PSYCH_RESULT.tags()).isEmpty();
    assertThat(ChatTopic.DAY_REVIEW.tags()).isEmpty();
    assertThat(ChatTopic.SCHEDULE_ADJUST.tags()).isEmpty();
  }

  @Test
  @DisplayName("일정 조정 제안 주제는 탓하지 않고, 시각이나 양 중 하나를 같이 정하게 안내한다")
  void scheduleAdjustGuide() {
    assertThat(ChatTopic.from("SCHEDULE_ADJUST")).contains(ChatTopic.SCHEDULE_ADJUST);
    assertThat(ChatTopic.SCHEDULE_ADJUST.label()).isEqualTo("일정 조정 제안");
    assertThat(ChatTopic.SCHEDULE_ADJUST.guide())
        .anyMatch(line -> line.contains("탓하지 마"))
        .anyMatch(line -> line.contains("day_plan"))
        .anyMatch(line -> line.contains("깎아내리지 마"));
  }
}
