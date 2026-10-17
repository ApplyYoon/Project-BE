package com.ithopeschool.projectbe.domain.session;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// study_sessions 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 기록이 섞이지 않습니다.
public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

  // 추천에는 내 과목의 목표가 있는 공부 기록 세 개만 필요합니다. 같은 시작 시각은 번호로 정렬합니다.
  List<StudySession>
      findTop3ByUserIdAndSubjectIdAndKindAndTargetMinutesGreaterThanOrderByStartedAtDescIdDesc(
          Long userId,
          Long subjectId,
          com.ithopeschool.projectbe.domain.plan.PlanKind kind,
          int targetMinutes);

  // 내 기록 중에 [startedAt ~ endedAt] 과 시간이 겹치는 것이 있는지 알려줍니다.
  //
  // 두 기록이 겹친다 = "다른 기록의 시작이 내 끝보다 앞" 이고 "다른 기록의 끝이 내 시작보다 뒤"
  //   이미 있는 기록 10:00 ~ 11:00
  //   10:30 ~ 11:30  → 겹침          09:00 ~ 10:00 → 안 겹침 (끝과 시작이 딱 붙은 것은 괜찮음)
  //
  // 이름 풀이: StartedAtLessThan(새 기록의 끝) And EndedAtGreaterThan(새 기록의 시작)
  boolean existsByUserIdAndStartedAtLessThanAndEndedAtGreaterThan(
      Long userId, Instant endedAt, Instant startedAt);

  // 내 기록 중에 [periodStart, periodEnd) 사이에 "시작한" 것을 시작 시각이 빠른 순서로 찾습니다.
  // periodEnd 는 포함하지 않습니다. (하루의 끝과 다음 날의 시작이 같은 순간이라, 포함하면 두 날에 다 잡힙니다)
  //
  // 끝난 시각이 아니라 시작한 시각(startedAt)만 봅니다. 날짜별로 센다는 것은 "시작한 날" 로 센다는 뜻이라,
  // 04:00 를 걸친 기록(03:30 ~ 04:30)도 시작한 날의 기록 하나로 잡힙니다. (docs/API.md 2-4)
  // (user_id, started_at) 색인 덕분에 기록이 많아져도 빨리 찾습니다.
  // 값은 SQL 문자열에 붙이지 않고 :이름 자리에 넘깁니다.
  @Query(
      "select s from StudySession s"
          + " where s.userId = :userId and s.startedAt >= :periodStart and s.startedAt < :periodEnd"
          + " order by s.startedAt asc, s.id asc")
  List<StudySession> findStartedBetween(
      @Param("userId") Long userId,
      @Param("periodStart") Instant periodStart,
      @Param("periodEnd") Instant periodEnd);

  // 내 기록 중 가장 먼저 시작한 것 하나. 기록이 없으면 빈 Optional 입니다.
  // 첫 리포트 카운트다운의 "첫 기록 날짜" 를 찾을 때 씁니다. (InsightService, 이슈 #35)
  Optional<StudySession> findFirstByUserIdOrderByStartedAtAscIdAsc(Long userId);

  // 내 기록 중에 이 계획들로 켠 타이머의 기록을 모두 찾습니다. 날짜와 종류(공부·노는)는 가리지 않습니다.
  // 계획 줄의 "실제 / 목표" 를 계산할 때 씁니다. 계획이 없으면(planIds 가 비면) 부르지 않습니다.
  List<StudySession> findByUserIdAndPlanIdIn(Long userId, Collection<Long> planIds);

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
