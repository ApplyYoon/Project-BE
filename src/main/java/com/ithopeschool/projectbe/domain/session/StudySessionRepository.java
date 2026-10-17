package com.ithopeschool.projectbe.domain.session;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// study_sessions 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 기록이 섞이지 않습니다.
public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

  // 내 기록 중에 [startedAt ~ endedAt] 과 시간이 겹치는 것이 있는지 알려줍니다.
  //
  // 두 기록이 겹친다 = "다른 기록의 시작이 내 끝보다 앞" 이고 "다른 기록의 끝이 내 시작보다 뒤"
  //   이미 있는 기록 10:00 ~ 11:00
  //   10:30 ~ 11:30  → 겹침          09:00 ~ 10:00 → 안 겹침 (끝과 시작이 딱 붙은 것은 괜찮음)
  //
  // 이름 풀이: StartedAtLessThan(새 기록의 끝) And EndedAtGreaterThan(새 기록의 시작)
  boolean existsByUserIdAndStartedAtLessThanAndEndedAtGreaterThan(
      Long userId, Instant endedAt, Instant startedAt);

  // 이 사용자의 줄(users)을 잠급니다. (for update = "내 저장이 끝날 때까지 다른 저장은 기다려")
  //
  // 왜 필요한가요? "겹치는지 보기 → 저장하기" 는 두 단계라서, 같은 요청이 동시에 두 번 오면(정지 버튼 두 번,
  // 인터넷이 끊겨 다시 보냄) 둘 다 "안 겹침" 을 보고 둘 다 저장해 버립니다.
  // 겹침을 보기 전에 이 줄을 먼저 잠그면, 같은 사람의 두 번째 요청은 첫 번째가 저장을 끝낼 때까지 기다렸다가
  // 겹침을 보게 되어 409 로 거절됩니다. 다른 사람의 요청은 잠그지 않아서 기다리지 않습니다.
  // 잠금은 @Transactional 이 끝날 때(저장이 DB 에 반영될 때) 풀립니다.
  // 값은 SQL 문자열에 붙이지 않고 :이름 자리에 넘깁니다.
  @Query(value = "select id from users where id = :userId for update", nativeQuery = true)
  Long lockUser(@Param("userId") Long userId);
}
