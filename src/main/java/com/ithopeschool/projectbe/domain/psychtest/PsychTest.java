package com.ithopeschool.projectbe.domain.psychtest;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 온보딩 심리검사 결과 테이블. 한 줄 = 검사 한 번. (이슈 #219, 기획서 6장, FE src/lib/psychTest.ts)
// 다시 하면 덮어쓰지 않고 새 줄을 더합니다. "처음의 나" 와 "지금의 나" 를 견주고, 14일 뒤 기록과 견줄 수 있게 하려는 것입니다.
//
// 척도 점수가 아니라 **문장마다의 답을 그대로** 저장합니다. (지금 검사 35개 · 옛 검사 14개) 점수는 읽을 때 PsychScore 로 계산합니다.
//   - 답을 남겨 두면 나중에 채점 규칙(경계값 5 · 3)을 바꿔도 지난 검사를 다시 채점할 수 있습니다.
//   - 데이터는 많이 남길수록 AI 가 더 객관적으로 분석할 수 있습니다. (2026-10-17 결정)
// 답은 한 줄에 여러 개라서 따로 딸린 테이블(@ElementCollection)에 담습니다. (생각 습관 테스트의 점수와 같은 방식)
//   psych_test_answers : (psych_test_id, item, answer)  문장마다 한 줄
@Entity
@Table(
    name = "psych_tests",
    indexes = @Index(name = "idx_psych_tests_user_id_id", columnList = "user_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PsychTest {

  // 검사 결과를 구분하는 고유 번호. 나중에 한 검사일수록 번호가 큽니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 검사를 한 사람(users.id)
  @Column(nullable = false)
  private Long userId;

  // 검사를 마친 시각 (서버에 저장한 시각)
  @Column(nullable = false, updatable = false)
  private Instant takenAt;

  // 문장 코드(S1 ~ C5) → 답(1 ~ 7). 35개(옛 검사는 14개) 모두 있습니다. (Service 가 확인한 뒤에 저장합니다)
  // EAGER: 결과를 꺼낼 때 답으로 점수를 늘 계산하기 때문에 한 번에 읽어 옵니다.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "psych_test_answers", joinColumns = @JoinColumn(name = "psych_test_id"))
  @MapKeyEnumerated(EnumType.STRING)
  @MapKeyColumn(name = "item", length = 4)
  @Column(name = "answer", nullable = false)
  private Map<PsychItem, Integer> answers = new EnumMap<>(PsychItem.class);

  public PsychTest(Long userId, Instant takenAt, Map<PsychItem, Integer> answers) {
    this.userId = userId;
    this.takenAt = takenAt;
    this.answers = new EnumMap<>(answers);
  }
}
