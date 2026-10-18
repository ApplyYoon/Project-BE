package com.ithopeschool.projectbe.domain.care;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.card.CardPriority;
import com.ithopeschool.projectbe.domain.cardresult.CardResult;
import com.ithopeschool.projectbe.domain.cardresult.JudgedCardDay;
import com.ithopeschool.projectbe.domain.checkin.EmotionTag;
import com.ithopeschool.projectbe.domain.psychtest.PsychScale;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 관리 단계 판정의 재료 중 "연속 일수" 계산을 확인합니다. (FE src/lib/dailyCareRun.ts 주석의 예시)
// DB 에서 모아 판정 · 저장하는 흐름은 CareStageFlowTest 가 확인합니다.
class CareStageServiceTest {

  private static LocalDate october(int day) {
    return LocalDate.of(2026, 10, day);
  }

  // 카드 하루치 판정 한 칸 (이 테스트는 날짜와 결과만 봅니다)
  private static JudgedCardDay card(long cardId, int day, CardResult result) {
    double runRate = result == CardResult.DONE ? 1 : 0;
    return new JudgedCardDay(
        cardId, october(day), false, CardPriority.FLEXIBLE, result, runRate, null, null, null);
  }

  @Test
  @DisplayName("휴식·미실행 연속: 10/17 미실행 · 10/16 휴식 · 10/15 실행 → 2")
  void restOrMissStreak() {
    List<JudgedCardDay> days =
        List.of(card(1, 17, CardResult.FAILED), card(1, 15, CardResult.DONE));

    assertThat(CareStageService.countRestOrMissStreak(days, Set.of(october(16)), october(17)))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("휴식·미실행 연속: 10/17 카드 2개 중 1개 실행 → 0, 카드가 없는 날에서 멈춘다")
  void restOrMissStreakStops() {
    List<JudgedCardDay> halfDone =
        List.of(card(1, 17, CardResult.DONE), card(2, 17, CardResult.POSTPONED));
    assertThat(CareStageService.countRestOrMissStreak(halfDone, Set.of(), october(17))).isZero();

    // 10/17 · 10/16 미실행, 10/15 카드 없음, 10/14 미실행 → 10/15 에서 멈춰서 2
    List<JudgedCardDay> gap =
        List.of(
            card(1, 17, CardResult.UNHANDLED),
            card(1, 16, CardResult.FAILED),
            card(1, 14, CardResult.FAILED));
    assertThat(CareStageService.countRestOrMissStreak(gap, Set.of(), october(17))).isEqualTo(2);
  }

  @Test
  @DisplayName("부정 감정 연속: [불안함, 피곤함] · [막막함] · [평온함] → 2, 태그가 없는 날에서 멈춘다")
  void negativeMoodStreak() {
    Map<LocalDate, Set<EmotionTag>> emotions =
        Map.of(
            october(17), Set.of(EmotionTag.ANXIOUS, EmotionTag.TIRED),
            october(16), Set.of(EmotionTag.STUCK),
            october(15), Set.of(EmotionTag.CALM));
    assertThat(CareStageService.countNegativeMoodStreak(emotions, october(17))).isEqualTo(2);

    // 부정 태그와 긍정 태그를 같이 고른 날은 "부정만" 이 아니라서 멈춥니다.
    Map<LocalDate, Set<EmotionTag>> mixed =
        Map.of(october(17), Set.of(EmotionTag.TIRED, EmotionTag.MOTIVATED));
    assertThat(CareStageService.countNegativeMoodStreak(mixed, october(17))).isZero();

    assertThat(CareStageService.countNegativeMoodStreak(Map.of(), october(17))).isZero();
  }

  @Test
  @DisplayName("관리 선호 척도: 5 이상 높음 · 3 이하 낮음 · 그 사이 보통 (FE getScaleLevel 과 같음)")
  void carePreference() {
    assertThat(CareStageService.carePreferenceOf(scoresWithCarePreference(6)))
        .isEqualTo(Willpower.CarePreference.HIGH);
    assertThat(CareStageService.carePreferenceOf(scoresWithCarePreference(4)))
        .isEqualTo(Willpower.CarePreference.MID);
    assertThat(CareStageService.carePreferenceOf(scoresWithCarePreference(2.5)))
        .isEqualTo(Willpower.CarePreference.LOW);
  }

  private static Map<PsychScale, Double> scoresWithCarePreference(double score) {
    Map<PsychScale, Double> scores = new EnumMap<>(PsychScale.class);
    scores.put(PsychScale.CARE_PREFERENCE, score);
    return scores;
  }
}
