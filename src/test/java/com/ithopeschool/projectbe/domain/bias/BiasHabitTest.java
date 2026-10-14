package com.ithopeschool.projectbe.domain.bias;

import static com.ithopeschool.projectbe.domain.bias.CognitiveBias.EXTERNAL_BLAME;
import static com.ithopeschool.projectbe.domain.bias.CognitiveBias.FUTURE_SELF;
import static com.ithopeschool.projectbe.domain.bias.CognitiveBias.PLANNING_FALLACY;
import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.bias.BiasHabit.Sighting;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// "자주 보이는 생각 습관" 규칙(최근 14일 중 서로 다른 3일 이상)을 확인합니다. 예시는 BiasHabit 주석과 같습니다.
class BiasHabitTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

  private static Sighting seen(int month, int day, CognitiveBias bias, String evidence) {
    return new Sighting(LocalDate.of(2026, month, day), bias, evidence);
  }

  @Test
  @DisplayName("서로 다른 3일에 보이면 자주 보이는 습관이고, 보인 날 수·가장 최근 날·근거를 알려 준다")
  void threeDifferentDays() {
    List<BiasHabit> habits =
        BiasHabit.of(
            List.of(
                seen(10, 5, PLANNING_FALLACY, "옛 근거"),
                seen(10, 13, PLANNING_FALLACY, "금방 할 줄 알았다고 함"),
                seen(10, 10, PLANNING_FALLACY, "중간 근거")),
            TODAY);

    assertThat(habits).hasSize(1);
    assertThat(habits.get(0).bias()).isEqualTo(PLANNING_FALLACY);
    assertThat(habits.get(0).days()).isEqualTo(3);
    assertThat(habits.get(0).lastDate()).isEqualTo(LocalDate.of(2026, 10, 13));
    assertThat(habits.get(0).lastEvidence()).isEqualTo("금방 할 줄 알았다고 함");
  }

  @Test
  @DisplayName("같은 날 여러 번 보인 것은 하루로 센다")
  void sameDayCountsOnce() {
    List<Sighting> sightings =
        List.of(
            seen(10, 13, EXTERNAL_BLAME, null),
            seen(10, 13, EXTERNAL_BLAME, null),
            seen(10, 12, EXTERNAL_BLAME, null));

    assertThat(BiasHabit.of(sightings, TODAY)).isEmpty();
  }

  @Test
  @DisplayName("오늘을 넣어 14일보다 전은 세지 않는다 (10월 14일 기준 10월 1일부터)")
  void onlyTheLastTwoWeeks() {
    List<Sighting> outside =
        List.of(
            seen(9, 30, FUTURE_SELF, null),
            seen(10, 2, FUTURE_SELF, null),
            seen(10, 9, FUTURE_SELF, null));
    List<Sighting> edge =
        List.of(
            seen(10, 1, FUTURE_SELF, null),
            seen(10, 2, FUTURE_SELF, null),
            seen(10, 9, FUTURE_SELF, null));

    assertThat(BiasHabit.of(outside, TODAY)).isEmpty();
    assertThat(BiasHabit.of(edge, TODAY)).hasSize(1);
  }

  @Test
  @DisplayName("보인 날이 많은 것부터, 같으면 더 최근에 보인 것부터 늘어놓는다")
  void ordering() {
    List<Sighting> sightings =
        List.of(
            // 미래의 나 믿기: 3일, 마지막 10/11
            seen(10, 9, FUTURE_SELF, null),
            seen(10, 10, FUTURE_SELF, null),
            seen(10, 11, FUTURE_SELF, null),
            // 계획 오류: 3일, 마지막 10/13
            seen(10, 3, PLANNING_FALLACY, null),
            seen(10, 4, PLANNING_FALLACY, null),
            seen(10, 13, PLANNING_FALLACY, null),
            // 상황 탓하기: 4일
            seen(10, 5, EXTERNAL_BLAME, null),
            seen(10, 6, EXTERNAL_BLAME, null),
            seen(10, 7, EXTERNAL_BLAME, null),
            seen(10, 8, EXTERNAL_BLAME, null));

    assertThat(BiasHabit.of(sightings, TODAY))
        .extracting(BiasHabit::bias)
        .containsExactly(EXTERNAL_BLAME, PLANNING_FALLACY, FUTURE_SELF);
  }

  @Test
  @DisplayName("근거가 하나도 없으면 근거는 null 이고, 가장 최근 통화에 근거가 없으면 그 전 근거를 쓴다")
  void evidenceFallsBackToTheLatestOneWithEvidence() {
    List<Sighting> noEvidence =
        List.of(
            seen(10, 1, FUTURE_SELF, null),
            seen(10, 2, FUTURE_SELF, null),
            seen(10, 3, FUTURE_SELF, null));
    List<Sighting> lastHasNone =
        List.of(
            seen(10, 1, FUTURE_SELF, "첫 근거"),
            seen(10, 2, FUTURE_SELF, "둘째 근거"),
            seen(10, 3, FUTURE_SELF, null));

    assertThat(BiasHabit.of(noEvidence, TODAY).get(0).lastEvidence()).isNull();
    assertThat(BiasHabit.of(lastHasNone, TODAY).get(0).lastEvidence()).isEqualTo("둘째 근거");
    assertThat(BiasHabit.of(lastHasNone, TODAY).get(0).lastDate())
        .isEqualTo(LocalDate.of(2026, 10, 3));
  }

  @Test
  @DisplayName("기록이 없으면 빈 목록이다")
  void noSightings() {
    assertThat(BiasHabit.of(List.of(), TODAY)).isEmpty();
  }
}
