package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.ai.DayPlanRule.SubjectInfo;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// AI 하루 계획의 숫자 규칙(DayPlanRule)을 확인합니다. 집중을 오래 못 하면 짧게 자주, 근거 문장과 함께.
class DayPlanRuleTest {

  // ---- 한 칸의 길이 ----

  @Test
  @DisplayName("공부 기록이 5번 이상이면 한 번 길이의 가운데값(5분 단위)으로 나눈다 — 집중이 짧으면 짧게")
  void blockFromSessions() {
    DayPlanRule.Choice choice = DayPlanRule.blockMinutesOf(List.of(20, 25, 22, 90, 18), 120, false);

    assertThat(choice.minutes()).isEqualTo(20);
    assertThat(choice.evidence()).isEqualTo("최근 14일 공부 기록 5번의 한 번 길이 가운데값이 22분이라 20분씩 나눴어요");
  }

  @Test
  @DisplayName("기록이 적으면 설문의 집중 시간, 그것도 없으면 주의 분산이 높을 때 30분, 아니면 50분. 15 ~ 90분 안으로")
  void blockFallbacks() {
    assertThat(DayPlanRule.blockMinutesOf(List.of(30, 40), 50, true).minutes()).isEqualTo(50);
    assertThat(DayPlanRule.blockMinutesOf(List.of(), 120, false).minutes()).isEqualTo(90);
    assertThat(DayPlanRule.blockMinutesOf(List.of(), null, true))
        .isEqualTo(new DayPlanRule.Choice(30, "심리검사에서 공부 중 흔들리는 편이라고 나와서 30분씩 짧게 나눴어요"));
    assertThat(DayPlanRule.blockMinutesOf(List.of(), null, false).minutes()).isEqualTo(50);
    assertThat(DayPlanRule.blockMinutesOf(List.of(3, 5, 4, 6, 5), null, false).minutes())
        .isEqualTo(15);
  }

  @Test
  @DisplayName("가운데값: 홀수 개는 가운데, 짝수 개는 가운데 둘의 평균(반올림)")
  void median() {
    assertThat(DayPlanRule.medianOf(List.of(5, 1, 3))).isEqualTo(3);
    assertThat(DayPlanRule.medianOf(List.of(10, 20, 31, 41))).isEqualTo(26);
  }

  // ---- 하루 총량 ----

  @Test
  @DisplayName("공부한 날이 3일 이상이면 평균의 1.1배(10분 단위), 그날 이미 세운 계획 시간은 뺀다")
  void totalFromDays() {
    DayPlanRule.Total total =
        DayPlanRule.totalMinutesOf(List.of(100, 120, 140), 300, 80, false, 30);

    // 평균 120 × 1.1 = 132 → 130, 이미 30분 → 100
    assertThat(total.minutes()).isEqualTo(100);
    assertThat(total.evidence())
        .containsExactly("최근 14일 공부한 3일의 평균이 120분이라 조금 더 잡았어요", "그날 이미 세운 계획 30분을 뺐어요");
  }

  @Test
  @DisplayName("완료율이 60% 미만이거나 계획 낙관이 높으면 0.8배로 줄인다 (둘 다면 한 번만)")
  void shrink() {
    DayPlanRule.Total lowRate = DayPlanRule.totalMinutesOf(List.of(), 200, 50, true, 0);
    assertThat(lowRate.minutes()).isEqualTo(160);
    assertThat(lowRate.evidence()).contains("최근 계획의 50%만 끝내서 양을 줄였어요").hasSize(2);

    DayPlanRule.Total optimistic = DayPlanRule.totalMinutesOf(List.of(), null, null, true, 0);
    assertThat(optimistic.minutes()).isEqualTo(100);
    assertThat(optimistic.evidence())
        .containsExactly("아직 기록이 적어서 하루 120분으로 잡았어요", "심리검사에서 계획을 크게 잡는 편이라고 나와서 양을 줄였어요");
  }

  @Test
  @DisplayName("30 ~ 600분 안으로 맞추고, 이미 세운 계획이 더 많으면 0 이다")
  void totalBounds() {
    assertThat(DayPlanRule.totalMinutesOf(List.of(5, 5, 5), null, null, false, 0).minutes())
        .isEqualTo(30);
    assertThat(DayPlanRule.totalMinutesOf(List.of(), 900, null, false, 0).minutes()).isEqualTo(600);
    assertThat(DayPlanRule.totalMinutesOf(List.of(), 120, null, false, 200).minutes()).isZero();
  }

  // ---- 칸 수와 과목 ----

  @Test
  @DisplayName("칸은 한 칸 길이로 채우고, 남는 시간이 15분 이상이면 짧은 칸 하나를 더한다 (많아도 12칸)")
  void blocks() {
    assertThat(DayPlanRule.blocksOf(130, 25)).containsExactly(25, 25, 25, 25, 25);
    assertThat(DayPlanRule.blocksOf(140, 25)).containsExactly(25, 25, 25, 25, 25, 15);
    // 총량이 한 칸보다 짧으면 짧은 칸 하나 (로컬에서 찾은 경우: 총량 30분 · 한 칸 50분)
    assertThat(DayPlanRule.blocksOf(30, 50)).containsExactly(30);
    assertThat(DayPlanRule.blocksOf(10, 50)).isEmpty();
    assertThat(DayPlanRule.blocksOf(0, 50)).isEmpty();
    assertThat(DayPlanRule.blocksOf(600, 15)).hasSize(12);
  }

  @Test
  @DisplayName("시험이 가까운 과목 먼저, 그다음 덜 한 과목. 시험 과목은 한 바퀴에 두 번 들어간다")
  void subjects() {
    SubjectInfo korean = new SubjectInfo(1L, "국어", null, 300);
    SubjectInfo math = new SubjectInfo(2L, "수학", 5, 200);
    SubjectInfo english = new SubjectInfo(3L, "영어", null, 40);
    List<SubjectInfo> ordered = DayPlanRule.subjectOrder(List.of(korean, math, english));

    assertThat(ordered).containsExactly(math, english, korean);
    assertThat(DayPlanRule.subjectRotation(ordered, 5))
        .containsExactly(math, math, english, korean, math);
    assertThat(DayPlanRule.subjectEvidence(ordered))
        .containsExactly("5일 뒤 시험이 있는 수학을(를) 먼저 · 더 많이 넣었어요", "최근 14일 가장 덜 한 영어(40분)을(를) 넣었어요");
  }

  @Test
  @DisplayName("과목이 하나면 덜 한 과목 문장은 없다")
  void singleSubject() {
    SubjectInfo only = new SubjectInfo(1L, "수학", null, 0);
    assertThat(DayPlanRule.subjectEvidence(List.of(only))).isEmpty();
  }
}
