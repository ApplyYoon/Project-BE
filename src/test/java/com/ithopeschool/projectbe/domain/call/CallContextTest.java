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

  @Test
  @DisplayName("밤형이라고 답했는데 아침 카드가 20%p 이상 더 잘 됐으면, 몇 번 중 몇 번인지 적고 스스로 알아차리게 물으라고 한다")
  void rhythmAgainstWhatTheySaid() {
    List<String> lines = CallContext.rhythmLines(new CallContext.RhythmGap(true, 9, 10, 6, 12));

    assertThat(lines.get(0))
        .isEqualTo(
            "- 가입 때 스스로 '밤형' 이라고 답했어. 그런데 최근 2주 기록에서 체크리스트까지 다 채운 날은 아침 카드 10번 중 9번(90%), 밤 카드 12번 중 6번(50%)이야.");
    assertThat(lines.get(1)).contains("단정하지 말고", "아침이 더 잘 되는 것 같은데", "밤에 하던 카드 하나를 아침으로");
  }

  @Test
  @DisplayName("아침형이라고 답했는데 밤이 더 잘 됐어도 같은 방식으로 적는다")
  void morningSaidButNightBetter() {
    assertThat(CallContext.rhythmLines(new CallContext.RhythmGap(false, 2, 5, 4, 5)).get(1))
        .contains("밤이 더 잘 되는 것 같은데", "아침에 하던 카드 하나를 밤으로");
  }

  @Test
  @DisplayName("답한 쪽이 더 잘 됐거나, 차이가 20%p 보다 작거나, 한쪽 카드가 4번보다 적으면 넣지 않는다")
  void noRhythmLines() {
    assertThat(CallContext.rhythmLines(new CallContext.RhythmGap(true, 2, 5, 5, 5))).isEmpty();
    assertThat(CallContext.rhythmLines(new CallContext.RhythmGap(true, 7, 10, 6, 10))).isEmpty();
    assertThat(CallContext.rhythmLines(new CallContext.RhythmGap(true, 3, 3, 1, 10))).isEmpty();
    assertThat(CallContext.rhythmLines(null)).isEmpty();
  }

  @Test
  @DisplayName("못 채운 이유는 2번 이상 나온 것만, 앞에서부터 2개까지 적는다")
  void causeLines() {
    List<String> lines =
        CallContext.causeLines(
            List.of(
                new CallContext.CauseCount("피곤·수면", 6),
                new CallContext.CauseCount("계획 과다", 3),
                new CallContext.CauseCount("딴생각", 2),
                new CallContext.CauseCount("막힘", 1)));

    assertThat(lines.get(0)).isEqualTo("- 최근 2주 카드를 못 채웠을 때 고른 이유: 피곤·수면 6번, 계획 과다 3번");
    assertThat(lines.get(1)).contains("탓하지 말고");
    assertThat(CallContext.causeLines(List.of(new CallContext.CauseCount("막힘", 1)))).isEmpty();
  }
}
