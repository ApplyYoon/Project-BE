package com.ithopeschool.projectbe.domain.reflection;

import com.ithopeschool.projectbe.domain.care.MissReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 성찰일기 처방 테이블. 한 줄 = 처방 한 번. (이슈 #61)
// 처방은 서버가 규칙(ReflectionRule)으로 내리고, 처방 기간 동안은 매일 같은 질문 3개에 답합니다.
//
// 질문 글을 그대로 저장합니다. 질문 세트(ReflectionQuestions)를 나중에 고쳐도 이미 내린 처방의 질문은 바뀌지 않게 하려는 것입니다.
// (FE 는 답을 질문 순서대로 저장해서, 기간 중간에 질문이 바뀌면 답이 엉뚱한 질문에 붙습니다)
// 질문은 늘 3개라서 회고(reviews)처럼 칸 3개에 담고, 딸린 테이블로 빼지 않았습니다.
//
// 답(일기 원문)은 이 테이블에 없습니다. 지금은 FE 기기에만 저장합니다. (서버 저장은 일기 #60 · 암호화 #68 에서 정함)
@Entity
@Table(
    name = "reflection_prescriptions",
    indexes = @Index(name = "idx_reflection_prescriptions_user_id_id", columnList = "user_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReflectionPrescription {

  // 처방을 구분하는 고유 번호. 나중에 내린 처방일수록 번호가 큽니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 처방을 받은 사람(users.id)
  @Column(nullable = false)
  private Long userId;

  // 처방 첫날 (처방을 내린 날)
  @Column(nullable = false)
  private LocalDate startDate;

  // 처방 마지막 날 (이 날까지 포함)
  @Column(nullable = false)
  private LocalDate endDate;

  // 처방한 이유가 된 "못 채운 이유" (TIRED, EVENT, PHONE, TOO_MUCH, UNWILLING, ETC 중 하나. 아픔은 처방 안 함)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MissReason missReason;

  // 처방할 때 최근 7일 중 그 이유가 나온 날 수 (3 ~ 7)
  @Column(nullable = false)
  private int reasonDays;

  // 첫 번째 질문 (100자까지)
  @Column(nullable = false, length = 100)
  private String question1;

  // 두 번째 질문 (100자까지)
  @Column(nullable = false, length = 100)
  private String question2;

  // 세 번째 질문 (100자까지)
  @Column(nullable = false, length = 100)
  private String question3;

  // questions 는 ReflectionQuestions.of(missReason) 의 3개를 그대로 넘깁니다.
  public ReflectionPrescription(
      Long userId,
      LocalDate startDate,
      LocalDate endDate,
      MissReason missReason,
      int reasonDays,
      List<String> questions) {
    this.userId = userId;
    this.startDate = startDate;
    this.endDate = endDate;
    this.missReason = missReason;
    this.reasonDays = reasonDays;
    this.question1 = questions.get(0);
    this.question2 = questions.get(1);
    this.question3 = questions.get(2);
  }

  // 질문 3개를 순서대로 돌려줍니다.
  public List<String> getQuestions() {
    return List.of(question1, question2, question3);
  }

  // 처방 기간(일). 첫날과 마지막 날을 모두 넣어 셉니다. (10-17 ~ 10-21 → 5)
  public int getDays() {
    return (int) (endDate.toEpochDay() - startDate.toEpochDay()) + 1;
  }
}
