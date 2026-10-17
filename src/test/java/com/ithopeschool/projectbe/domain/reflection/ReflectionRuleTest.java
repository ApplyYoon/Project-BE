package com.ithopeschool.projectbe.domain.reflection;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.reflection.ReflectionRule.Decision;
import com.ithopeschool.projectbe.domain.reflection.ReflectionRule.ReasonFact;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 성찰일기를 처방할지 정하는 규칙 테스트입니다. 규칙 표는 ReflectionRule 맨 위에 있습니다.
class ReflectionRuleTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

  // daysAgo 일 전에 reason 이 나왔다는 기록 하나
  private static ReasonFact fact(int daysAgo, MissReason reason) {
    return new ReasonFact(TODAY.minusDays(daysAgo), reason);
  }

  // 0, 1, 2 ... 일 전에 reason 이 하루씩 나왔다는 기록 count 개
  private static List<ReasonFact> days(MissReason reason, int count) {
    List<ReasonFact> facts = new ArrayList<>();
    for (int daysAgo = 0; daysAgo < count; daysAgo++) {
      facts.add(fact(daysAgo, reason));
    }
    return facts;
  }

  @Test
  @DisplayName("최근 7일 중 같은 이유가 3일이면 그 이유로 처방한다")
  void prescribesWhenSameReasonOnThreeDays() {
    Decision decision = ReflectionRule.decide(TODAY, days(MissReason.UNWILLING, 3), null);

    assertThat(decision).isEqualTo(new Decision(MissReason.UNWILLING, 3));
  }

  @Test
  @DisplayName("같은 이유가 2일뿐이면 다른 이유와 합쳐 4일이어도 처방하지 않는다")
  void doesNotPrescribeWhenNoReasonReachesThreeDays() {
    List<ReasonFact> facts = new ArrayList<>(days(MissReason.PHONE, 2));
    facts.add(fact(3, MissReason.UNWILLING));
    facts.add(fact(4, MissReason.UNWILLING));

    assertThat(ReflectionRule.decide(TODAY, facts, null)).isNull();
  }

  @Test
  @DisplayName("같은 날 같은 이유가 여러 번 나와도 하루로 센다")
  void countsSameDayOnce() {
    List<ReasonFact> facts =
        List.of(
            fact(0, MissReason.PHONE),
            fact(0, MissReason.PHONE),
            fact(1, MissReason.PHONE),
            fact(1, MissReason.PHONE));

    assertThat(ReflectionRule.decide(TODAY, facts, null)).isNull();
  }

  @Test
  @DisplayName("7일 전(오늘을 넣어 8일째)의 기록은 세지 않는다")
  void ignoresFactsOutsideSevenDays() {
    List<ReasonFact> facts =
        List.of(fact(0, MissReason.PHONE), fact(6, MissReason.PHONE), fact(7, MissReason.PHONE));
    assertThat(ReflectionRule.decide(TODAY, facts, null)).isNull();

    List<ReasonFact> withinSevenDays =
        List.of(fact(0, MissReason.PHONE), fact(5, MissReason.PHONE), fact(6, MissReason.PHONE));
    assertThat(ReflectionRule.decide(TODAY, withinSevenDays, null))
        .isEqualTo(new Decision(MissReason.PHONE, 3));
  }

  @Test
  @DisplayName("아픔은 4일이어도 처방하지 않는다 (아플 때는 쉬는 게 먼저)")
  void neverPrescribesForSick() {
    assertThat(ReflectionRule.decide(TODAY, days(MissReason.SICK, 4), null)).isNull();
  }

  @Test
  @DisplayName("3일이 된 이유가 여럿이면 날 수가 많은 것, 같으면 MissReason 에 먼저 적힌 것을 고른다")
  void picksMostDaysThenEnumOrder() {
    List<ReasonFact> moreUnwilling = new ArrayList<>(days(MissReason.PHONE, 3));
    moreUnwilling.addAll(days(MissReason.UNWILLING, 4));
    assertThat(ReflectionRule.decide(TODAY, moreUnwilling, null))
        .isEqualTo(new Decision(MissReason.UNWILLING, 4));

    // PHONE 이 UNWILLING 보다 MissReason 에 먼저 적혀 있습니다.
    List<ReasonFact> tie = new ArrayList<>(days(MissReason.UNWILLING, 3));
    tie.addAll(days(MissReason.PHONE, 3));
    assertThat(ReflectionRule.decide(TODAY, tie, null))
        .isEqualTo(new Decision(MissReason.PHONE, 3));
  }

  @Test
  @DisplayName("지난 처방이 끝난 다음 날부터 7일은 쉬고, 8일째부터 다시 처방할 수 있다")
  void restsSevenDaysAfterLastPrescription() {
    List<ReasonFact> facts = days(MissReason.PHONE, 5);

    // 아직 처방 기간 안 (오늘이 마지막 날)
    assertThat(ReflectionRule.decide(TODAY, facts, TODAY)).isNull();
    // 끝난 지 7일째 = 쉬는 기간의 마지막 날
    assertThat(ReflectionRule.decide(TODAY, facts, TODAY.minusDays(7))).isNull();
    // 끝난 지 8일째
    assertThat(ReflectionRule.decide(TODAY, facts, TODAY.minusDays(8)))
        .isEqualTo(new Decision(MissReason.PHONE, 5));
  }

  @Test
  @DisplayName("기록이 없으면 처방하지 않는다")
  void doesNotPrescribeWithoutFacts() {
    assertThat(ReflectionRule.decide(TODAY, List.of(), null)).isNull();
  }

  @Test
  @DisplayName("처방 이유 문장: 이유 이름과 날 수를 넣고, 기타는 '다른 이유로' 라고 쓴다")
  void reasonSentence() {
    assertThat(ReflectionRule.reasonSentence(MissReason.UNWILLING, 3, 5))
        .isEqualTo("최근 일주일 동안 '하기 싫음' 때문에 못 채운 날이 3일 있었어. 5일 동안 짧게 돌아보자.");
    assertThat(ReflectionRule.reasonSentence(MissReason.ETC, 4, 5))
        .isEqualTo("최근 일주일 동안 다른 이유로 못 채운 날이 4일 있었어. 5일 동안 짧게 돌아보자.");
  }
}
