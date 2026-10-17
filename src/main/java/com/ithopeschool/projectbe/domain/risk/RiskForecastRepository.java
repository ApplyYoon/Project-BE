package com.ithopeschool.projectbe.domain.risk;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// risk_forecasts 테이블의 창구입니다. 학생 한 명의 것을 다루는 메서드에는 모두 userId 가 들어 있어서,
// 다른 사람의 데이터가 섞이지 않습니다.
public interface RiskForecastRepository extends JpaRepository<RiskForecast, Long> {

  // 내 그날의 예측. 아직 안 했으면 빈 Optional 입니다. (새로 만들지 고칠지 정할 때, 하나 보여 줄 때 씁니다)
  Optional<RiskForecast> findByUserIdAndForecastDate(Long userId, LocalDate forecastDate);

  // 내 from ~ to (둘 다 포함) 사이의 예측들, 날짜 순서로.
  List<RiskForecast> findByUserIdAndForecastDateBetweenOrderByForecastDateAsc(
      Long userId, LocalDate from, LocalDate to);

  // 실제 달성률이 아직 비어 있는 예측들 중 from ~ to (둘 다 포함) 사이. 모든 학생의 것입니다.
  // 예약 작업(RiskConfirmJob)이 "그날이 지났으니 결과를 채울 예측" 을 찾을 때 씁니다.
  // 학생 한 명의 것은 아래 메서드를 씁니다.
  List<RiskForecast> findByActualAchievementRateIsNullAndForecastDateBetween(
      LocalDate from, LocalDate to);

  // 내 예측 중 실제 달성률이 아직 비어 있고 from ~ to 사이인 것들.
  List<RiskForecast> findByUserIdAndActualAchievementRateIsNullAndForecastDateBetween(
      Long userId, LocalDate from, LocalDate to);
}
