package com.ithopeschool.projectbe.domain.push;

import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.call.CallRecord;
import com.ithopeschool.projectbe.domain.call.CallRecordRepository;
import com.ithopeschool.projectbe.domain.job.JobContext;
import com.ithopeschool.projectbe.domain.job.JobResultStore;
import com.ithopeschool.projectbe.domain.job.ScheduledJob;
import com.ithopeschool.projectbe.domain.message.MessageKind;
import com.ithopeschool.projectbe.domain.message.MessageRepository;
import com.ithopeschool.projectbe.domain.message.MessageService;
import com.ithopeschool.projectbe.domain.safety.QuietHours;
import com.ithopeschool.projectbe.domain.settings.QuietHoursSettings;
import com.ithopeschool.projectbe.domain.thinkingtest.StudyStyle;
import com.ithopeschool.projectbe.domain.thinkingtest.ThinkingTestRepository;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// 하루 한 번, 알림을 켠 학생에게 "오늘의 작전" 푸시를 보내는 예약 작업입니다. (이슈 #32, docs/JOBS.md "habit-nudge")
// cron-job.org 가 POST /api/v1/jobs/habit-nudge 를 부릅니다. (예: 매일 저녁 7시)
//
// 학생마다 아래를 차례로 따지고, 하나라도 걸리면 그 학생은 건너뜁니다.
//   1. 그 학생의 연락 금지 시간이면 보내지 않습니다.                     (관리 설정 #22, 안 정했으면 23 ~ 7시)
//      "오늘의 작전" 은 미룰 메시지(QuietPolicy.DELAY)지만, 미뤄 두었다 보내는 대기 목록이 아직 없어서 그날은 보내지 않습니다. (#57)
//   2. 오늘(새벽 4시 기준) 이미 보냈으면 보내지 않습니다.                 (같은 작업이 두 번 불려도 한 번만)
//   3. 최근 7일 안에 위기 표현이 감지된 통화가 있으면 보내지 않습니다.      (안전이 먼저. 공부 재촉을 멈춤)
//   4. 생각 습관에 맞춘 한 줄을 고릅니다: 저장된 테스트에서 가장 높은 습관 → 최근 2주 통화에서 가장 최근에 보인 습관 → 없으면 기본 문장
//   5. 앱의 알림 목록(messages)에 남기고, 그 학생의 모든 기기에 푸시를 보냅니다.
//
// ⚠️ 메시지 빈도(관리 설정의 dailyCount)는 아직 보지 않습니다. 이 작업은 하루 한 번만 보냅니다.
// ⚠️ 체크인의 기분·시험 기간은 아직 브라우저에만 있어서, 안전 브레이크 중 "위기 표현" 만 봅니다.
// ⚠️ 알림 내용은 로그에 찍지 않습니다. 몇 명에게 보냈는지 숫자만 남깁니다.
@Component
@RequiredArgsConstructor
public class HabitNudgeJob implements ScheduledJob {

  public static final String NAME = "habit-nudge";

  // 위기 표현이 있던 통화가 이 기간(일, 오늘 포함) 안에 있으면 보내지 않습니다. (통화의 위로 기간과 같습니다)
  static final int CRISIS_PAUSE_DAYS = 7;
  // 습관을 찾아볼 지난 통화의 기간(일, 오늘 포함)
  static final int RECENT_DAYS = 14;

  private static final Logger log = LoggerFactory.getLogger(HabitNudgeJob.class);

  private final PushSubscriptionRepository pushSubscriptionRepository;
  private final PushSubscriptionService pushSubscriptionService;
  private final MessageRepository messageRepository;
  private final MessageService messageService;
  private final ThinkingTestRepository thinkingTestRepository;
  private final CallRecordRepository callRecordRepository;
  private final JobResultStore resultStore;
  // 학생마다 관리 설정의 연락 금지 시간을 읽어 옵니다. (#22, #57)
  private final QuietHoursSettings quietHoursSettings;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void run(JobContext context) {
    int sentUsers = 0;
    int skipped = 0;
    for (Long userId : pushSubscriptionRepository.findSubscribedUserIds()) {
      if (sendTo(userId, context)) {
        sentUsers++;
      } else {
        skipped++;
      }
    }
    // 결과는 그날 한 칸에 덮어씁니다. (같은 날 다시 돌면 이미 보낸 사람은 건너뛰어서 숫자만 달라집니다)
    resultStore.save(NAME, context.date(), "보냄 " + sentUsers + "명 · 건너뜀 " + skipped + "명");
    log.info("[오늘의 작전] 보냄 {}명 · 건너뜀 {}명", sentUsers, skipped);
  }

  // 한 학생에게 보냅니다. 보냈으면(앱 목록에 남겼으면) true
  private boolean sendTo(Long userId, JobContext context) {
    // 1) 그 학생의 연락 금지 시간 (보내도 될 때만 SEND)
    QuietHours.Decision decision =
        QuietHours.decide(
            context.startedAt(),
            quietHoursSettings.windowOf(userId),
            MessageKind.NUDGE.quietPolicy());
    if (decision.action() != QuietHours.Action.SEND) {
      return false;
    }
    // 2) 오늘 이미 보냈는지 (새벽 4시부터)
    if (messageRepository.existsByUserIdAndKindAndSentAtGreaterThanEqual(
        userId, MessageKind.NUDGE, DayRule.startOf(context.date()))) {
      return false;
    }
    List<CallRecord> recentCalls =
        callRecordRepository.findByUserIdAndCallDateGreaterThanEqualOrderByStartedAtDescIdDesc(
            userId, context.date().minusDays(RECENT_DAYS - 1));
    // 3) 최근 위기 표현
    if (hasRecentCrisis(recentCalls, context)) {
      return false;
    }
    // 4) 한 줄 고르기
    HabitNudge nudge = HabitNudge.of(habitOf(userId, recentCalls));
    // 5) 앱 목록에 남기고 푸시 (먼저 남겨서, 푸시가 실패해도 오늘 다시 보내지 않게 합니다)
    messageService.save(userId, MessageKind.NUDGE, nudge.body());
    pushSubscriptionService.sendToUser(userId, nudge.toPayload());
    return true;
  }

  private static boolean hasRecentCrisis(List<CallRecord> recentCalls, JobContext context) {
    for (CallRecord call : recentCalls) {
      if (call.isCrisisDetected()
          && !call.getCallDate().isBefore(context.date().minusDays(CRISIS_PAUSE_DAYS - 1))) {
        return true;
      }
    }
    return false;
  }

  // 이 학생에게 맞출 생각 습관. 테스트에서 가장 높은 습관 → 최근 통화에서 가장 최근에 보인 습관 → 없으면 null
  private CognitiveBias habitOf(Long userId, List<CallRecord> recentCalls) {
    List<CognitiveBias> top =
        thinkingTestRepository
            .findFirstByUserIdOrderByIdDesc(userId)
            .map(test -> StudyStyle.topHabitsOf(test.getScores()))
            .orElse(List.of());
    if (!top.isEmpty()) {
      return top.get(0);
    }
    for (CallRecord call : recentCalls) {
      if (call.getFoundBias() != null) {
        return call.getFoundBias();
      }
    }
    return null;
  }
}
