package com.ithopeschool.projectbe.domain.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.bias.Weakness;
import com.ithopeschool.projectbe.domain.call.CallRecord;
import com.ithopeschool.projectbe.domain.call.CallRecordRepository;
import com.ithopeschool.projectbe.domain.job.JobContext;
import com.ithopeschool.projectbe.domain.job.JobResultStore;
import com.ithopeschool.projectbe.domain.message.MessageKind;
import com.ithopeschool.projectbe.domain.message.MessageRepository;
import com.ithopeschool.projectbe.domain.message.MessageService;
import com.ithopeschool.projectbe.domain.safety.QuietHours;
import com.ithopeschool.projectbe.domain.safety.SafetyService;
import com.ithopeschool.projectbe.domain.settings.QuietHoursSettings;
import com.ithopeschool.projectbe.domain.thinkingtest.ThinkingTest;
import com.ithopeschool.projectbe.domain.thinkingtest.ThinkingTestRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

// "오늘의 작전" 예약 작업의 규칙을 확인합니다. DB 와 푸시는 가짜로 바꿉니다.
class HabitNudgeJobTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
  // 저녁 7시(한국 시간) = 10:00Z — 연락 금지 시간이 아님
  private static final JobContext EVENING =
      new JobContext(TODAY, Instant.parse("2026-10-15T10:00:00Z"));
  // 밤 11시 30분(한국 시간) = 14:30Z — 연락 금지 시간
  private static final JobContext LATE_NIGHT =
      new JobContext(TODAY, Instant.parse("2026-10-15T14:30:00Z"));

  private final PushSubscriptionRepository subscriptions = mock(PushSubscriptionRepository.class);
  private final PushSubscriptionService pushService = mock(PushSubscriptionService.class);
  private final MessageRepository messageRepository = mock(MessageRepository.class);
  private final MessageService messageService = mock(MessageService.class);
  private final ThinkingTestRepository thinkingTests = mock(ThinkingTestRepository.class);
  private final CallRecordRepository callRecords = mock(CallRecordRepository.class);
  private final JobResultStore resultStore = new JobResultStore();
  // 학생마다의 연락 금지 시간 (관리 설정 #22). 기본은 23:00 ~ 07:00 입니다. (setUp)
  private final QuietHoursSettings quietHoursSettings = mock(QuietHoursSettings.class);

  private final SafetyService safetyService = mock(SafetyService.class);
  private final HabitNudgeJob job =
      new HabitNudgeJob(
          subscriptions,
          pushService,
          messageRepository,
          messageService,
          thinkingTests,
          callRecords,
          resultStore,
          quietHoursSettings,
          safetyService);

  @Test
  void checkInProtectionStopsNudgeBeforeSending() {
    given(safetyService.isActive(7L)).willReturn(true);
    job.run(EVENING);
    org.mockito.Mockito.verifyNoInteractions(messageService, pushService);
  }

  @BeforeEach
  void setUp() {
    // 기본: 7번 학생이 알림을 켰고, 오늘 아직 안 보냈고, 테스트·통화 기록은 없음
    given(subscriptions.findSubscribedUserIds()).willReturn(List.of(7L));
    given(messageRepository.existsByUserIdAndKindAndSentAtGreaterThanEqual(anyLong(), any(), any()))
        .willReturn(false);
    given(thinkingTests.findFirstByUserIdOrderByIdDesc(anyLong())).willReturn(Optional.empty());
    given(
            callRecords.findByUserIdAndCallDateGreaterThanEqualOrderByStartedAtDescIdDesc(
                anyLong(), any()))
        .willReturn(List.of());
    given(quietHoursSettings.windowOf(anyLong())).willReturn(QuietHours.Window.DEFAULT);
  }

  private static ThinkingTest testWithTop(CognitiveBias top) {
    Map<CognitiveBias, Double> scores = new EnumMap<>(CognitiveBias.class);
    for (CognitiveBias bias : CognitiveBias.values()) {
      scores.put(bias, 4.0);
    }
    scores.put(top, 6.0);
    return new ThinkingTest(
        7L, Instant.parse("2026-10-01T00:00:00Z"), 2, scores, EnumSet.noneOf(Weakness.class));
  }

  @Test
  @DisplayName("기록이 없으면 기본 문장을 앱 알림 목록에 먼저 남기고, 그다음 푸시를 보낸다")
  void sendsGenericNudge() {
    job.run(EVENING);

    InOrder order = inOrder(messageService, pushService);
    order.verify(messageService).save(7L, MessageKind.NUDGE, "오늘 계획 하나, 5분만 먼저 시작해 볼까?");
    order.verify(pushService).sendToUser(eq(7L), eq(HabitNudge.of(null).toPayload()));
    assertThat(resultStore.find(HabitNudgeJob.NAME, TODAY)).contains("보냄 1명 · 건너뜀 0명");
  }

  @Test
  @DisplayName("저장된 테스트가 있으면 가장 높은 습관의 작전을 보낸다")
  void usesTheTestsTopHabit() {
    given(thinkingTests.findFirstByUserIdOrderByIdDesc(7L))
        .willReturn(Optional.of(testWithTop(CognitiveBias.FUTURE_SELF)));

    job.run(EVENING);

    verify(messageService)
        .save(7L, MessageKind.NUDGE, "\"내일부터\" 대신 오늘 5분짜리 시작 하나 정하기 (미래의 나 믿기 대비)");
  }

  @Test
  @DisplayName("테스트가 없으면 최근 통화에서 가장 최근에 보인 습관을 쓴다")
  void usesTheLatestFoundHabit() {
    CallRecord call =
        new CallRecord(7L, TODAY.minusDays(1), Instant.parse("2026-10-14T10:00:00Z"), null);
    call.finish(
        Instant.parse("2026-10-14T10:03:00Z"),
        "요약",
        CognitiveBias.WHAT_THE_HELL,
        null,
        null,
        null,
        null,
        null,
        null);
    given(
            callRecords.findByUserIdAndCallDateGreaterThanEqualOrderByStartedAtDescIdDesc(
                7L, TODAY.minusDays(13)))
        .willReturn(List.of(call));

    job.run(EVENING);

    verify(messageService).save(7L, MessageKind.NUDGE, "계획 하나를 놓쳐도 다음 계획 하나는 지키기 (될 대로 되라 대비)");
  }

  @Test
  @DisplayName("기본 연락 금지 시간(23~7시)이면 보내지 않는다")
  void quietHours() {
    job.run(LATE_NIGHT);

    verify(messageService, never()).save(anyLong(), any(), any());
    verify(pushService, never()).sendToUser(anyLong(), any());
    assertThat(resultStore.find(HabitNudgeJob.NAME, TODAY)).contains("보냄 0명 · 건너뜀 1명");
  }

  @Test
  @DisplayName("학생이 관리 설정에서 정한 연락 금지 시간을 따른다: 18~7시로 정한 학생에게는 저녁 7시에도 안 보낸다 (#57)")
  void followsEachStudentsQuietHours() {
    given(subscriptions.findSubscribedUserIds()).willReturn(List.of(7L, 8L));
    // 7번은 18:00 ~ 07:00 로 정했고, 8번은 기본값입니다.
    given(quietHoursSettings.windowOf(7L))
        .willReturn(new QuietHours.Window(LocalTime.of(18, 0), LocalTime.of(7, 0)));

    job.run(EVENING);

    verify(messageService, never()).save(eq(7L), any(), any());
    verify(messageService).save(eq(8L), eq(MessageKind.NUDGE), any());
    assertThat(resultStore.find(HabitNudgeJob.NAME, TODAY)).contains("보냄 1명 · 건너뜀 1명");
  }

  @Test
  @DisplayName("기본값보다 늦게 금지를 시작한 학생(23시30분~)에게는 밤 11시 30분 직전까지 보낸다")
  void laterQuietStart() {
    // 밤 11시 10분(한국 시간) = 14:10Z — 기본값(23시~)이면 금지지만, 이 학생은 23:30 부터 금지
    JobContext elevenTen = new JobContext(TODAY, Instant.parse("2026-10-15T14:10:00Z"));
    given(quietHoursSettings.windowOf(7L))
        .willReturn(new QuietHours.Window(LocalTime.of(23, 30), LocalTime.of(7, 0)));

    job.run(elevenTen);

    verify(messageService).save(eq(7L), eq(MessageKind.NUDGE), any());
  }

  @Test
  @DisplayName("오늘(새벽 4시부터) 이미 보냈으면 다시 보내지 않는다 (작업이 두 번 불려도 한 번만)")
  void alreadySentToday() {
    given(
            messageRepository.existsByUserIdAndKindAndSentAtGreaterThanEqual(
                7L, MessageKind.NUDGE, Instant.parse("2026-10-14T19:00:00Z")))
        .willReturn(true);

    job.run(EVENING);

    verify(pushService, never()).sendToUser(anyLong(), any());
    assertThat(resultStore.find(HabitNudgeJob.NAME, TODAY)).contains("보냄 0명 · 건너뜀 1명");
  }

  @Test
  @DisplayName("최근 7일 안에 위기 표현이 있던 통화가 있으면 보내지 않는다 (8일 전이면 보낸다)")
  void recentCrisisPausesNudges() {
    CallRecord crisis =
        new CallRecord(7L, TODAY.minusDays(6), Instant.parse("2026-10-09T10:00:00Z"), null);
    crisis.finishWithCrisis(Instant.parse("2026-10-09T10:03:00Z"));
    given(
            callRecords.findByUserIdAndCallDateGreaterThanEqualOrderByStartedAtDescIdDesc(
                anyLong(), any()))
        .willReturn(List.of(crisis));
    job.run(EVENING);
    verify(pushService, never()).sendToUser(anyLong(), any());

    CallRecord oldCrisis =
        new CallRecord(7L, TODAY.minusDays(7), Instant.parse("2026-10-08T10:00:00Z"), null);
    oldCrisis.finishWithCrisis(Instant.parse("2026-10-08T10:03:00Z"));
    given(
            callRecords.findByUserIdAndCallDateGreaterThanEqualOrderByStartedAtDescIdDesc(
                anyLong(), any()))
        .willReturn(List.of(oldCrisis));
    job.run(EVENING);
    verify(pushService).sendToUser(eq(7L), any());
  }

  @Test
  @DisplayName("알림을 켠 학생마다 따로 따진다 (한 명을 건너뛰어도 다른 학생에게는 보낸다)")
  void eachStudentSeparately() {
    given(subscriptions.findSubscribedUserIds()).willReturn(List.of(7L, 8L));
    given(messageRepository.existsByUserIdAndKindAndSentAtGreaterThanEqual(eq(7L), any(), any()))
        .willReturn(true);

    job.run(EVENING);

    verify(pushService, never()).sendToUser(eq(7L), any());
    verify(pushService).sendToUser(eq(8L), any());
    assertThat(resultStore.find(HabitNudgeJob.NAME, TODAY)).contains("보냄 1명 · 건너뜀 1명");
  }
}
