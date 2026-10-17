package com.ithopeschool.projectbe.domain.restday;

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

// 휴식 처리한 날 테이블. 한 줄 = 한 사람이 하루를 "휴식" 으로 처리한 것. (최종 기획서 「4.5 상담」 휴식 처리, 이슈 #225)
// 미리 "오늘 쉴게" 는 없고, 불가항력(번아웃 · 아픔)으로 못 한 날을 지난 뒤에 처리합니다. 한 달에 4번까지입니다.
// 휴식한 날의 카드는 실패 집계와 의지력 계산에서 빠집니다. (FE src/lib/cardResult.ts 의 'rest')
//
// 같은 사람의 같은 날짜는 한 줄만 있습니다. (uk_rest_days_user_id_rest_date)
@Entity
@Table(
    name = "rest_days",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_rest_days_user_id_rest_date",
            columnNames = {"user_id", "rest_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestDay {

  // 줄을 구분하는 고유 번호
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 누가 쉰 날인지 (users.id)
  @Column(nullable = false)
  private Long userId;

  // 휴식으로 처리한 날짜 (하루는 새벽 4시 기준, DayRule)
  @Column(nullable = false)
  private LocalDate restDate;

  // 휴식으로 처리한 시각
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  public RestDay(Long userId, LocalDate restDate, Instant createdAt) {
    this.userId = userId;
    this.restDate = restDate;
    this.createdAt = createdAt;
  }
}
