package com.ithopeschool.projectbe.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.plan.PlanShortcuts.FrequentPlan;
import com.ithopeschool.projectbe.domain.plan.PlanShortcuts.PlanItem;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

// 계획을 한 번 눌러서 넣는 계산(어제 계획 가져오기 · 자주 쓰는 계획)을 확인합니다.
// feExample 로 시작하는 테스트는 FE 함수(src/lib/planShortcuts.ts) 주석의 예시를 그대로 옮긴 것입니다.
// 나머지 기대값도 FE 함수를 직접 돌려서 나온 값과 같습니다.
class PlanShortcutsTest {

  // 과목 번호
  private static final Long MATH = 1L;
  private static final Long ENGLISH = 2L;
  private static final Long KOREAN = 3L;
  private static final Long SCIENCE = 4L;
  private static final Long HISTORY = 5L;
  private static final Long ART = 6L;

  // 지금 남아 있는 과목들
  private static final Set<Long> SUBJECTS = Set.of(MATH, ENGLISH, KOREAN, SCIENCE, HISTORY, ART);

  // 오늘은 2026-10-10 입니다. 자주 쓰는 계획은 9/27 ~ 10/10 의 계획을 셉니다.
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

  private static LocalDate october(int day) {
    return LocalDate.of(2026, 10, day);
  }

  private static LocalDate september(int day) {
    return LocalDate.of(2026, 9, day);
  }

  // 시작 시각을 정하지 않은 공부 계획
  private static PlanItem study(LocalDate date, Long subjectId, String content, int minutes) {
    return new PlanItem(date, subjectId, content, minutes, null, PlanKind.STUDY);
  }

  // 과목이 없는 노는 계획
  private static PlanItem play(LocalDate date, String content, int minutes, LocalTime startTime) {
    return new PlanItem(date, null, content, minutes, startTime, PlanKind.PLAY);
  }

  // 자주 쓰는 계획의 할 일 내용만 꺼냅니다. (순서를 확인할 때 씁니다)
  private static List<String> contentsOf(List<FrequentPlan> plans) {
    return plans.stream().map(FrequentPlan::content).toList();
  }

  // ─── 가져오기 ───

  @Test
  void feExampleCopiesPlansExceptDeletedSubjects() {
    // FE 예) 어제 계획 [수학 60분, 영어 30분, 한국사 40분(과목이 지워짐)] → 10/10 의 수학 60분, 영어 30분
    List<PlanItem> yesterday =
        List.of(
            study(october(9), MATH, "문제집", 60),
            study(october(9), ENGLISH, "단어", 30),
            study(october(9), HISTORY, "한국사", 40));

    assertThat(PlanShortcuts.copy(yesterday, october(10), Set.of(MATH, ENGLISH)))
        .containsExactly(
            study(october(10), MATH, "문제집", 60), study(october(10), ENGLISH, "단어", 30));
  }

  @Test
  void copyKeepsStartTimeAndOrder() {
    // 시작 시각을 정해 둔 계획은 그 시각도 같이 가져오고, 순서는 받은 순서 그대로입니다.
    List<PlanItem> yesterday =
        List.of(
            study(october(9), MATH, "문제집", 60),
            new PlanItem(october(9), ENGLISH, "단어", 30, LocalTime.of(19, 0), PlanKind.STUDY),
            study(october(9), MATH, "오답", 25));

    assertThat(PlanShortcuts.copy(yesterday, october(10), SUBJECTS))
        .containsExactly(
            study(october(10), MATH, "문제집", 60),
            new PlanItem(october(10), ENGLISH, "단어", 30, LocalTime.of(19, 0), PlanKind.STUDY),
            study(october(10), MATH, "오답", 25));
  }

  @Test
  void copyTakesPlayPlansWithoutCheckingTheSubject() {
    // 노는 계획은 과목이 없어서, 남아 있는 과목이 하나도 없어도 가져옵니다.
    List<PlanItem> yesterday =
        List.of(
            study(october(9), MATH, "문제집", 60), play(october(9), "게임", 20, LocalTime.of(21, 0)));

    assertThat(PlanShortcuts.copy(yesterday, october(10), Set.of()))
        .containsExactly(play(october(10), "게임", 20, LocalTime.of(21, 0)));
  }

  @Test
  void copyTreatsPlanWithoutKindAsStudy() {
    // 종류가 비어 있는 계획은 공부 계획으로 가져옵니다. (FE 도 kind 가 없으면 공부로 봅니다)
    List<PlanItem> yesterday = List.of(new PlanItem(october(9), MATH, "문제집", 60, null, null));

    assertThat(PlanShortcuts.copy(yesterday, october(10), SUBJECTS))
        .containsExactly(study(october(10), MATH, "문제집", 60));
  }

  @Test
  void copyOfNothingIsEmpty() {
    // 가져올 계획이 없거나, 과목이 모두 지워졌으면 빈 목록입니다.
    assertThat(PlanShortcuts.copy(List.of(), october(10), SUBJECTS)).isEmpty();
    assertThat(
            PlanShortcuts.copy(List.of(study(october(9), MATH, "문제집", 60)), october(10), Set.of()))
        .isEmpty();
  }

  // ─── 자주 쓰는 계획 ───

  @Test
  void feExampleCountsTheSameCombination() {
    // FE 예) 10/8 수학 문제집 60분, 10/9 수학 문제집 60분, 10/9 영어 단어 30분, 9/20 국어 비문학 50분(14일보다 전)
    //   → [수학 문제집 60분 2번, 영어 단어 30분 1번]
    List<PlanItem> plans =
        List.of(
            study(october(8), MATH, "문제집", 60),
            study(october(9), MATH, "문제집", 60),
            study(october(9), ENGLISH, "단어", 30),
            study(september(20), KOREAN, "비문학", 50));

    assertThat(PlanShortcuts.frequent(plans, TODAY, SUBJECTS))
        .containsExactly(
            new FrequentPlan(MATH, "문제집", 60, 2), new FrequentPlan(ENGLISH, "단어", 30, 1));
  }

  @Test
  void differentMinutesIsADifferentCombination() {
    // "단어 50개 30분" 과 "단어 50개 40분" 은 다른 조합입니다.
    List<PlanItem> plans =
        List.of(
            study(october(8), ENGLISH, "단어 50개", 30),
            study(october(9), ENGLISH, "단어 50개", 40),
            study(october(10), ENGLISH, "단어 50개", 30));

    assertThat(PlanShortcuts.frequent(plans, TODAY, SUBJECTS))
        .containsExactly(
            new FrequentPlan(ENGLISH, "단어 50개", 30, 2), new FrequentPlan(ENGLISH, "단어 50개", 40, 1));
  }

  @Test
  void countsFromThirteenDaysAgo() {
    // 오늘이 10/10 이면 9/27(13일 전)부터 셉니다. 9/26 은 세지 않습니다.
    List<PlanItem> plans =
        List.of(study(september(27), MATH, "a", 60), study(september(26), ENGLISH, "b", 30));

    assertThat(PlanShortcuts.frequent(plans, TODAY, SUBJECTS))
        .containsExactly(new FrequentPlan(MATH, "a", 60, 1));
  }

  @Test
  void countsTodayButNotFuturePlans() {
    // 오늘 계획은 세고, 내일 계획은 세지 않습니다.
    List<PlanItem> plans =
        List.of(study(october(10), MATH, "a", 60), study(october(11), ENGLISH, "b", 30));

    assertThat(PlanShortcuts.frequent(plans, TODAY, SUBJECTS))
        .containsExactly(new FrequentPlan(MATH, "a", 60, 1));
  }

  @Test
  void periodCrossesTheMonth() {
    // 오늘이 10/05 이면 9/22 부터입니다. 9/21 은 세지 않습니다.
    List<PlanItem> plans =
        List.of(study(september(22), MATH, "a", 60), study(september(21), ENGLISH, "b", 30));

    assertThat(PlanShortcuts.frequent(plans, october(5), SUBJECTS))
        .containsExactly(new FrequentPlan(MATH, "a", 60, 1));
  }

  @Test
  void sameCountPutsTheMoreRecentOneFirst() {
    // 모두 한 번씩 썼으면 더 최근에 쓴 조합이 앞에 옵니다. (10/09 → 10/07 → 10/05)
    List<PlanItem> plans =
        List.of(
            study(october(5), MATH, "a", 60),
            study(october(9), ENGLISH, "b", 30),
            study(october(7), KOREAN, "c", 50));

    assertThat(contentsOf(PlanShortcuts.frequent(plans, TODAY, SUBJECTS)))
        .containsExactly("b", "c", "a");
  }

  @Test
  void lastDateIsTheLatestDateEvenIfItComesFirst() {
    // 마지막으로 쓴 날짜는 목록의 순서가 아니라 날짜가 가장 늦은 것입니다. (a 는 10/09, b 는 10/08)
    List<PlanItem> plans =
        List.of(
            study(october(9), MATH, "a", 60),
            study(october(3), MATH, "a", 60),
            study(october(8), ENGLISH, "b", 30),
            study(october(4), ENGLISH, "b", 30));

    assertThat(PlanShortcuts.frequent(plans, TODAY, SUBJECTS))
        .containsExactly(new FrequentPlan(MATH, "a", 60, 2), new FrequentPlan(ENGLISH, "b", 30, 2));
  }

  @Test
  void sameCountAndSameDatePutsTheLaterOneFirst() {
    // 횟수도 마지막 날짜도 같으면 목록에서 뒤에 나온 조합이 앞에 옵니다. (FE 가 실제로 돌려주는 순서와 같습니다)
    List<PlanItem> plans =
        List.of(
            study(october(9), MATH, "a", 60),
            study(october(9), ENGLISH, "b", 30),
            study(october(9), KOREAN, "c", 50));

    assertThat(contentsOf(PlanShortcuts.frequent(plans, TODAY, SUBJECTS)))
        .containsExactly("c", "b", "a");
  }

  @Test
  void deletedSubjectIsNotCounted() {
    // 지워진 과목(99번)의 계획은 뺍니다.
    List<PlanItem> plans =
        List.of(study(october(9), 99L, "a", 60), study(october(9), MATH, "b", 30));

    assertThat(PlanShortcuts.frequent(plans, TODAY, SUBJECTS))
        .containsExactly(new FrequentPlan(MATH, "b", 30, 1));
  }

  @Test
  void playPlanIsNotCounted() {
    // 노는 계획은 과목이 없어서, 여러 번 썼어도 자주 쓰는 계획에 넣지 않습니다.
    List<PlanItem> plans =
        List.of(
            play(october(9), "게임", 20, null),
            play(october(9), "게임", 20, null),
            study(october(9), MATH, "b", 30));

    assertThat(PlanShortcuts.frequent(plans, TODAY, SUBJECTS))
        .containsExactly(new FrequentPlan(MATH, "b", 30, 1));
  }

  @Test
  void returnsAtMostFive() {
    // 조합이 6개면 5개까지만 돌려줍니다. 가장 오래전에 쓴 a 가 빠집니다.
    List<PlanItem> plans =
        List.of(
            study(october(4), MATH, "a", 10),
            study(october(5), ENGLISH, "b", 20),
            study(october(6), KOREAN, "c", 30),
            study(october(7), SCIENCE, "d", 40),
            study(october(8), HISTORY, "e", 50),
            study(october(9), ART, "f", 60));

    assertThat(contentsOf(PlanShortcuts.frequent(plans, TODAY, SUBJECTS)))
        .containsExactly("f", "e", "d", "c", "b");
  }

  @Test
  void noPlansIsEmpty() {
    assertThat(PlanShortcuts.frequent(List.of(), TODAY, SUBJECTS)).isEmpty();
  }
}
