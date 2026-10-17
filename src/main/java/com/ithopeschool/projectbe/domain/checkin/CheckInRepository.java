package com.ithopeschool.projectbe.domain.checkin;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// checkins 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 체크인이 섞이지 않습니다.
public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

  // 내 그날의 체크인. 아직 안 했으면 빈 Optional 입니다. (덮어쓸지 새로 만들지 정할 때 씁니다)
  Optional<CheckIn> findByUserIdAndCheckInDate(Long userId, LocalDate checkInDate);

  // 내 from ~ to (둘 다 포함) 사이에 체크인한 날들, 날짜 순서로.
  // 체크인을 안 한 날은 목록에 없습니다. ("모름" 이지 "0" 이 아닙니다)
  List<CheckIn> findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
      Long userId, LocalDate from, LocalDate to);

  // 내 체크인을 모두 지우고, 지운 줄 수를 돌려줍니다. (설정의 "모든 기록 지우기")
  // 부르는 즉시 DB 에서 지웁니다. 사용자 번호는 SQL 글자에 붙이지 않고 파라미터(:userId)로 넘깁니다.
  @Modifying
  @Query("delete from CheckIn c where c.userId = :userId")
  int deleteAllOfUser(@Param("userId") Long userId);
}
