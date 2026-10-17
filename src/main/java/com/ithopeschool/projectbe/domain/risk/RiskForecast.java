package com.ithopeschool.projectbe.domain.risk;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 위험 예측 기록 테이블. 한 줄 = 한 사람의 하루치 예측. (이슈 #49 분석 ⑧, 학생이 설계)
// "무너질 가능성이 높은 날" 을 미리 적어 두고, 그날이 지난 뒤 실제 달성률을 채워서 예측이 맞았는지 남깁니다.
//
// 위험 없음(NONE)인 날도 한 줄을 남깁니다. "위험하다고 한 날" 만 남기면 "위험 없다고 했는데 무너진 날" 을 셀 수 없어서입니다.
// 같은 날을 다시 예측하면 새 줄을 만들지 않고 그 줄을 최신 결과로 고칩니다.
// 그래서 한 사람에게 같은 날짜의 줄은 하나뿐입니다. (user_id, forecast_date) 가 겹치지 않게 막아 두었습니다.
//
// 다시 예측해도 고치지 않는 경우(RiskService)
//   - 실제 달성률이 이미 채워진 줄: 결과를 본 뒤에 예측을 바꾸는 일을 막기 위해 확정으로 둡니다.
//   - 고칠 때는 위험 수준과 이유만 바꾸고, 실제 달성률·맞았는지는 건드리지 않습니다.
//
// 위험하다고 본 이유는 한 줄에 여러 개일 수 있어서 따로 딸린 테이블(@ElementCollection)에 담습니다.
//   risk_forecast_reasons : (risk_forecast_id, reason)  이유마다 한 줄
//
// 실제 달성률과 맞았는지는 처음에 비어 있습니다. (아직 그날이 안 지났다는 뜻)
// 그날 계획이 없어서 달성률을 구할 수 없었던 날도 비어 있습니다. 두 경우 모두 "맞았는지 알 수 없음" 입니다.
@Entity
@Table(
    name = "risk_forecasts",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_risk_forecasts_user_id_forecast_date",
            columnNames = {"user_id", "forecast_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiskForecast {

  // 줄을 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 예측의 주인(users.id). 다른 사람의 데이터가 섞이지 않게 하는 기준입니다.
  @Column(nullable = false)
  private Long userId;

  // 어느 날을 예측했는지. 하루의 기준(새벽 4시)을 따르는 날짜입니다.
  @Column(nullable = false)
  private LocalDate forecastDate;

  // 위험 수준 (NONE 없음 / WATCH 주의 / HIGH 높음)
  @Column(nullable = false, length = 10)
  @Enumerated(EnumType.STRING)
  private RiskLevel riskLevel;

  // 그날이 지난 뒤 채우는 실제 달성률(0 ~ 100). 아직 안 지났거나 그날 계획이 없었으면 비어 있습니다.
  private Integer actualAchievementRate;

  // 예측이 맞았는지. 실제 달성률을 채울 때 함께 채웁니다. 모르면 비어 있습니다.
  //   위험하다(주의·높음) + 못 채운 날(100% 미만)  → 맞음
  //   위험 없음            + 다 채운 날(100%)       → 맞음
  //   그 밖에는 틀림
  private Boolean wasCorrect;

  // 위험하다고 본 이유들. 위험 없음이면 비어 있습니다.
  // EAGER: 예측을 꺼낼 때 이유를 늘 같이 쓰기 때문에 한 번에 읽어 옵니다.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "risk_forecast_reasons",
      joinColumns = @JoinColumn(name = "risk_forecast_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "reason", length = 20, nullable = false)
  private Set<RiskReason> reasons = EnumSet.noneOf(RiskReason.class);

  // 새 예측을 만듭니다. 실제 달성률과 맞았는지는 그날이 지난 뒤에 채웁니다.
  public RiskForecast(
      Long userId, LocalDate forecastDate, RiskLevel riskLevel, Set<RiskReason> reasons) {
    this.userId = userId;
    this.forecastDate = forecastDate;
    this.riskLevel = riskLevel;
    this.reasons = copyOf(reasons);
  }

  // 같은 날을 다시 예측했을 때 최신 결과로 고칩니다. 위험 수준과 이유만 바꿉니다.
  // 실제 달성률이 이미 채워진 줄(확정)이면 이 메서드를 부르기 전에 막아야 합니다. (RiskService)
  public void renew(RiskLevel riskLevel, Set<RiskReason> reasons) {
    this.riskLevel = riskLevel;
    // 컬렉션을 통째로 새 값으로 바꿔서, 옛 이유가 남아 새 이유와 섞이지 않게 합니다.
    this.reasons = copyOf(reasons);
  }

  // 그날이 지난 뒤 실제 달성률을 채우고, 예측이 맞았는지 정합니다.
  // actualAchievementRate: 0 ~ 100. 그날 계획이 없어서 구할 수 없으면 이 메서드를 부르지 않습니다.
  // missedDay: 그날을 "못 채운 날" 로 볼지 (CheckInQuestions.isMissedDay: 달성률 100% 미만)
  public void confirm(int actualAchievementRate, boolean missedDay) {
    this.actualAchievementRate = actualAchievementRate;
    this.wasCorrect = riskLevel.isRisky() == missedDay;
  }

  // 실제 달성률이 채워진 확정된 예측인지 알려줍니다.
  public boolean isConfirmed() {
    return actualAchievementRate != null;
  }

  private static Set<RiskReason> copyOf(Set<RiskReason> reasons) {
    return reasons.isEmpty() ? EnumSet.noneOf(RiskReason.class) : EnumSet.copyOf(reasons);
  }
}
