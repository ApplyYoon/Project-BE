package com.ithopeschool.projectbe.domain.push;

import com.ithopeschool.projectbe.domain.call.CallRing;
import com.ithopeschool.projectbe.domain.gpt.GptUsageGuard;
import com.ithopeschool.projectbe.domain.job.JobContext;
import com.ithopeschool.projectbe.domain.job.JobResultStore;
import com.ithopeschool.projectbe.domain.job.ScheduledJob;
import com.ithopeschool.projectbe.domain.message.Message;
import com.ithopeschool.projectbe.domain.message.MessageKind;
import com.ithopeschool.projectbe.domain.message.MessageRepository;
import com.ithopeschool.projectbe.domain.message.MessageService;
import com.ithopeschool.projectbe.domain.safety.QuietHours;
import com.ithopeschool.projectbe.domain.settings.CareSetting;
import com.ithopeschool.projectbe.domain.settings.CareSettingRepository;
import com.ithopeschool.projectbe.domain.settings.QuietHoursSettings;
import java.time.Instant;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// 학생이 정해 둔 AI 전화 시간이 되면 "전화 스타일 알림" 을 웹 푸시로 보내는 예약 작업입니다. (이슈 #32, docs/JOBS.md "call-ring")
// FE 토론(FE#29)의 결론 "전화 스타일 알림 → 누르면 앱 안에서 음성 대화" 의 서버 쪽입니다.
// 앱이 열려 있으면 FE 가 같은 규칙(shouldRingNow)으로 직접 울리고, 앱이 꺼져 있을 때는 이 작업이 알립니다.
// cron-job.org 가 POST /api/v1/jobs/call-ring 을 5분마다 부릅니다.
//
// 알림을 켠 학생마다 아래를 차례로 따지고, 하나라도 걸리면 그 학생은 건너뜁니다.
//   1. 지금 울릴 때인지 (CallRing.shouldRingNow)
//      - 전화 시간: 관리 설정(#22)의 callTime. 설정을 안 바꿨으면 기본 21:00
//      - 정한 시각 ~ 5분 사이이고, 최근 12시간 안에 울린 적이 없을 때만
//      - "마지막으로 울린 시각" 은 알림 목록(messages)의 가장 최근 전화 알림(CALL)의 보낸 시각입니다.
//   2. 그 학생의 연락 금지 시간이면 보내지 않습니다. 전화 알림은 버릴 메시지(DROP)라 나중에 보내지도 않습니다. (#57)
//   3. 오늘 걸 수 있는 통화가 남아 있지 않으면(#33 하루 통화 한도) 보내지 않습니다. 눌러도 통화를 못 하기 때문입니다.
//      오늘 끝낸 공부가 없어도 울립니다. 통화를 여는 공부 조건은 2026-10-22 에 뺐습니다. (CheckCall)
//   4. 앱의 알림 목록에 먼저 남기고, 그 학생의 모든 기기에 푸시를 보냅니다. 누르면 통화 화면(/call)이 열립니다.
//
// 위기 표현이 감지된 뒤 7일은 통화가 위로하는 통화가 됩니다(#56). 그래서 "오늘의 작전" 과 달리 전화 알림은 멈추지 않습니다.
// 알림 문장도 공부를 재촉하지 않는 말로 정했습니다.
//
// ⚠️ 알림 내용은 로그에 찍지 않습니다. 몇 명에게 보냈는지 숫자만 남깁니다.
@Component
@RequiredArgsConstructor
public class CallRingJob implements ScheduledJob {

  public static final String NAME = "call-ring";

  // 알림 제목과 글. 공부를 재촉하지 않고, AI 임을 숨기지 않습니다.
  static final String TITLE = "AI 전화 시간이에요 📞";
  static final String BODY = "눌러서 오늘 하루 이야기를 나눠 봐요.";
  // 누르면 열 앱 안의 주소 (FE 의 통화 화면)
  static final String URL = "/call";

  private static final Logger log = LoggerFactory.getLogger(CallRingJob.class);

  private final PushSubscriptionRepository pushSubscriptionRepository;
  private final PushSubscriptionService pushSubscriptionService;
  private final MessageRepository messageRepository;
  private final MessageService messageService;
  private final CareSettingRepository careSettingRepository;
  private final QuietHoursSettings quietHoursSettings;
  private final GptUsageGuard gptUsageGuard;
  private final JobResultStore resultStore;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void run(JobContext context) {
    int rangUsers = 0;
    for (Long userId : pushSubscriptionRepository.findSubscribedUserIds()) {
      if (ringTo(userId, context)) {
        rangUsers++;
      }
    }
    // 5분마다 돌아서 대부분 0명입니다. 그날 한 칸에 마지막 결과만 덮어씁니다.
    resultStore.save(NAME, context.date(), "울림 " + rangUsers + "명");
    if (rangUsers > 0) {
      log.info("[전화 알림] 울림 {}명", rangUsers);
    }
  }

  // 한 학생에게 전화 알림을 보냅니다. 보냈으면(앱 목록에 남겼으면) true
  private boolean ringTo(Long userId, JobContext context) {
    Instant now = context.startedAt();

    // 1) 지금 울릴 때인지
    LocalTime callTime =
        careSettingRepository
            .findByUserId(userId)
            .map(CareSetting::getCallTime)
            .orElse(CareSetting.DEFAULT_CALL_TIME);
    Instant lastRingAt =
        messageRepository
            .findFirstByUserIdAndKindOrderBySentAtDesc(userId, MessageKind.CALL)
            .map(Message::getSentAt)
            .orElse(null);
    if (!CallRing.shouldRingNow(now, callTime, lastRingAt)) {
      return false;
    }

    // 2) 그 학생의 연락 금지 시간 (보내도 될 때만 SEND)
    QuietHours.Decision decision =
        QuietHours.decide(now, quietHoursSettings.windowOf(userId), MessageKind.CALL.quietPolicy());
    if (decision.action() != QuietHours.Action.SEND) {
      return false;
    }

    // 3) 오늘 걸 수 있는 통화가 남았는지
    if (gptUsageGuard.allowedCallSeconds(userId) <= 0) {
      return false;
    }

    // 4) 앱 목록에 남기고 푸시 (먼저 남겨서, 푸시가 실패해도 12시간 안에 다시 울리지 않게 합니다)
    messageService.save(userId, MessageKind.CALL, BODY);
    pushSubscriptionService.sendToUser(userId, new WebPushSender.Payload(TITLE, BODY, URL));
    return true;
  }
}
