package com.ithopeschool.projectbe.domain.thinkingtest;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// thinking_tests 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 결과가 섞이지 않습니다.
public interface ThinkingTestRepository extends JpaRepository<ThinkingTest, Long> {

  // 내 가장 최근 테스트 결과. 아직 안 했으면 빈 Optional 입니다. (번호가 클수록 나중에 한 것)
  Optional<ThinkingTest> findFirstByUserIdOrderByIdDesc(Long userId);

  // 내 테스트 결과 전부, 최근 것부터. (처음과 지금을 견줄 때 씁니다)
  List<ThinkingTest> findByUserIdOrderByIdDesc(Long userId);
}
