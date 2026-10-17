package com.ithopeschool.projectbe.domain.dailysignal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// daily_signals 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 데이터가 섞이지 않습니다.
public interface DailySignalRepository extends JpaRepository<DailySignal, Long> {

  // 내 그날의 데이터. 아직 안 보냈으면 빈 Optional 입니다. (덮어쓸지 새로 만들지 정할 때 씁니다)
  Optional<DailySignal> findByUserIdAndSignalDate(Long userId, LocalDate signalDate);

  // 내 from ~ to (둘 다 포함) 사이에 데이터가 있는 날들, 날짜 순서로.
  // 분석(#38)은 이 목록에 있는 날만 씁니다. 목록에 없는 날은 "데이터 없음" 이지 "0" 이 아닙니다.
  List<DailySignal> findByUserIdAndSignalDateBetweenOrderBySignalDateAsc(
      Long userId, LocalDate from, LocalDate to);
}
