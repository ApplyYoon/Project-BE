package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.call.CallContext.Facts;
import com.ithopeschool.projectbe.domain.call.CallContext.GoalLine;
import com.ithopeschool.projectbe.domain.call.CallContext.NextExam;
import com.ithopeschool.projectbe.domain.call.CallContext.PastExam;
import com.ithopeschool.projectbe.domain.call.CallContext.ScoreLine;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 값만 계산하는 함수라서 DB 없이 확인합니다.
class CallContextTest {

  private static final GoalLine MATH_GOAL =
      new GoalLine("11월 학평 수학 3등급", 33, "지금 5등급", "학원 다녀오면 피곤하다고 미뤄요", "학원 끝나면 씻기 전에 오답 1개부터 펴요");

  @Test
  @DisplayName("꺼낼 기록이 하나도 없으면 빈 글자라서 대본이 그대로다")
  void emptyWhenNothing() {
    assertThat(CallContext.section(Facts.NONE)).isEmpty();
  }

  @Test
  @DisplayName("기록이 있으면 빈 줄 두 개 뒤에 [기록에서 꺼낸 것] 칸을 만들고, 지어내지 말라는 줄을 먼저 넣는다")
  void sectionHeader() {
    String section = CallContext.section(new Facts(new NextExam("11월 학평", 33), null, List.of()));

    assertThat(section).startsWith("\n\n[기록에서 꺼낸 것]\n");
    assertThat(section).contains("숫자를 지어내지 마", "- 다음 시험: 11월 학평 (D-33)");
  }

  @Test
  @DisplayName("시험 당일이면 D-day 로 적는다")
  void examToday() {
    assertThat(CallContext.examLines(new NextExam("중간고사", 0), null))
        .containsExactly("- 다음 시험: 중간고사 (D-day). 남은 날만 말하고 겁주지 마.");
  }

  @Test
  @DisplayName("지난 시험은 과목마다 예상 → 실제 점수를 한 줄로 적고, 비교하거나 깎아내리지 말라는 줄을 붙인다")
  void pastExamScores() {
    List<String> lines =
        CallContext.examLines(
            null,
            new PastExam(
                "9월 학평", List.of(new ScoreLine("국어", 70, 66), new ScoreLine("수학", 55, 41))));

    assertThat(lines.get(0)).isEqualTo("- 지난 시험 9월 학평 (예상 → 실제): 국어 70 → 66점, 수학 55 → 41점");
    assertThat(lines.get(1)).contains("비교하거나 깎아내리지 마");
  }

  @Test
  @DisplayName("목표는 제목 · 남은 날 · 지금 수준, 스스로 적은 걸림돌과 방법을 적는다")
  void goalLines() {
    List<String> lines = CallContext.goalLines(List.of(MATH_GOAL));

    assertThat(lines)
        .startsWith(
            "- 목표: 「11월 학평 수학 3등급」 (D-33, 지금 5등급)",
            "  - 스스로 적은 걸림돌: 「학원 다녀오면 피곤하다고 미뤄요」",
            "  - 그때 하기로 한 방법: 「학원 끝나면 씻기 전에 오답 1개부터 펴요」");
  }

  @Test
  @DisplayName("걸림돌 · 방법 · 지금 수준이 없으면 그 줄을 빼고, 목표가 없으면 아무 줄도 없다")
  void goalWithoutWoop() {
    assertThat(CallContext.goalLines(List.of(new GoalLine("영단어장 1회독", 20, null, null, null))))
        .startsWith("- 목표: 「영단어장 1회독」 (D-20)")
        .noneMatch(line -> line.contains("걸림돌:") || line.contains("방법:"));
    assertThat(CallContext.goalLines(List.of())).isEmpty();
  }

  @Test
  @DisplayName("학생이 쓴 글은 줄바꿈을 띄어쓰기로 바꾸고, 40자가 넘으면 잘라 … 를 붙인다")
  void cutsLongText() {
    assertThat(CallContext.cut("학원\n다녀오면  피곤")).isEqualTo("학원 다녀오면 피곤");
    assertThat(CallContext.cut("가".repeat(41))).isEqualTo("가".repeat(40) + "…");
    assertThat(CallContext.cut("가".repeat(40))).isEqualTo("가".repeat(40));
  }
}
