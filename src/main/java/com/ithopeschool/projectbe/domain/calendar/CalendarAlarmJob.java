package com.ithopeschool.projectbe.domain.calendar;

import com.ithopeschool.projectbe.domain.job.JobContext;
import com.ithopeschool.projectbe.domain.job.JobResultStore;
import com.ithopeschool.projectbe.domain.job.ScheduledJob;
import com.ithopeschool.projectbe.domain.push.PushSubscriptionService;
import com.ithopeschool.projectbe.domain.push.WebPushSender;
import com.ithopeschool.projectbe.domain.safety.QuietHours;
import com.ithopeschool.projectbe.domain.safety.QuietPolicy;
import com.ithopeschool.projectbe.domain.settings.QuietHoursSettings;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// 구글 캘린더 일정이 곧 시작하면 웹 푸시로 알리는 예약 작업입니다. (2026-10-24, docs/JOBS.md "calendar-alarm")
// cron-job.org 가 POST /api/v1/jobs/calendar-alarm 을 10분마다(:00 · :10 · :20 …) 부릅니다.
//
// 한 번 돌 때 "지금부터 10분 안에 시작하는 일정" 중 아직 처리하지 않은 것을 알립니다.
//   - 정각 · 30분처럼 10분 단위로 시작하는 일정은 딱 10분 전에 알림이 갑니다. (19:00 일정 → 18:50 실행)
//   - 19:15 처럼 어중간한 일정은 5분 전쯤 갑니다. (19:10 실행)
//   - 실행 한 번이 다음 10분을 맡아서 빠지는 일정은 없습니다. 실행을 놓치면 그 10분 안의 알림은 가지 않습니다.
//
// 일정마다 아래를 차례로 합니다.
//   1. "처리함" 을 먼저 적습니다. 푸시가 실패하거나 작업이 두 번 불려도 같은 일정을 또 보내지 않게 하려는 것입니다.
//   2. 그 학생의 연락 금지 시간이면 보내지 않습니다. 지난 일정을 아침에 알리면 엉뚱해서 버립니다(DROP). (#57)
//   3. 그 학생의 모든 기기에 푸시를 보냅니다. 누르면 홈(달력)이 열립니다.
// 끝으로 시작한 지 하루가 지난 일정을 지웁니다. 일정 제목을 필요 이상으로 오래 들고 있지 않으려는 것입니다.
//
// 앱의 알림 목록(messages)에는 남기지 않습니다. messages 는 "AI 가 보낸 메시지" 만 담는 곳입니다.
// ⚠️ 일정 제목은 로그에 찍지 않습니다. 몇 개를 보냈는지 숫자만 남깁니다.
@Component
@RequiredArgsConstructor
public class CalendarAlarmJob implements ScheduledJob {

  public static final String NAME = "calendar-alarm";

  // 일정 시작 몇 분 전부터 알릴지. 작업을 부르는 간격(10분)과 같게 둡니다.
  static final Duration NOTICE_BEFORE = Duration.ofMinutes(10);
  // 시작한 뒤 이만큼 지난 일정은 지웁니다.
  static final Duration KEEP_AFTER = Duration.ofDays(1);
  // 누르면 열 앱 안의 주소 (FE 의 홈 = 달력이 있는 곳)
  static final String URL = "/";

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
  private static final Logger log = LoggerFactory.getLogger(CalendarAlarmJob.class);

  private final CalendarAlarmRepository calendarAlarmRepository;
  private final PushSubscriptionService pushSubscriptionService;
  private final QuietHoursSettings quietHoursSettings;
  private final JobResultStore resultStore;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void run(JobContext context) {
    Instant now = context.startedAt();
    int sent = 0;
    int skipped = 0;

    List<CalendarAlarm> dueAlarms =
        calendarAlarmRepository
            .findByHandledAtIsNullAndStartsAtAfterAndStartsAtLessThanEqualOrderByStartsAtAsc(
                now, now.plus(NOTICE_BEFORE));
    for (CalendarAlarm alarm : dueAlarms) {
      // 1) 처리함을 먼저 저장
      alarm.markHandled(now);
      calendarAlarmRepository.save(alarm);

      // 2) 연락 금지 시간
      QuietHours.Decision decision =
          QuietHours.decide(now, quietHoursSettings.windowOf(alarm.getUserId()), QuietPolicy.DROP);
      if (decision.action() != QuietHours.Action.SEND) {
        skipped++;
        continue;
      }

      // 3) 푸시
      pushSubscriptionService.sendToUser(alarm.getUserId(), payloadOf(alarm, now));
      sent++;
    }

    // 지난 일정 정리
    calendarAlarmRepository.deleteByStartsAtBefore(now.minus(KEEP_AFTER));

    // 10분마다 돌아서 대부분 0개입니다. 그날 한 칸에 마지막 결과만 덮어씁니다.
    resultStore.save(NAME, context.date(), "보냄 " + sent + "개 · 건너뜀 " + skipped + "개");
    if (sent + skipped > 0) {
      log.info("[캘린더 알림] 보냄 {}개 · 건너뜀 {}개", sent, skipped);
    }
  }

  // 알림 글을 만듭니다. 남은 분은 올려서 셉니다. (9분 30초 남음 → 10분)
  //
  //   18:50 에 19:00 '학원'  →  제목 "📅 학원", 글 "10분 뒤 19:00 에 시작해요"
  static WebPushSender.Payload payloadOf(CalendarAlarm alarm, Instant now) {
    long secondsLeft = Duration.between(now, alarm.getStartsAt()).getSeconds();
    long minutesLeft = Math.max(1, (secondsLeft + 59) / 60);
    String clock = CLOCK.format(alarm.getStartsAt().atZone(SEOUL));
    return new WebPushSender.Payload(
        "📅 " + alarm.getTitle(), minutesLeft + "분 뒤 " + clock + " 에 시작해요", URL);
  }
}
