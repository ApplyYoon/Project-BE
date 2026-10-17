package com.ithopeschool.projectbe.domain.insight;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// insights 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 인사이트가 섞이지 않습니다.
public interface InsightRepository extends JpaRepository<Insight, Long> {

  // 내 인사이트 하나. 없는 번호이거나 남의 인사이트면 빈 Optional 입니다. (반응을 저장할 때 씁니다)
  Optional<Insight> findByIdAndUserId(Long id, Long userId);

  // 내가 반응을 누른 인사이트의 수 (세 가지 중 무엇이든)
  long countByUserIdAndReactionIsNotNull(Long userId);

  // 내가 이 반응을 누른 인사이트의 수
  long countByUserIdAndReaction(Long userId, InsightResponse reaction);

  // 내가 받은 인사이트 전부, 최근에 보여 준 것이 앞. (다음 인사이트를 고를 때 종류마다의 기록을 만듭니다)
  List<Insight> findByUserIdOrderByShownDateDescIdDesc(Long userId);
}
