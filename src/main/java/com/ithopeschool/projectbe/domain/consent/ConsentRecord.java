package com.ithopeschool.projectbe.domain.consent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 동의 기록 테이블. 한 줄 = 동의 한 번. (이슈 #71) — 학생이 설계
//
// 동의할 때마다 새 줄을 더해서 이력을 남깁니다. 가입할 때 한 줄이 생기고, 동의 문구가 바뀌어 다시 동의하면 한 줄이 더 생깁니다.
// 한 번 쓴 줄은 바꾸지 않아서(updatable = false) 값을 바꾸는 메서드가 없습니다.
//   - 지금 동의 상태 = 이 사람의 줄 중 번호(id)가 가장 큰 줄
//   - 필수 동의(이용약관 · 개인정보 수집·이용)에 동의하지 않으면 가입이 안 되므로, "줄이 있다" 는 것이 곧 필수 동의를 했다는 뜻입니다.
//   - 일기·감정 분석 동의(선택)는 바꿀 수 있는 설정이라 accounts.diary_analysis_agreed 에 따로 있습니다.
//
// 이 테이블에는 이름·전화번호·생년월일을 넣지 않습니다. (CLAUDE.md 4장 — 분석에 쓰지 않는 정보는 모으지 않기)
@Entity
@Table(
    name = "consent_records",
    indexes = @Index(name = "idx_consent_records_user_id_id", columnList = "user_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentRecord {

  // 동의 기록을 구분하는 고유 번호. 나중에 한 동의일수록 번호가 큽니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 누가 동의했는지 (users.id)
  @Column(nullable = false)
  private Long userId;

  // 동의한 시각 (서버가 기록을 저장한 시각)
  @Column(nullable = false, updatable = false)
  private Instant agreedAt;

  // 동의한 문구의 버전. FE 가 보여 준 문구의 버전입니다. (ConsentPolicy.CURRENT_VERSION 참고)
  @Column(nullable = false, updatable = false, length = 20)
  private String textVersion;

  // 만 14세 이상이라고 확인했는지. true 면 확인했다는 뜻입니다.
  // 비어 있으면(null) "확인받은 적 없다" 는 뜻입니다. 이 확인을 받기 전에 가입한 계정의 옛 동의가 그렇습니다.
  @Column(updatable = false)
  private Boolean ageConfirmedOver14;

  public ConsentRecord(
      Long userId, Instant agreedAt, String textVersion, Boolean ageConfirmedOver14) {
    this.userId = userId;
    this.agreedAt = agreedAt;
    this.textVersion = textVersion;
    this.ageConfirmedOver14 = ageConfirmedOver14;
  }
}
