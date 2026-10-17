package com.ithopeschool.projectbe.domain.psychtest;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// psych_tests 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 검사가 섞이지 않습니다.
public interface PsychTestRepository extends JpaRepository<PsychTest, Long> {

  // 내 가장 최근 검사. 아직 안 했으면 빈 Optional 입니다. (번호가 클수록 나중에 한 것)
  Optional<PsychTest> findFirstByUserIdOrderByIdDesc(Long userId);
}
