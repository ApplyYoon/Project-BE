package com.ithopeschool.projectbe.domain.consent;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// consent_records 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 동의 기록이 섞이지 않습니다.
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {

  // 내 가장 최근 동의 기록. 한 번도 동의한 적이 없으면 빈 Optional 입니다. (번호가 클수록 나중에 한 동의)
  Optional<ConsentRecord> findFirstByUserIdOrderByIdDesc(Long userId);
}
