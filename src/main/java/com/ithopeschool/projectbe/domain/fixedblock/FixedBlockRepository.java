package com.ithopeschool.projectbe.domain.fixedblock;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// fixed_blocks 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 일정이 섞이지 않습니다.
public interface FixedBlockRepository extends JpaRepository<FixedBlock, Long> {

  // 내 고정 일정 전부 (만든 순서)
  List<FixedBlock> findByUserIdOrderById(Long userId);

  // 내 고정 일정 중 그 번호. 없는 번호이거나 남의 일정이면 빈 Optional 입니다.
  Optional<FixedBlock> findByIdAndUserId(Long id, Long userId);
}
