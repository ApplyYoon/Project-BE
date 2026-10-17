package com.ithopeschool.projectbe.domain.restday;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// rest_days 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 휴식이 섞이지 않습니다.
public interface RestDayRepository extends JpaRepository<RestDay, Long> {

  // 그 날짜를 이미 휴식으로 처리했는지 알려 줍니다.
  boolean existsByUserIdAndRestDate(Long userId, LocalDate restDate);

  // from ~ to (두 날짜 모두 포함) 에 휴식으로 처리한 날 수. (이번 달 몇 번 썼는지)
  long countByUserIdAndRestDateBetween(Long userId, LocalDate from, LocalDate to);

  // from ~ to (두 날짜 모두 포함) 의 내 휴식 날을 날짜 순서로 찾습니다. 없으면 빈 목록입니다.
  List<RestDay> findByUserIdAndRestDateBetweenOrderByRestDate(
      Long userId, LocalDate from, LocalDate to);
}
