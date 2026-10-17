package com.ithopeschool.projectbe.domain.onboarding;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// onboarding_answers 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 설문이 섞이지 않습니다.
// 같은 밀리초에 두 줄이 생기면 답한 시각이 같아서, 그때는 번호(id)가 큰 쪽을 나중 것으로 봅니다.
public interface OnboardingAnswerRepository extends JpaRepository<OnboardingAnswer, Long> {

  // 지금 내 설문 (답한 시각이 가장 늦은 줄). 아직 안 했으면 빈 Optional 입니다.
  Optional<OnboardingAnswer> findFirstByUserIdOrderByAnsweredAtDescIdDesc(Long userId);

  // 내 설문 전부, 처음 것부터. (처음 답한 자기 인식을 찾을 때 씁니다)
  List<OnboardingAnswer> findByUserIdOrderByAnsweredAtAscIdAsc(Long userId);
}
