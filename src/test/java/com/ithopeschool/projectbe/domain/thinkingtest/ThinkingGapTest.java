package com.ithopeschool.projectbe.domain.thinkingtest;

import static com.ithopeschool.projectbe.domain.thinkingtest.ThinkingGap.Verdict.BIGGER;
import static com.ithopeschool.projectbe.domain.thinkingtest.ThinkingGap.Verdict.MATCHED;
import static com.ithopeschool.projectbe.domain.thinkingtest.ThinkingGap.Verdict.SMALLER;
import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.bias.Weakness;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.thinkingtest.ThinkingGap.CallFact;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// "내 생각 vs 현실" 판정 규칙(ThinkingGap 주석의 표)을 확인합니다.
class ThinkingGapTest {

  private static final LocalDate DAY = LocalDate.of(2026, 10, 14);

  private static CallFact reason(MissReason reason) {
    return new CallFact(DAY, null, reason);
  }

  private static CallFact found(CognitiveBias bias) {
    return new CallFact(DAY, bias, null);
  }

  private static List<CallFact> calls(CallFact... facts) {
    return new ArrayList<>(List.of(facts));
  }

  @Test
  @DisplayName("판정 규칙: 꼽았고 1번 이상 → 맞았어 / 꼽았는데 0번 → 작았어 / 안 꼽았는데 2번 이상 → 컸어 / 그 밖 → 없음")
  void verdictRule() {
    assertThat(ThinkingGap.verdictOf(true, 1)).isEqualTo(MATCHED);
    assertThat(ThinkingGap.verdictOf(true, 0)).isEqualTo(SMALLER);
    assertThat(ThinkingGap.verdictOf(false, 2)).isEqualTo(BIGGER);
    assertThat(ThinkingGap.verdictOf(false, 1)).isNull();
    assertThat(ThinkingGap.verdictOf(false, 0)).isNull();
  }

  @Test
  @DisplayName("약점 칩은 짝인 '못 채운 이유' 로 견준다 (게임·유튜브 → 폰·SNS, 잠 → 피곤, 계획 세우기 → 계획 과다)")
  void weaknessesAreComparedThroughMissReasons() {
    ThinkingGap gap =
        ThinkingGap.of(
            List.of(),
            Set.of(Weakness.GAME, Weakness.VIDEO, Weakness.SLEEP),
            calls(
                reason(MissReason.TIRED),
                reason(MissReason.TOO_MUCH),
                reason(MissReason.TOO_MUCH),
                reason(MissReason.TOO_MUCH)),
            14);

    assertThat(gap.reasons())
        .extracting(r -> r.reason() + ":" + r.verdict() + ":" + r.actualCount())
        .containsExactly("PHONE:SMALLER:0", "TIRED:MATCHED:1", "TOO_MUCH:BIGGER:3");
    assertThat(gap.reasons().get(0).thoughtBy()).containsExactly(Weakness.GAME, Weakness.VIDEO);
    assertThat(gap.enoughData()).isTrue();
  }

  @Test
  @DisplayName("아픔·기타는 견주지 않고, 짝이 없는 약점(집중력·기분)은 '확인할 수 없는 약점' 으로 따로 준다")
  void sickEtcAndUnverifiable() {
    ThinkingGap gap =
        ThinkingGap.of(
            List.of(),
            Set.of(Weakness.FOCUS, Weakness.MOOD),
            calls(
                reason(MissReason.SICK),
                reason(MissReason.SICK),
                reason(MissReason.ETC),
                reason(MissReason.ETC)),
            14);

    assertThat(gap.reasons()).isEmpty();
    assertThat(gap.unverifiable()).containsExactly(Weakness.FOCUS, Weakness.MOOD);
  }

  @Test
  @DisplayName("테스트의 습관과 통화에서 보인 습관도 같은 규칙으로 견준다")
  void habitsAreCompared() {
    ThinkingGap gap =
        ThinkingGap.of(
            List.of(CognitiveBias.PLANNING_FALLACY, CognitiveBias.ALL_OR_NOTHING),
            Set.of(),
            calls(
                found(CognitiveBias.PLANNING_FALLACY),
                found(CognitiveBias.FUTURE_SELF),
                found(CognitiveBias.FUTURE_SELF)),
            14);

    assertThat(gap.habits())
        .extracting(h -> h.bias() + ":" + h.verdict())
        .containsExactly(
            "PLANNING_FALLACY:MATCHED", "FUTURE_SELF:BIGGER", "ALL_OR_NOTHING:SMALLER");
  }

  @Test
  @DisplayName("문장은 '컸어 → 작았어 → 맞았어' 순서이고, 정해 둔 틀로 숫자를 넣는다")
  void sentences() {
    ThinkingGap gap =
        ThinkingGap.of(
            List.of(),
            Set.of(Weakness.GAME, Weakness.SLEEP),
            calls(
                reason(MissReason.TIRED), reason(MissReason.TOO_MUCH), reason(MissReason.TOO_MUCH)),
            14);

    assertThat(gap.sentences())
        .containsExactly(
            "꼽지 않았던 '계획 과다' 쪽이 실제로는 못 채운 이유로 2번 나왔어. 생각보다 큰 원인일 수 있어.",
            "내가 꼽은 약점 '게임' 쪽은 최근 14일 동안 실제로 못 채운 이유로 나오지 않았어.",
            "내가 꼽은 약점 '잠·늦잠' 쪽은 실제로도 '피곤·수면' 때문에 못 채운 날이 1번 있었어. 생각이 맞았어.");
    // 통화에서 이야기할 거리는 맞았던 것을 뺀 두 문장
    assertThat(gap.differenceSentences()).hasSize(2);
  }

  @Test
  @DisplayName("통화가 3번보다 적으면 '더 지켜봐야 해' 를 먼저 말한다")
  void notEnoughData() {
    ThinkingGap gap =
        ThinkingGap.of(List.of(), Set.of(Weakness.GAME), calls(reason(MissReason.TIRED)), 14);

    assertThat(gap.enoughData()).isFalse();
    assertThat(gap.sentences().get(0)).isEqualTo("아직 통화가 1번뿐이라 더 지켜봐야 해. 통화가 쌓일수록 정확해져.");
  }

  @Test
  @DisplayName("통화가 충분한데 다른 점이 하나도 없으면 '크게 다르지 않아' 한 줄이다")
  void noDifference() {
    ThinkingGap gap =
        ThinkingGap.of(
            List.of(),
            Set.of(),
            calls(reason(MissReason.TIRED), reason(MissReason.SICK), reason(MissReason.EVENT)),
            14);

    assertThat(gap.sentences()).containsExactly("아직은 내가 생각한 나와 실제 기록이 크게 다르지 않아.");
  }
}
