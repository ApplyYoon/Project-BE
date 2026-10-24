package com.ithopeschool.projectbe.domain.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.job.JobContext;
import com.ithopeschool.projectbe.domain.job.JobResultStore;
import com.ithopeschool.projectbe.domain.push.PushSubscriptionService;
import com.ithopeschool.projectbe.domain.push.WebPushSender;
import com.ithopeschool.projectbe.domain.safety.QuietHours;
import com.ithopeschool.projectbe.domain.settings.QuietHoursSettings;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

// "구글 캘린더 알림" 예약 작업을 실제 DB(H2)로 확인합니다. 푸시와 연락 금지 시간 설정만 가짜로 바꿉니다.
// 시각은 한국 시간 = UTC + 9 시간입니다. (18:50 한국 시간 = 09:50Z)
@DataJpaTest
class CalendarAlarmJobTest {

  private static final Long STUDENT = 7L;
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);
  // 시험하는 시각(18:00 ~ 19:20)과 겹치지 않는 연락 금지 시간 (02:00 ~ 03:00)
  private static final QuietHours.Window NO_QUIET =
      new QuietHours.Window(LocalTime.of(2, 0), LocalTime.of(3, 0));

  @Autowired private CalendarAlarmRepository repository;
  @Autowired private EntityManager entityManager;

  private final PushSubscriptionService pushService = mock(PushSubscriptionService.class);
  private final QuietHoursSettings quietHoursSettings = mock(QuietHoursSettings.class);
  private CalendarAlarmJob job;

  @BeforeEach
  void setUp() {
    given(quietHoursSettings.windowOf(anyLong())).willReturn(NO_QUIET);
    job = new CalendarAlarmJob(repository, pushService, quietHoursSettings, new JobResultStore());
  }

  // 한국 시간 "HH:mm" 의 순간
  private static Instant korean(String time) {
    return TODAY.atTime(LocalTime.parse(time)).minusHours(9).toInstant(ZoneOffset.UTC);
  }

  // 한국 시간 "HH:mm" 에 작업이 돈 것으로 합니다.
  private void runAt(String koreanTime) {
    job.run(new JobContext(TODAY, korean(koreanTime)));
    entityManager.flush();
    entityManager.clear();
  }

  private void saveEvent(String key, String title, String koreanStart) {
    repository.saveAndFlush(new CalendarAlarm(STUDENT, key, title, korean(koreanStart)));
  }

  @Test
  @DisplayName("10분 안에 시작하는 일정만 알린다: 19:00 일정은 18:50 실행에서 '10분 뒤' 로 간다")
  void sendsTenMinutesBefore() {
    saveEvent("a", "학원", "19:00");

    runAt("18:40");
    verify(pushService, never()).sendToUser(anyLong(), any());

    runAt("18:50");
    ArgumentCaptor<WebPushSender.Payload> payload =
        ArgumentCaptor.forClass(WebPushSender.Payload.class);
    verify(pushService).sendToUser(eq(STUDENT), payload.capture());
    assertThat(payload.getValue().title()).isEqualTo("📅 학원");
    assertThat(payload.getValue().body()).isEqualTo("10분 뒤 19:00 에 시작해요");
    assertThat(payload.getValue().url()).isEqualTo("/");
  }

  @Test
  @DisplayName("10분마다 불러도 한 일정은 한 번만 알리고, 어중간한 19:15 일정은 19:10 실행에서 '5분 뒤' 로 간다")
  void sendsOnce() {
    saveEvent("a", "약속", "19:15");

    runAt("19:00");
    runAt("19:10");
    runAt("19:10");
    runAt("19:20");

    ArgumentCaptor<WebPushSender.Payload> payload =
        ArgumentCaptor.forClass(WebPushSender.Payload.class);
    verify(pushService, times(1)).sendToUser(eq(STUDENT), payload.capture());
    assertThat(payload.getValue().body()).isEqualTo("5분 뒤 19:15 에 시작해요");
  }

  @Test
  @DisplayName("이미 시작한 일정은 늦게라도 알리지 않는다")
  void skipsStarted() {
    saveEvent("a", "학원", "19:00");

    runAt("19:00");

    verify(pushService, never()).sendToUser(anyLong(), any());
  }

  @Test
  @DisplayName("연락 금지 시간이면 보내지 않고 '처리함' 으로 남겨서 나중에도 보내지 않는다")
  void dropsInQuietHours() {
    given(quietHoursSettings.windowOf(STUDENT))
        .willReturn(new QuietHours.Window(LocalTime.of(23, 0), LocalTime.of(7, 0)));
    saveEvent("a", "아침 운동", "06:55");

    runAt("06:50");

    verify(pushService, never()).sendToUser(anyLong(), any());
    assertThat(repository.findByUserId(STUDENT).getFirst().getHandledAt())
        .isEqualTo(korean("06:50"));
  }

  @Test
  @DisplayName("시작한 지 하루가 지난 일정은 지운다")
  void removesOldEvents() {
    repository.saveAndFlush(
        new CalendarAlarm(STUDENT, "old", "지난 일정", korean("18:00").minusSeconds(25 * 3600)));
    saveEvent("new", "학원", "19:00");

    runAt("18:00");

    assertThat(repository.findByUserId(STUDENT))
        .extracting(CalendarAlarm::getEventKey)
        .containsExactly("new");
  }
}
