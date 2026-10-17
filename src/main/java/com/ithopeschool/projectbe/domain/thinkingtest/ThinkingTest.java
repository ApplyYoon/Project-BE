package com.ithopeschool.projectbe.domain.thinkingtest;

import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.bias.Weakness;
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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 생각 습관 테스트 결과 테이블. 한 줄 = 테스트 한 번. (이슈 #35, FE src/lib/thinkingTest.ts)
// 다시 하면 덮어쓰지 않고 새 줄을 더합니다. 그래야 "처음의 나" 와 "지금의 나" 를 견줄 수 있습니다.
//
// 문장별 답은 저장하지 않고, 습관 8개의 평균(1 ~ 7)만 저장합니다.
// 문장은 앞으로 바뀔 수 있어서(선생님 말씀), 문장이 바뀌어도 이 테이블은 그대로 쓸 수 있게 하려는 것입니다.
//
// 습관 점수와 약점은 한 줄에 여러 개라서 따로 딸린 테이블(@ElementCollection)에 담습니다.
//   thinking_test_scores     : (thinking_test_id, habit, average)  습관마다 한 줄
//   thinking_test_weaknesses : (thinking_test_id, weakness)         고른 약점마다 한 줄
// "기타" 에 직접 쓴 약점 글은 받지 않아서 저장하지 않습니다.
@Entity
@Table(
    name = "thinking_tests",
    indexes = @Index(name = "idx_thinking_tests_user_id_id", columnList = "user_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ThinkingTest {

  // 테스트 결과를 구분하는 고유 번호. 나중에 한 테스트일수록 번호가 큽니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 테스트를 한 사람(users.id)
  @Column(nullable = false)
  private Long userId;

  // 테스트를 마친 시각 (서버에 저장한 시각)
  @Column(nullable = false, updatable = false)
  private Instant takenAt;

  // 어떤 모양의 테스트였는지. FE 의 TEST_VERSION 과 같습니다. (2 = 리커트 7점 척도)
  @Column(nullable = false)
  private int testVersion;

  // 습관마다의 평균 (1 ~ 7, 역문항은 뒤집어 센 값). 높을수록 그 생각을 자주 하는 편입니다.
  // EAGER: 결과를 꺼낼 때 점수를 늘 같이 쓰기 때문에 한 번에 읽어 옵니다.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "thinking_test_scores",
      joinColumns = @JoinColumn(name = "thinking_test_id"))
  @MapKeyEnumerated(EnumType.STRING)
  @MapKeyColumn(name = "habit", length = 30)
  @Column(name = "average", nullable = false)
  private Map<CognitiveBias, Double> scores = new EnumMap<>(CognitiveBias.class);

  // 테스트 마지막에 고른 "내가 생각하는 나의 약점" 칩들
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "thinking_test_weaknesses",
      joinColumns = @JoinColumn(name = "thinking_test_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "weakness", length = 20, nullable = false)
  private Set<Weakness> weaknesses = EnumSet.noneOf(Weakness.class);

  public ThinkingTest(
      Long userId,
      Instant takenAt,
      int testVersion,
      Map<CognitiveBias, Double> scores,
      Set<Weakness> weaknesses) {
    this.userId = userId;
    this.takenAt = takenAt;
    this.testVersion = testVersion;
    this.scores = new EnumMap<>(scores);
    this.weaknesses =
        weaknesses.isEmpty() ? EnumSet.noneOf(Weakness.class) : EnumSet.copyOf(weaknesses);
  }
}
