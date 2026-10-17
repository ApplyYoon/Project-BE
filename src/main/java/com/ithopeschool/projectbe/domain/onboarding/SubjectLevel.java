package com.ithopeschool.projectbe.domain.onboarding;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 과목 하나의 현재 수준. 딸린 테이블 onboarding_answer_subject_levels 의 한 줄입니다. (FE 의 SubjectLevel)
// 엔티티가 아니라 설문 답(OnboardingAnswer)에 매달린 값이라서 @Embeddable 입니다. 그래서 id 가 없습니다.
//
// 성적은 등급(1 ~ 9)과 점수(0 ~ 100) 중 하나만 있습니다. 둘 다 있거나 둘 다 없는 값은 Service 가 받지 않습니다.
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubjectLevel {

  // 어떤 과목인지 (subjects.id)
  @Column(nullable = false)
  private Long subjectId;

  // 최근 등급 (1 ~ 9). 점수로 적었으면 비어 있습니다.
  private Integer grade;

  // 최근 점수 (0 ~ 100). 등급으로 적었으면 비어 있습니다.
  private Integer score;

  // 이 과목에 대한 자신감 (1 ~ 5)
  @Column(nullable = false)
  private int confidence;

  public SubjectLevel(Long subjectId, Integer grade, Integer score, int confidence) {
    this.subjectId = subjectId;
    this.grade = grade;
    this.score = score;
    this.confidence = confidence;
  }
}
