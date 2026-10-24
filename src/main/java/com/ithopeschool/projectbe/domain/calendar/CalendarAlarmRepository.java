package com.ithopeschool.projectbe.domain.calendar;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

// calendar_alarms 테이블의 창구입니다.
public interface CalendarAlarmRepository extends JpaRepository<CalendarAlarm, Long> {

  // 내 일정 알림 전부 (올릴 때 이미 있는 것과 비교합니다)
  List<CalendarAlarm> findByUserId(Long userId);

  // 알림을 보낼 차례인 일정: 아직 처리하지 않았고, from 보다 뒤 ~ to 까지(포함) 시작하는 것. 먼저 시작하는 것부터.
  // 예약 작업은 from = 지금, to = 지금 + 10분 으로 부릅니다.
  List<CalendarAlarm>
      findByHandledAtIsNullAndStartsAtAfterAndStartsAtLessThanEqualOrderByStartsAtAsc(
          Instant from, Instant to);

  // before 보다 먼저 시작한 일정을 지우고, 지운 개수를 돌려줍니다. (지난 일정 정리)
  @Transactional
  long deleteByStartsAtBefore(Instant before);

  // 내 일정 알림을 모두 지우고, 지운 개수를 돌려줍니다. (캘린더 연결 끊기)
  @Transactional
  long deleteByUserId(Long userId);
}
