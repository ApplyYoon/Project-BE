package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest.Line;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 같은 말 되풀이 검사 규칙의 단위 테스트입니다. (채팅 수정 3)
class RepeatCheckTest {

  private static List<Line> aiSaid(String... aiTexts) {
    List<Line> lines = new ArrayList<>();
    for (String text : aiTexts) {
      lines.add(new Line("ai", text));
      lines.add(new Line("me", "응"));
    }
    return lines;
  }

  @Test
  @DisplayName("직전 AI 답은 마지막 3개만, 순서대로 본다 (학생 말은 빼고)")
  void recentAiReplies() {
    List<Line> lines = aiSaid("A", "B", "C", "D");

    assertThat(RepeatCheck.recentAiReplies(lines)).containsExactly("B", "C", "D");
    assertThat(RepeatCheck.recentAiReplies(List.of(new Line("me", "안녕")))).isEmpty();
  }

  @Test
  @DisplayName("첫 문장은 처음 나오는 . ? ! 또는 줄바꿈까지이고, 40자가 넘으면 자른다")
  void firstSentence() {
    assertThat(RepeatCheck.firstSentence("오늘은 10분만 해도 충분해. 피곤하면 쉬어도 돼."))
        .isEqualTo("오늘은 10분만 해도 충분해.");
    assertThat(RepeatCheck.firstSentence("괜찮아?\n천천히 하자")).isEqualTo("괜찮아?");
    assertThat(RepeatCheck.firstSentence("첫 줄\n둘째 줄")).isEqualTo("첫 줄");
    assertThat(RepeatCheck.firstSentence("그랬구나")).isEqualTo("그랬구나");
    assertThat(RepeatCheck.firstSentence("가".repeat(50))).hasSize(40);
  }

  @Test
  @DisplayName("[이미 한 말] 칸에 직전 AI 답의 첫 문장을 넣고, AI 줄이 없으면 빈 글자다")
  void section() {
    String section = RepeatCheck.section(aiSaid("어제 어땠어?", "오늘은 10분만 해도 충분해. 짧게 하자."));

    assertThat(section)
        .startsWith("\n\n[이미 한 말]")
        .contains("- \"어제 어땠어?\"")
        .contains("- \"오늘은 10분만 해도 충분해.\"")
        .doesNotContain("짧게 하자")
        .contains("다시 쓰지 말고, 학생이 방금 한 말에 맞춰 새로 답해");
    assertThat(RepeatCheck.section(List.of(new Line("me", "안녕")))).isEmpty();
  }

  @Test
  @DisplayName("공백 · 문장부호를 지우고 첫 문장이 같으면 반복이다")
  void sameFirstSentence() {
    List<Line> lines = aiSaid("오늘은 10분만 해도 충분해. 피곤하면 짧게 하자.");

    assertThat(RepeatCheck.repeats("오늘은 10분만 해도 충분해! 미적분은 천천히.", lines)).isTrue();
    assertThat(RepeatCheck.repeats("오늘은10분만 해도충분해~~", lines)).isTrue();
    // 짧은 첫 문장도 똑같으면 반복입니다.
    assertThat(RepeatCheck.repeats("그랬구나. 무슨 일 있었어?", aiSaid("그랬구나! 많이 힘들었지?"))).isTrue();
  }

  @Test
  @DisplayName("12글자 이상 이어서 같으면 반복이고, 11글자까지는 반복이 아니다")
  void longSamePiece() {
    List<Line> lines = aiSaid("오늘은 10분만 해도 충분해.");

    // "오늘은10분만해도충분해" 12글자가 문장 가운데에 들어 있음
    assertThat(RepeatCheck.repeats("많이 피곤했구나 오늘은 10분만 해도 충분해 알지?", lines)).isTrue();
    // "늘은10분만해도충분해" 11글자만 같음
    assertThat(RepeatCheck.repeats("음 늘은 10분만 해도 충분해 진짜", lines)).isFalse();
  }

  @Test
  @DisplayName("학생 말에 맞춘 새 답은 반복이 아니다")
  void freshReplyIsNotRepeat() {
    List<Line> lines = aiSaid("오늘은 10분만 해도 충분해. 피곤하면 짧게 하자.");

    assertThat(RepeatCheck.repeats("미적분이 불안하구나. 어느 단원이 제일 걱정돼?", lines)).isFalse();
    // AI 줄이 없는 대화는 늘 반복이 아닙니다.
    assertThat(RepeatCheck.repeats("안녕", List.of(new Line("me", "안녕")))).isFalse();
  }

  @Test
  @DisplayName("직전 3개보다 앞의 AI 답은 비교하지 않는다")
  void onlyLastThree() {
    List<Line> lines = aiSaid("오늘은 10분만 해도 충분해.", "B 이야기", "C 이야기", "D 이야기");

    assertThat(RepeatCheck.repeats("오늘은 10분만 해도 충분해.", lines)).isFalse();
  }
}
