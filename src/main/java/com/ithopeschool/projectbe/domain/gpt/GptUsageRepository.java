package com.ithopeschool.projectbe.domain.gpt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// gpt_usages 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 기록이 섞이지 않습니다.
public interface GptUsageRepository extends JpaRepository<GptUsage, Long> {

  // 그 시각(from) 이후에 내가 부른 것 중, 이 종류들의 개수 (오늘 몇 번 불렀는지 셀 때 씁니다)
  long countByUserIdAndKindInAndCalledAtGreaterThanEqual(
      Long userId, Collection<GptUsageKind> kinds, Instant from);

  // 그 시각(from) 이후에 내가 부른 것 중, 이 종류의 기록들 (오늘 통화한 시간을 더할 때 씁니다)
  List<GptUsage> findByUserIdAndKindAndCalledAtGreaterThanEqual(
      Long userId, GptUsageKind kind, Instant from);

  // 내 통화 하나의 기록 (통화가 끝났을 때 시간과 비용을 채우려고 찾습니다)
  Optional<GptUsage> findByCallRecordIdAndUserId(Long callRecordId, Long userId);

  // 내 기록을 부른 순서(번호 순서)대로 모두 찾습니다.
  List<GptUsage> findByUserIdOrderById(Long userId);
}
