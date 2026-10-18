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
  @DisplayName("저녁형이라고 답했는데 아침 카드가 20%p 이상 더 잘 됐으면, 몇 번 중 몇 번인지 적고 스스로 알아차리게 물으라고 한다")
  void rhythmAgainstWhatTheySaid() {
    List<String> lines = CallContext.rhythmLines(new CallContext.RhythmGap(true, 9, 10, 6, 12));

    assertThat(lines.get(0))
        .isEqualTo(
            "- 가입 때 스스로 '저녁형' 이라고 답했어. 그런데 최근 2주 기록에서 체크리스트까지 다 채운 날은 아침 카드 10번 중 9번(90%), 저녁 카드 12번 중 6번(50%)이야.");
    assertThat(lines.get(1)).contains("단정하지 말고", "아침이 더 잘 되는 것 같은데", "저녁에 하던 카드 하나를 아침으로");
  }

  @Test
  @DisplayName("아침형이라고 답했는데 저녁이 더 잘 됐어도 같은 방식으로 적는다")
  void morningSaidButNightBetter() {
    assertThat(CallContext.rhythmLines(new CallContext.RhythmGap(false, 2, 5, 4, 5)).get(1))
        .contains("저녁이 더 잘 되는 것 같은데", "아침에 하던 카드 하나를 저녁으로");
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

  // ---- 최근 기록 (규칙 7 ~ 10) ----

  private static Facts recentOnly(CallContext.Recent recent) {
    return new Facts(null, null, List.of(), null, List.of(), recent);
  }

  @Test
  @DisplayName("최근 기록은 시험 · 목표보다 앞에, 약속 · 수면 · 감정 · 실행률 순서로 적는다")
  void recentFirst() {
    CallContext.Recent recent =
        new CallContext.Recent(
            new CallContext.Promise("폰을 보고 싶으면", "물 한 잔 마신다", 1),
            List.of(new CallContext.EmotionCount("피곤함", 4)),
            new CallContext.RunRate(8, 0.5, 16, 0.8),
            new CallContext.Sleep(310, 0));
    String section =
        CallContext.section(
            new Facts(new NextExam("11월 학평", 33), null, List.of(), null, List.of(), recent));

    assertThat(section)
        .containsSubsequence(
            "- 어제 체크인에서 스스로 정한 약속: 「폰을 보고 싶으면」 → 「물 한 잔 마신다」",
            "- 오늘 날짜의 수면 기록: 5시간 10분 (휴대폰 기록)",
            "- 최근 7일 체크인에서 고른 감정: 피곤함 4번",
            "- 카드 실행률: 최근 7일 50% (카드 8번), 그 앞 2주 80% (카드 16번)",
            "- 다음 시험: 11월 학평 (D-33)");
  }

  @Test
  @DisplayName("약속: 오늘 것이면 떠올리게, 어제 것이면 해 봤는지 묻게 한다")
  void promiseGuide() {
    assertThat(CallContext.promiseLines(new CallContext.Promise("졸리면", "스트레칭한다", 0)))
        .containsExactly(
            "- 오늘 체크인에서 스스로 정한 약속: 「졸리면」 → 「스트레칭한다」", "  - 그 상황이 오면 이 약속을 떠올리게 해 줘. 약속을 대신 바꾸지 마.");
    assertThat(CallContext.promiseLines(new CallContext.Promise("졸리면", "스트레칭한다", 1)).get(1))
        .contains("해 봤는지 가볍게 물어봐", "탓하지 말고");
    assertThat(CallContext.promiseLines(null)).isEmpty();
  }

  @Test
  @DisplayName("수면: 6시간보다 적을 때만 적고, 딱 맞는 시간이면 '분' 을 뺀다")
  void sleep() {
    assertThat(CallContext.sleepLines(new CallContext.Sleep(300, 1)).get(0))
        .isEqualTo("- 어제 날짜의 수면 기록: 5시간 (휴대폰 기록)");
    assertThat(CallContext.sleepLines(new CallContext.Sleep(360, 0))).isEmpty();
    assertThat(CallContext.sleepLines(null)).isEmpty();
  }

  @Test
  @DisplayName("감정: 2번 이상 고른 것만, 많은 것부터 3개까지")
  void emotions() {
    List<CallContext.EmotionCount> emotions =
        List.of(
            new CallContext.EmotionCount("피곤함", 4),
            new CallContext.EmotionCount("불안함", 3),
            new CallContext.EmotionCount("막막함", 2),
            new CallContext.EmotionCount("짜증남", 2),
            new CallContext.EmotionCount("평온함", 1));

    assertThat(CallContext.emotionLines(emotions).get(0))
        .isEqualTo("- 최근 7일 체크인에서 고른 감정: 피곤함 4번, 불안함 3번, 막막함 2번");
    assertThat(CallContext.emotionLines(List.of(new CallContext.EmotionCount("평온함", 1)))).isEmpty();
  }

  @Test
  @DisplayName("실행률: 두 기간 모두 카드가 3번 이상이고 15%p 이상 차이 날 때만, 오르면 칭찬 · 내리면 탓하지 않고 묻는다")
  void runRate() {
    assertThat(CallContext.runRateLines(new CallContext.RunRate(8, 0.5, 16, 0.8)).get(1))
        .contains("탓하지 말고", "무엇이 달라졌는지");
    assertThat(CallContext.runRateLines(new CallContext.RunRate(8, 0.9, 16, 0.6)).get(1))
        .contains("칭찬해", "다른 사람과 비교하지 마");
    // 차이가 작음 (80% vs 70%)
    assertThat(CallContext.runRateLines(new CallContext.RunRate(8, 0.8, 16, 0.7))).isEmpty();
    // 카드가 적음
    assertThat(CallContext.runRateLines(new CallContext.RunRate(2, 0.0, 16, 0.8))).isEmpty();
  }

  @Test
  @DisplayName("최근 기록이 없으면(Recent.NONE) 아무 줄도 더하지 않는다")
  void noRecent() {
    assertThat(CallContext.section(recentOnly(CallContext.Recent.NONE))).isEmpty();
  }

  // ---- 이번 주 목표 · 자기 실험 (규칙 11 · 12) ----

  @Test
  @DisplayName("이번 주 목표 · 진행 중 실험은 최근 기록 뒤, 시험 앞에 적는다")
  void ongoingOrder() {
    CallContext.Recent recent =
        new CallContext.Recent(null, List.of(new CallContext.EmotionCount("피곤함", 2)), null, null);
    CallContext.Ongoing ongoing =
        new CallContext.Ongoing(
            new CallContext.WeekGoalLine("완주율 65% 넘기기", 50, "%"),
            List.of(new CallContext.ExperimentLine("시작할 때 폰을 가방에 넣기", List.of("수학 문제집"), 5)));
    String section =
        CallContext.section(
            new Facts(
                new NextExam("11월 학평", 33), null, List.of(), null, List.of(), recent, ongoing));

    assertThat(section)
        .containsSubsequence(
            "- 최근 7일 체크인에서 고른 감정: 피곤함 2번",
            "- 이번 주 목표: 「완주율 65% 넘기기」 (지금 50%)",
            "- 진행 중인 자기 실험 (14일 중 5일째): 「시작할 때 폰을 가방에 넣기」 · 붙인 카드 「수학 문제집」",
            "캐묻지 마",
            "- 다음 시험: 11월 학평 (D-33)");
  }

  @Test
  @DisplayName("이번 주 목표: 아직 숫자가 없으면 '아직 기록 없음', 없으면 아무 줄도 없다")
  void weekGoalLines() {
    assertThat(
            CallContext.weekGoalLines(new CallContext.WeekGoalLine("시작 지연 18분 안으로", null, "분"))
                .get(0))
        .isEqualTo("- 이번 주 목표: 「시작 지연 18분 안으로」 (아직 기록 없음)");
    assertThat(CallContext.weekGoalLines(null)).isEmpty();
  }

  @Test
  @DisplayName("실험: 카드 제목은 3장까지, 카드가 없으면 규칙만, 실험이 없으면 아무 줄도 없다")
  void experimentLines() {
    List<String> lines =
        CallContext.experimentLines(
            List.of(
                new CallContext.ExperimentLine("체크리스트를 하나 줄이기", List.of("a", "b", "c", "d"), 2),
                new CallContext.ExperimentLine("끝나면 한 일을 한 줄로 남기기", List.of(), 14)));

    assertThat(lines.get(0)).endsWith("붙인 카드 「a」, 「b」, 「c」");
    assertThat(lines.get(1)).isEqualTo("- 진행 중인 자기 실험 (14일 중 14일째): 「끝나면 한 일을 한 줄로 남기기」");
    assertThat(lines).hasSize(3);
    assertThat(CallContext.experimentLines(List.of())).isEmpty();
  }
}
