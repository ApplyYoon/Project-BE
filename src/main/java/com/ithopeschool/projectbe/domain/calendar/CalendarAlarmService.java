package com.ithopeschool.projectbe.domain.calendar;

import com.ithopeschool.projectbe.domain.calendar.dto.CalendarAlarmSyncRequest;
import com.ithopeschool.projectbe.domain.calendar.dto.CalendarAlarmSyncResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 구글 캘린더 일정 알림을 올리고 지우는 로직입니다. (2026-10-24, 보내는 것은 CalendarAlarmJob)
//
// currentUserProvider.getCurrentUserId() 로 시작해서, 내 일정 알림만 바꿀 수 있습니다. 로그인하지 않은 요청은 401 입니다.
// ⚠️ 일정 제목은 로그에 찍지 않습니다.
//
// 올리기(sync)는 "덮어쓰기" 입니다. 보낸 목록이 앞으로의 내 일정 전부가 됩니다.
//   - 받은 목록에 있는 일정  → 새로 저장하거나, 이미 있으면 제목 · 시각을 고침
//                               (시각이 그대로면 "이미 보냄" 표시도 그대로라, 같은 목록을 여러 번 올려도 알림이 두 번 가지 않음)
//   - 받은 목록에 없는 일정  → 지움 (구글에서 지웠거나, 이미 지난 일정)
//   - 이미 시작했거나 8일보다 먼 일정은 받지 않고 넘어갑니다. (FE 는 7일 치를 보냄)
@Service
@RequiredArgsConstructor
public class CalendarAlarmService {

  // 앞으로 이만큼 안에 시작하는 일정만 받습니다. FE 가 보내는 7일에 하루 여유를 둔 값입니다.
  static final Duration KEEP_AHEAD = Duration.ofDays(8);

  private final CurrentUserProvider currentUserProvider;
  private final CalendarAlarmRepository calendarAlarmRepository;
  private final Clock clock;

  @Transactional
  public CalendarAlarmSyncResponse sync(CalendarAlarmSyncRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    Instant now = clock.instant();
    Instant until = now.plus(KEEP_AHEAD);

    // 1) 받을 일정만 고릅니다. 같은 id 를 두 번 보냈으면 처음 것만 씁니다. (id → 일정)
    Map<String, CalendarAlarmSyncRequest.Event> incoming = new LinkedHashMap<>();
    for (CalendarAlarmSyncRequest.Event event : request.events()) {
      boolean isUpcoming = event.startsAt().isAfter(now) && !event.startsAt().isAfter(until);
      if (isUpcoming) {
        incoming.putIfAbsent(event.eventKey(), event);
      }
    }

    // 2) 이미 있는 줄: 받은 목록에 있으면 고치고, 없으면 지웁니다.
    //    (같은 id 의 줄이 어쩌다 두 개 있으면 하나만 남깁니다)
    Set<String> keptKeys = new HashSet<>();
    List<CalendarAlarm> existing = calendarAlarmRepository.findByUserId(userId);
    for (CalendarAlarm alarm : existing) {
      CalendarAlarmSyncRequest.Event event = incoming.get(alarm.getEventKey());
      if (event == null || keptKeys.contains(alarm.getEventKey())) {
        calendarAlarmRepository.delete(alarm);
        continue;
      }
      // @Transactional 이라 바뀐 값은 끝날 때 DB 에 저장됩니다.
      alarm.update(event.title().strip(), event.startsAt());
      keptKeys.add(alarm.getEventKey());
    }

    // 3) 처음 받은 일정은 새로 저장합니다.
    for (CalendarAlarmSyncRequest.Event event : incoming.values()) {
      if (!keptKeys.contains(event.eventKey())) {
        calendarAlarmRepository.save(
            new CalendarAlarm(userId, event.eventKey(), event.title().strip(), event.startsAt()));
      }
    }
    return new CalendarAlarmSyncResponse(incoming.size());
  }

  // 내 일정 알림을 모두 지웁니다. 학생이 구글 캘린더 연결을 끊을 때 FE 가 부릅니다.
  @Transactional
  public void clear() {
    calendarAlarmRepository.deleteByUserId(currentUserProvider.getCurrentUserId());
  }
}
