package com.ithopeschool.projectbe.domain.insight;

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
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 인사이트 테이블. 한 줄 = 학생에게 보여 준 인사이트 하나. (이슈 #43 · #41, 담당 학생이 설계)
// 반응 버튼을 누르면 이 줄의 반응 칸에 적고, 다시 누르면 바꿉니다. 인사이트 하나에 반응은 하나뿐입니다.
//
// 반응만 비어 있어도 됩니다. (누르기 전에는 모르는 값입니다) 나머지 칸은 보여 주는 순간에 모두 정해집니다.
//
// 근거 날짜는 한 줄에 여러 개라서 따로 딸린 테이블(@ElementCollection)에 담습니다. (생각 습관 테스트의 약점과 같은 방식)
//   insight_evidence_dates : (insight_id, evidence_date)  근거 날짜마다 한 줄
//   날짜를 글자로 이어 붙이지 않고 날짜로 저장해서, 날짜로 찾기 쉽게 했습니다. (담당 학생이 고름)
//
// 다음에 인사이트를 고를 때(#41 InsightPicker) "언제 보여 줬나 · 무엇을 눌렀나" 를 이 테이블에서 꺼냅니다.
@Entity
@Table(
    name = "insights",
    indexes = @Index(name = "idx_insights_user_id_shown_date", columnList = "user_id, shown_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Insight {

  // 줄을 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 인사이트의 주인(users.id). 다른 사람의 인사이트에 반응하지 못하게 하는 기준입니다.
  @Column(nullable = false)
  private Long userId;

  // 카드에 보여 준 한 문장
  @Column(nullable = false, length = 200)
  private String sentence;

  // 인사이트의 종류. 같은 발견이면 같은 값입니다. (예: morning-vs-night) key 는 DB 예약어라 insightKey 라고 지었습니다
  @Column(nullable = false, length = 50)
  private String insightKey;

  // 비교 조건 중 하나라도 5일 미만이라 "가설" 로 보여 줬는지
  @Column(nullable = false)
  private boolean hypothesis;

  // 보여 준 날 (하루의 기준 새벽 4시)
  @Column(nullable = false)
  private LocalDate shownDate;

  // 학생이 누른 반응. 누르기 전에는 비어 있습니다.
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private InsightResponse reaction;

  // 이 결론의 근거가 된 날짜들. 최소 하나입니다.
  // EAGER: 인사이트를 꺼낼 때 근거 날짜를 늘 같이 쓰기 때문에 한 번에 읽어 옵니다.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "insight_evidence_dates", joinColumns = @JoinColumn(name = "insight_id"))
  @Column(name = "evidence_date", nullable = false)
  private Set<LocalDate> evidenceDates = new HashSet<>();

  public Insight(
      Long userId,
      String sentence,
      String insightKey,
      boolean hypothesis,
      LocalDate shownDate,
      List<LocalDate> evidenceDates) {
    // 근거 날짜가 없는 인사이트는 보여 주지 않기로 했습니다. (#41) 여기까지 왔다면 고르는 코드가 잘못된 것입니다.
    if (evidenceDates == null || evidenceDates.isEmpty()) {
      throw new IllegalArgumentException("근거 날짜가 없는 인사이트는 저장하지 않습니다");
    }
    this.userId = userId;
    this.sentence = sentence;
    this.insightKey = insightKey;
    this.hypothesis = hypothesis;
    this.shownDate = shownDate;
    this.evidenceDates.addAll(evidenceDates);
  }

  // 반응을 적습니다. 이미 눌렀으면 새 반응으로 바꿉니다.
  public void react(InsightResponse reaction) {
    this.reaction = reaction;
  }
}
