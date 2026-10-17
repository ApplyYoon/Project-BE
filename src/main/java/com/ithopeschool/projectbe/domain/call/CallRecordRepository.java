package com.ithopeschool.projectbe.domain.call;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// call_records 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 통화 기록이 섞이지 않습니다.
public interface CallRecordRepository extends JpaRepository<CallRecord, Long> {

  // 내 통화 중 from 날짜부터의 것을 최근 순서(늦게 시작한 것부터)로 찾습니다. 없으면 빈 목록입니다.
  // 시작 시각이 같으면 번호가 큰 것(나중에 만든 것)이 먼저입니다.
  List<CallRecord> findByUserIdAndCallDateGreaterThanEqualOrderByStartedAtDescIdDesc(
      Long userId, LocalDate from);

  // 내 통화 중에서 번호로 하나 찾습니다. 없는 번호이거나 남의 통화면 빈 Optional 입니다.
  Optional<CallRecord> findByIdAndUserId(Long id, Long userId);
}
