package com.ithopeschool.projectbe.domain.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ithopeschool.projectbe.domain.calendar.dto.CalendarAlarmSyncRequest;
import com.ithopeschool.projectbe.domain.calendar.dto.CalendarAlarmSyncRequest.Event;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

// 일정 올리기(덮어쓰기)를 실제 DB(H2)로 확인합니다. 로그인한 사람과 지금 시각만 가짜로 정합니다.
@DataJpaTest
class CalendarAlarmServiceTest {

  private static final Long ME = 7L;
  private static final Long OTHER = 8L;
  // 지금은 2026-10-24 18:00 (한국 시간)
  private static final Instant NOW = Instant.parse("2026-10-24T09:00:00Z");

  @Autowired private CalendarAlarmRepository repository;
  @Autowired private EntityManager entityManager;

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private CalendarAlarmService service;

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    service =
        new CalendarAlarmService(currentUserProvider, repository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static Event event(String key, String title, String startsAt) {
    return new Event(key, title, Instant.parse(startsAt));
  }

  private int sync(Event... events) {
    int saved = service.sync(new CalendarAlarmSyncRequest(List.of(events))).saved();
    entityManager.flush();
    entityManager.clear();
    return saved;
  }

  private List<CalendarAlarm> mine() {
    return repository.findByUserId(ME).stream()
        .sorted(Comparator.comparing(CalendarAlarm::getStartsAt))
        .toList();
  }

  @Test
  @DisplayName("앞으로 8일 안에 시작하는 일정만 저장하고, 지난 일정 · 너무 먼 일정 · 같은 id 의 두 번째는 뺀다")
  void savesOnlyUpcoming() {
    int saved =
        sync(
            event("a", "학원", "2026-10-24T10:00:00Z"),
            event("past", "지난 일정", "2026-10-24T08:00:00Z"),
            event("far", "먼 일정", "2026-11-10T00:00:00Z"),
            event("a", "같은 id", "2026-10-25T10:00:00Z"),
            event("b", "  밤 스터디  ", "2026-10-24T16:00:00Z"));

    assertThat(saved).isEqualTo(2);
    assertThat(mine()).extracting(CalendarAlarm::getEventKey).containsExactly("a", "b");
    assertThat(mine()).extracting(CalendarAlarm::getTitle).containsExactly("학원", "밤 스터디");
    assertThat(mine()).allMatch(alarm -> alarm.getHandledAt() == null);
  }

  @Test
  @DisplayName("다시 올리면 덮어쓴다: 목록에 없는 일정은 지우고, 있는 일정은 제목을 고친다")
  void overwrites() {
    sync(event("a", "학원", "2026-10-24T10:00:00Z"), event("b", "약속", "2026-10-24T11:00:00Z"));

    sync(event("a", "수학 학원", "2026-10-24T10:00:00Z"), event("c", "새 일정", "2026-10-25T01:00:00Z"));

    assertThat(mine()).extracting(CalendarAlarm::getEventKey).containsExactly("a", "c");
    assertThat(mine().getFirst().getTitle()).isEqualTo("수학 학원");
  }

  @Test
  @DisplayName("이미 알린 일정을 같은 시각으로 다시 올리면 '처리함' 이 남아서 두 번 알리지 않는다")
  void keepsHandledWhenSameTime() {
    sync(event("a", "학원", "2026-10-24T09:05:00Z"));
    CalendarAlarm alarm = mine().getFirst();
    alarm.markHandled(NOW);
    repository.saveAndFlush(alarm);
    entityManager.clear();

    sync(event("a", "학원", "2026-10-24T09:05:00Z"));

    assertThat(mine().getFirst().getHandledAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("구글에서 일정을 옮겨 시작 시각이 바뀌면 '처리함' 을 지워서 새 시각에 다시 알린다")
  void resetsHandledWhenMoved() {
    sync(event("a", "학원", "2026-10-24T09:05:00Z"));
    CalendarAlarm alarm = mine().getFirst();
    alarm.markHandled(NOW);
    repository.saveAndFlush(alarm);
    entityManager.clear();

    sync(event("a", "학원", "2026-10-24T12:00:00Z"));

    assertThat(mine().getFirst().getStartsAt()).isEqualTo(Instant.parse("2026-10-24T12:00:00Z"));
    assertThat(mine().getFirst().getHandledAt()).isNull();
  }

  @Test
  @DisplayName("빈 목록을 올리거나 연결을 끊으면 내 일정만 지우고 다른 사람 것은 그대로 둔다")
  void clearsOnlyMine() {
    repository.saveAndFlush(new CalendarAlarm(OTHER, "x", "남의 일정", NOW.plusSeconds(3600)));
    sync(event("a", "학원", "2026-10-24T10:00:00Z"));

    assertThat(sync()).isZero();
    assertThat(mine()).isEmpty();

    sync(event("a", "학원", "2026-10-24T10:00:00Z"));
    service.clear();
    entityManager.flush();
    entityManager.clear();

    assertThat(mine()).isEmpty();
    assertThat(repository.findByUserId(OTHER)).hasSize(1);
  }

  @Test
  @DisplayName("한 사람의 같은 일정 id 는 한 줄만 저장된다 (동시에 두 번 올려도 알림이 두 번 가지 않게)")
  void oneRowPerEvent() {
    repository.saveAndFlush(new CalendarAlarm(ME, "a", "학원", NOW.plusSeconds(300)));

    assertThatThrownBy(
            () -> repository.saveAndFlush(new CalendarAlarm(ME, "a", "학원", NOW.plusSeconds(300))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
