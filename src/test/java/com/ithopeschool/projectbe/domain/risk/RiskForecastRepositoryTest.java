package com.ithopeschool.projectbe.domain.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

// 위험 예측 기록(risk_forecasts)과 딸린 이유(risk_forecast_reasons)를 실제 DB(H2)에 넣고 꺼내 봅니다.
@DataJpaTest
class RiskForecastRepositoryTest {

  private static final Set<RiskReason> BOTH =
      EnumSet.of(RiskReason.WEAK_WEEKDAY, RiskReason.LATE_SLEEP);

  @Autowired private RiskForecastRepository riskForecastRepository;
  @Autowired private EntityManager em;

  private static LocalDate october(int day) {
    return LocalDate.of(2026, 10, day);
  }

  private RiskForecast save(Long userId, int day, RiskLevel level, Set<RiskReason> reasons) {
    return riskForecastRepository.saveAndFlush(
        new RiskForecast(userId, october(day), level, reasons));
  }

  // 저장한 것이 메모리에 남아 있어서 진짜 DB 에서 다시 읽은 게 아닌 일이 없도록, 비우고 다시 꺼냅니다.
  private RiskForecast reload(Long userId, int day) {
    em.flush();
    em.clear();
    return riskForecastRepository.findByUserIdAndForecastDate(userId, october(day)).orElseThrow();
  }

  @Test
  @DisplayName("저장하면 위험 수준과 이유가 그대로 꺼내지고, 실제 달성률·맞았는지는 비어 있다")
  void savesLevelAndReasons() {
    save(1L, 13, RiskLevel.HIGH, BOTH);

    RiskForecast found = reload(1L, 13);

    assertThat(found.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
    assertThat(found.getReasons())
        .containsExactlyInAnyOrder(RiskReason.WEAK_WEEKDAY, RiskReason.LATE_SLEEP);
    assertThat(found.getActualAchievementRate()).isNull();
    assertThat(found.getWasCorrect()).isNull();
    assertThat(found.isConfirmed()).isFalse();
  }

  @Test
  @DisplayName("위험 없음(NONE)도 이유 없이 한 줄로 저장된다")
  void savesNoneWithoutReasons() {
    save(1L, 13, RiskLevel.NONE, EnumSet.noneOf(RiskReason.class));

    RiskForecast found = reload(1L, 13);

    assertThat(found.getRiskLevel()).isEqualTo(RiskLevel.NONE);
    assertThat(found.getReasons()).isEmpty();
  }

  @Test
  @DisplayName("한 사람이 같은 날짜로 두 줄을 만들 수는 없다 (다른 사람이면 괜찮다)")
  void oneRowPerUserPerDay() {
    save(1L, 13, RiskLevel.WATCH, EnumSet.of(RiskReason.LATE_SLEEP));
    save(2L, 13, RiskLevel.WATCH, EnumSet.of(RiskReason.LATE_SLEEP));

    assertThatThrownBy(() -> save(1L, 13, RiskLevel.NONE, EnumSet.noneOf(RiskReason.class)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("다시 예측해 고치면 위험 수준이 바뀌고, 옛 이유는 남지 않고 새 이유로 통째로 바뀐다")
  void renewReplacesReasons() {
    RiskForecast forecast = save(1L, 13, RiskLevel.HIGH, BOTH);

    forecast.renew(RiskLevel.WATCH, EnumSet.of(RiskReason.WEAK_WEEKDAY));
    RiskForecast found = reload(1L, 13);
    assertThat(found.getRiskLevel()).isEqualTo(RiskLevel.WATCH);
    assertThat(found.getReasons()).containsExactly(RiskReason.WEAK_WEEKDAY);

    // 이유가 모두 사라지는 경우(위험 없음)도 옛 이유가 남지 않아야 합니다.
    found.renew(RiskLevel.NONE, EnumSet.noneOf(RiskReason.class));
    RiskForecast none = reload(1L, 13);
    assertThat(none.getRiskLevel()).isEqualTo(RiskLevel.NONE);
    assertThat(none.getReasons()).isEmpty();
    assertThat(riskForecastRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("고쳐도 이미 채운 실제 달성률·맞았는지는 지워지지 않는다")
  void renewKeepsTheResultColumns() {
    RiskForecast forecast = save(1L, 13, RiskLevel.HIGH, BOTH);
    forecast.confirm(40, true);

    forecast.renew(RiskLevel.NONE, EnumSet.noneOf(RiskReason.class));
    RiskForecast found = reload(1L, 13);

    assertThat(found.getActualAchievementRate()).isEqualTo(40);
    assertThat(found.getWasCorrect()).isTrue();
  }

  @Test
  @DisplayName("맞았는지: 위험하다고 했고 못 채웠으면 맞고, 위험 없다고 했는데 못 채웠으면 틀린다")
  void wasCorrectRule() {
    // 위험하다(주의·높음) + 못 채운 날(100% 미만) → 맞음 / + 다 채운 날(100%) → 틀림
    RiskForecast watch = save(1L, 13, RiskLevel.WATCH, EnumSet.of(RiskReason.LATE_SLEEP));
    watch.confirm(99, true);
    assertThat(watch.getWasCorrect()).isTrue();
    RiskForecast high = save(1L, 14, RiskLevel.HIGH, BOTH);
    high.confirm(100, false);
    assertThat(high.getWasCorrect()).isFalse();

    // 위험 없음 + 다 채운 날 → 맞음 / + 못 채운 날 → 틀림
    RiskForecast noneFull = save(1L, 15, RiskLevel.NONE, EnumSet.noneOf(RiskReason.class));
    noneFull.confirm(100, false);
    assertThat(noneFull.getWasCorrect()).isTrue();
    RiskForecast noneMissed = save(1L, 16, RiskLevel.NONE, EnumSet.noneOf(RiskReason.class));
    noneMissed.confirm(0, true);
    assertThat(noneMissed.getWasCorrect()).isFalse();

    assertThat(watch.isConfirmed()).isTrue();
    // 달성률 0% 도 "채워진 것" 입니다. (비어 있음과 0 은 다릅니다)
    assertThat(noneMissed.isConfirmed()).isTrue();
  }

  @Test
  @DisplayName("기간 조회는 내 것만, 날짜 순서로, 양 끝 날짜를 포함해서 꺼낸다")
  void findsBetweenDatesForOneUser() {
    save(1L, 15, RiskLevel.NONE, EnumSet.noneOf(RiskReason.class));
    save(1L, 12, RiskLevel.WATCH, EnumSet.of(RiskReason.LATE_SLEEP));
    save(1L, 20, RiskLevel.HIGH, BOTH);
    save(1L, 11, RiskLevel.NONE, EnumSet.noneOf(RiskReason.class));
    save(2L, 13, RiskLevel.HIGH, BOTH);
    em.flush();
    em.clear();

    List<RiskForecast> found =
        riskForecastRepository.findByUserIdAndForecastDateBetweenOrderByForecastDateAsc(
            1L, october(12), october(20));

    assertThat(found)
        .extracting(RiskForecast::getForecastDate)
        .containsExactly(october(12), october(15), october(20));
  }

  @Test
  @DisplayName("결과가 비어 있는 예측만, 기간 안에서, 내 것만(또는 모두의 것) 찾는다")
  void findsOnlyPendingOnes() {
    save(1L, 10, RiskLevel.NONE, EnumSet.noneOf(RiskReason.class)); // 기간 밖
    save(1L, 12, RiskLevel.WATCH, EnumSet.of(RiskReason.LATE_SLEEP)); // 비어 있음
    RiskForecast done = save(1L, 13, RiskLevel.HIGH, BOTH); // 이미 채움
    done.confirm(50, true);
    save(2L, 14, RiskLevel.NONE, EnumSet.noneOf(RiskReason.class)); // 다른 사람, 비어 있음
    em.flush();
    em.clear();

    assertThat(
            riskForecastRepository.findByUserIdAndActualAchievementRateIsNullAndForecastDateBetween(
                1L, october(11), october(14)))
        .extracting(RiskForecast::getForecastDate)
        .containsExactly(october(12));
    assertThat(
            riskForecastRepository.findByActualAchievementRateIsNullAndForecastDateBetween(
                october(11), october(14)))
        .extracting(RiskForecast::getUserId)
        .containsExactlyInAnyOrder(1L, 2L);
  }
}
