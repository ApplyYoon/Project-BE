package com.ithopeschool.projectbe.domain.thinkingtest;

import static com.ithopeschool.projectbe.domain.thinkingtest.ThinkingGap.Verdict.BIGGER;
import static com.ithopeschool.projectbe.domain.thinkingtest.ThinkingGap.Verdict.MATCHED;
import static com.ithopeschool.projectbe.domain.thinkingtest.ThinkingGap.Verdict.SMALLER;
import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.bias.Weakness;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactValue;
import com.ithopeschool.projectbe.domain.thinkingtest.ExamThinkingGap.ReasonFact;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 시험 전 2주의 "내 생각 vs 현실" 규칙(ExamThinkingGap 주석의 표)을 확인합니다.
class ExamThinkingGapTest {

  // 시험 10월 20일 → 기간은 10월 6일 ~ 10월 19일
  private static final LocalDate EXAM = LocalDate.of(2026, 10, 20);

  // "계획할 때" 축 결과. 평균 5.0 이면 낙관, 4.0 이면 균형
  private static final StudyStyle.AxisResult OPTIMISTIC =
      new StudyStyle.AxisResult(StudyStyle.AXES.get(0), 5.0, "낙관");
  private static final StudyStyle.AxisResult BALANCED =
      new StudyStyle.AxisResult(StudyStyle.AXES.get(0), 4.0, "균형");

  // 계획 10개, 계획 대비 실제 90% (계획 쪽은 판정이 없거나 SMALLER)
  private static final FactValue PLAN_90 = new FactValue(90, 10);

  private static ReasonFact fact(int day, MissReason reason) {
    return new ReasonFact(LocalDate.of(2026, 10, day), reason);
  }

  @Test
  @DisplayName("기간은 시험 14일 전 ~ 하루 전이고, 그 밖의 날(시험 날 포함)은 세지 않는다")
  void onlyTheTwoWeeksBeforeTheExam() {
    ExamThinkingGap gap =
        ExamThinkingGap.of(
            EXAM,
            Set.of(),
            BALANCED,
            List.of(
                fact(5, MissReason.TOO_MUCH),
                fact(6, MissReason.TOO_MUCH),
                fact(19, MissReason.TOO_MUCH),
                fact(20, MissReason.TOO_MUCH)),
            PLAN_90);

    assertThat(gap.fromDate()).isEqualTo(LocalDate.of(2026, 10, 6));
    assertThat(gap.toDate()).isEqualTo(LocalDate.of(2026, 10, 19));
    // 6일 · 19일만 들어갑니다
    assertThat(gap.reasonDays()).isEqualTo(2);
    assertThat(gap.reasons())
        .extracting(r -> r.reason() + ":" + r.actualCount() + ":" + r.verdict())
        .containsExactly("TOO_MUCH:2:BIGGER");
  }

  @Test
  @DisplayName("같은 날 같은 이유는 하루로 센다 (타이머와 통화에서 둘 다 '폰' 이어도 1일)")
  void sameDayCountsOnce() {
    ExamThinkingGap gap =
        ExamThinkingGap.of(
            EXAM,
            Set.of(),
            BALANCED,
            List.of(
                fact(10, MissReason.PHONE),
                fact(10, MissReason.PHONE),
                fact(10, MissReason.TIRED),
                fact(11, MissReason.PHONE)),
            PLAN_90);

    // 이유가 나온 날은 10일 · 11일 이틀
    assertThat(gap.reasonDays()).isEqualTo(2);
    assertThat(gap.enoughData()).isFalse();
    // 폰 2일 → 컸어 / 피곤 1일 → 꼽지 않았고 1일뿐이라 판정 없음
    assertThat(gap.reasons())
        .extracting(r -> r.reason() + ":" + r.actualCount())
        .containsExactly("PHONE:2");
  }

  @Test
  @DisplayName("약점 칩 판정: 꼽았고 나옴 → 맞았어 / 꼽았는데 안 나옴 → 작았어. 아픔·기타는 날 수에도 안 넣는다")
  void weaknessVerdicts() {
    ExamThinkingGap gap =
        ExamThinkingGap.of(
            EXAM,
            Set.of(Weakness.SLEEP, Weakness.GAME, Weakness.FOCUS),
            BALANCED,
            List.of(
                fact(7, MissReason.TIRED),
                fact(8, MissReason.TIRED),
                fact(9, MissReason.SICK),
                fact(12, MissReason.ETC),
                fact(13, MissReason.UNWILLING)),
            PLAN_90);

    // 견주는 이유가 나온 날: 7 · 8 · 13 (아픔 9일, 기타 12일은 빼고)
    assertThat(gap.reasonDays()).isEqualTo(3);
    assertThat(gap.enoughData()).isTrue();
    assertThat(gap.reasons())
        .extracting(r -> r.reason() + ":" + r.verdict())
        .containsExactly("PHONE:SMALLER", "TIRED:MATCHED");
    assertThat(gap.unverifiable()).containsExactly(Weakness.FOCUS);
  }

  @Test
  @DisplayName("계획 쪽 판정: 낙관+80% 미만 → 맞았어 / 낙관+80% 이상 → 작았어 / 낙관 아님+80% 미만 → 컸어 / 그 밖 → 없음")
  void planVerdicts() {
    assertThat(ExamThinkingGap.planGapOf(OPTIMISTIC, new FactValue(79, 5)).verdict())
        .isEqualTo(MATCHED);
    assertThat(ExamThinkingGap.planGapOf(OPTIMISTIC, new FactValue(80, 5)).verdict())
        .isEqualTo(SMALLER);
    assertThat(ExamThinkingGap.planGapOf(BALANCED, new FactValue(58, 9)).verdict())
        .isEqualTo(BIGGER);
    assertThat(ExamThinkingGap.planGapOf(BALANCED, new FactValue(120, 9)).verdict()).isNull();
  }

  @Test
  @DisplayName("공부 계획이 5개보다 적거나 계산할 수 없으면 계획 쪽은 견주지 않는다")
  void tooFewPlans() {
    ExamThinkingGap.PlanGap fourPlans = ExamThinkingGap.planGapOf(OPTIMISTIC, new FactValue(40, 4));
    assertThat(fourPlans.planRate()).isNull();
    assertThat(fourPlans.planCount()).isEqualTo(4);
    assertThat(fourPlans.verdict()).isNull();

    ExamThinkingGap.PlanGap noPlans = ExamThinkingGap.planGapOf(OPTIMISTIC, FactValue.UNKNOWN);
    assertThat(noPlans.planRate()).isNull();
    assertThat(noPlans.planCount()).isZero();
  }

  @Test
  @DisplayName("문장: 모자란 자료 안내가 먼저, 그다음 컸어 → 작았어 → 맞았어 순서")
  void sentences() {
    ExamThinkingGap gap =
        ExamThinkingGap.of(
            EXAM,
            Set.of(Weakness.GAME),
            OPTIMISTIC,
            List.of(fact(7, MissReason.TOO_MUCH), fact(8, MissReason.TOO_MUCH)),
            new FactValue(58, 9));

    assertThat(gap.sentences())
        .containsExactly(
            "시험 전 2주에 그만두거나 못 채운 이유가 남은 날이 2일뿐이라, 이유 쪽은 더 지켜봐야 해.",
            "꼽지 않았던 '계획 과다' 쪽이 시험 전 2주 중 2일, 그만두거나 못 채운 이유로 나왔어. 생각보다 큰 원인일 수 있어.",
            "내가 꼽은 약점 '게임' 쪽은 시험 전 2주 동안 그만두거나 못 채운 이유로 나오지 않았어.",
            "테스트에서 계획할 때 '낙관' 쪽이었고, 시험 전 2주도 계획한 시간의 58%를 했어. 생각이 맞았어.");
  }

  @Test
  @DisplayName("문장: 계획이 없던 2주 · 다를 게 없던 2주")
  void sentencesWhenNothingStandsOut() {
    ExamThinkingGap noPlans =
        ExamThinkingGap.of(
            EXAM,
            Set.of(),
            BALANCED,
            List.of(
                fact(7, MissReason.TIRED), fact(8, MissReason.EVENT), fact(9, MissReason.PHONE)),
            FactValue.UNKNOWN);
    // 이유 쪽은 견줄 만큼 있었고 다른 점이 없었습니다
    assertThat(noPlans.sentences())
        .containsExactly(
            "시험 전 2주에는 공부 계획이 없어서 계획 쪽은 견주지 않았어.", "시험 전 2주의 기록은 내가 생각한 나와 크게 다르지 않았어.");

    ExamThinkingGap same =
        ExamThinkingGap.of(
            EXAM,
            Set.of(),
            BALANCED,
            List.of(
                fact(7, MissReason.TIRED), fact(8, MissReason.EVENT), fact(9, MissReason.PHONE)),
            PLAN_90);
    assertThat(same.sentences()).containsExactly("시험 전 2주의 기록은 내가 생각한 나와 크게 다르지 않았어.");
  }
}
