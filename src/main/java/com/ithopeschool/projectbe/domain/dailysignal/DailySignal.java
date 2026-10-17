package com.ithopeschool.projectbe.domain.dailysignal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 하루 신호 테이블. 한 줄 = 한 사람의 하루치 휴대폰·수면·걸음 데이터. (이슈 #18)
// 같은 날을 다시 보내면 새 줄을 만들지 않고 그 줄을 덮어씁니다.
// 그래서 한 사람에게 같은 날짜의 줄은 하나뿐입니다. (user_id, signal_date) 가 겹치지 않게 막아 두었습니다.
//
// 모든 데이터 칸은 비어 있어도 됩니다. 워치·헬스 앱이 없는 학생도 핵심 기능은 쓸 수 있어야 하기 때문입니다.
// 비어 있는 칸은 "0" 이 아니라 "모름" 입니다. 분석에서 0 으로 세면 안 됩니다.
//
// 안드로이드의 앱별 사용 시간은 이번에 빼고 나중에 따로 만듭니다.
// 건강 데이터라 암호화 대상입니다. 암호화는 #68 에서 붙입니다.
@Entity
@Table(
    name = "daily_signals",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_daily_signals_user_id_signal_date",
            columnNames = {"user_id", "signal_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailySignal {

  // 줄을 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 데이터의 주인(users.id). 다른 사람의 데이터가 섞이지 않게 하는 기준입니다.
  @Column(nullable = false)
  private Long userId;

  // 어느 날의 데이터인지. (date 는 DB 예약어라 signalDate 라고 지었습니다)
  @Column(nullable = false)
  private LocalDate signalDate;

  // 실제로 잔 시간(분). 중간에 깬 시간이 빠져 있어서 잠든~일어난 시각의 차이와 다를 수 있습니다. 모르면 비어 있습니다.
  private Integer sleepMinutes;

  // 잠든 시각 (날짜까지 함께 저장해서 자정을 넘겨 자도 헷갈리지 않습니다). 모르면 비어 있습니다.
  private Instant sleptAt;

  // 일어난 시각. 잠든 시각보다 뒤입니다. 모르면 비어 있습니다.
  private Instant wokeAt;

  // 하루 걸음 수. 모르면 비어 있습니다.
  private Integer steps;

  // SNS 를 30분 넘게 썼는지 (아이폰은 앱별 시간 대신 이런 신호만 줍니다). 모르면 비어 있습니다.
  private Boolean snsOverThirtyMinutes;

  public DailySignal(
      Long userId,
      LocalDate signalDate,
      Integer sleepMinutes,
      Instant sleptAt,
      Instant wokeAt,
      Integer steps,
      Boolean snsOverThirtyMinutes) {
    this.userId = userId;
    this.signalDate = signalDate;
    overwrite(sleepMinutes, sleptAt, wokeAt, steps, snsOverThirtyMinutes);
  }

  // 같은 날을 다시 보냈을 때, 새로 받은 값으로 전부 바꿉니다.
  // 이번에 안 보낸 칸은 비워집니다. 예전 값을 남기지 않고 "마지막에 보낸 그대로" 가 그날의 데이터입니다.
  public void overwrite(
      Integer sleepMinutes,
      Instant sleptAt,
      Instant wokeAt,
      Integer steps,
      Boolean snsOverThirtyMinutes) {
    this.sleepMinutes = sleepMinutes;
    this.sleptAt = sleptAt;
    this.wokeAt = wokeAt;
    this.steps = steps;
    this.snsOverThirtyMinutes = snsOverThirtyMinutes;
  }
}
