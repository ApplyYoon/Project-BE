package com.ithopeschool.projectbe.domain.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.call.CheckCall;
import com.ithopeschool.projectbe.domain.call.CheckCallService;
import com.ithopeschool.projectbe.domain.gpt.GptUsageGuard;
import com.ithopeschool.projectbe.domain.job.JobContext;
import com.ithopeschool.projectbe.domain.job.JobResultStore;
import com.ithopeschool.projectbe.domain.message.Message;
import com.ithopeschool.projectbe.domain.message.MessageKind;
import com.ithopeschool.projectbe.domain.message.MessageRepository;
import com.ithopeschool.projectbe.domain.message.MessageService;
import com.ithopeschool.projectbe.domain.safety.QuietHours;
import com.ithopeschool.projectbe.domain.safety.QuietPolicy;
import com.ithopeschool.projectbe.domain.settings.CareSetting;
import com.ithopeschool.projectbe.domain.settings.CareSettingRepository;
import com.ithopeschool.projectbe.domain.settings.QuietHoursSettings;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// "전화 알림" 예약 작업의 규칙을 확인합니다. DB 와 푸시는 가짜로 바꿉니다.
// 시각은 한국 시간 = UTC + 9 시간입니다. (21:00 한국 시간 = 12:00Z)
class CallRingJobTest {

  private static final Long STUDENT = 7L;
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

  private final PushSubscriptionRepository subscriptions = mock(PushSubscriptionRepository.class);
  private final PushSubscriptionService pushService = mock(PushSubscriptionService.class);
  private final MessageRepository messageRepository = mock(MessageRepository.class);
  private final MessageService messageService = mock(MessageService.class);
  private final CareSettingRepository careSettings = mock(CareSettingRepository.class);
  private final QuietHoursSettings quietHoursSettings = mock(QuietHoursSettings.class);
  private final GptUsageGuard gptUsageGuard = mock(GptUsageGuard.class);
  private final JobResultStore resultStore = new JobResultStore();
  // 확인 통화 문은 기본으로 열려 있습니다. (오늘 공부를 끝냄)
  private final CheckCallService checkCallService = mock(CheckCallService.class);

  private final CallRingJob job =
      new CallRingJob(
          subscriptions,
          pushService,
          messageRepository,
          messageService,
          careSettings,
          quietHoursSettings,
          gptUsageGuard,
          resultStore,
          checkCallService);

  // 한국 시간 "HH:mm" 에 작업이 돈 것으로 합니다.
  private static JobContext at(String koreanTime) {
    LocalTime time = LocalTime.parse(koreanTime);
    Instant startedAt = TODAY.atTime(time).minusHours(9).toInstant(ZoneOffset.UTC);
    return new JobContext(TODAY, startedAt);
  }

  // 학생이 관리 설정에서 전화 시간을 바꿔 두었다고 합니다.
  private void callTimeIs(String time) {
    CareSetting setting = mock(CareSetting.class);
    given(setting.getCallTime()).willReturn(LocalTime.parse(time));
    given(careSettings.findByUserId(STUDENT)).willReturn(Optional.of(setting));
  }

  // 마지막으로 전화 알림을 울린 시각 (UTC)
  private void lastRangAt(String instant) {
    Message message = mock(Message.class);
    given(message.getSentAt()).willReturn(Instant.parse(instant));
    given(messageRepository.findFirstByUserIdAndKindOrderBySentAtDesc(STUDENT, MessageKind.CALL))
        .willReturn(Optional.of(message));
  }

  private void expectRang() {
    verify(messageService).save(STUDENT, MessageKind.CALL, CallRingJob.BODY);
    ArgumentCaptor<WebPushSender.Payload> payload =
        ArgumentCaptor.forClass(WebPushSender.Payload.class);
    verify(pushService).sendToUser(eq(STUDENT), payload.capture());
    assertThat(payload.getValue().title()).isEqualTo(CallRingJob.TITLE);
    assertThat(payload.getValue().url()).isEqualTo("/call");
  }

  private void expectNotRang() {
    verify(messageService, never()).save(anyLong(), any(), any());
    verifyNoInteractions(pushService);
  }

  @BeforeEach
  void setUp() {
    // 기본: 7번 학생이 알림을 켰고, 설정은 안 바꿨고(전화 21:00 · 연락 금지 23:00 ~ 07:00),
    // 울린 적이 없고, 오늘 통화가 남아 있음
    given(subscriptions.findSubscribedUserIds()).willReturn(List.of(STUDENT));
    given(careSettings.findByUserId(anyLong())).willReturn(Optional.empty());
    given(messageRepository.findFirstByUserIdAndKindOrderBySentAtDesc(anyLong(), any()))
        .willReturn(Optional.empty());
    given(quietHoursSettings.windowOf(anyLong())).willReturn(QuietHours.Window.DEFAULT);
    given(gptUsageGuard.allowedCallSeconds(anyLong())).willReturn(300);
    given(checkCallService.gateOf(anyLong(), any()))
        .willReturn(new CheckCall.Gate(true, null, null));
  }

  @Test
  @DisplayName("오늘 끝낸 공부가 없어서 확인 통화 문이 닫혀 있으면 울리지 않는다 (눌러도 못 거니까)")
  void notWhenCallLocked() {
    given(checkCallService.gateOf(STUDENT, TODAY)).willReturn(CheckCall.Gate.locked());

    job.run(at("21:00"));

    expectNotRang();
  }

  @Test
  @DisplayName("설정을 안 바꿨으면 기본 전화 시간 21:00 에 울린다. 앱 목록(CALL)에 남기고 푸시를 보낸다")
  void ringsAtDefaultCallTime() {
    job.run(at("21:00"));

    expectRang();
    assertThat(resultStore.find(CallRingJob.NAME, TODAY)).contains("울림 1명");
  }

  @Test
  @DisplayName("전화 시간 전에는 울리지 않는다")
  void notBeforeCallTime() {
    job.run(at("20:59"));

    expectNotRang();
  }

  @Test
  @DisplayName("정한 시각에서 5분 안(21:04)이면 아직 울린다 (예약 작업이 딱 그 1분에 돌지 않아도 놓치지 않게)")
  void ringsWithinFiveMinutes() {
    job.run(at("21:04"));
    expectRang();
  }

  @Test
  @DisplayName("21:05 는 5분이 지나서 놓친 것으로 보고 울리지 않는다")
  void notAtFiveMinutesLate() {
    job.run(at("21:05"));

    expectNotRang();
  }

  @Test
  @DisplayName("최근 12시간 안에 이미 울렸으면 다시 울리지 않는다 (5분마다 불려도 한 번만)")
  void ringsOnlyOnce() {
    // 21:00 한국 시간 = 12:00Z 에 이미 울림
    lastRangAt("2026-10-17T12:00:00Z");

    job.run(at("21:03"));

    expectNotRang();
  }

  @Test
  @DisplayName("어제 울렸던 것은 12시간이 지나서 오늘은 다시 울린다")
  void ringsAgainNextDay() {
    lastRangAt("2026-10-16T12:00:00Z");

    job.run(at("21:00"));

    expectRang();
  }

  @Test
  @DisplayName("학생이 정한 전화 시간을 따른다 (22:00 으로 바꿨으면 21:00 에는 안 울리고 22:02 에 울린다)")
  void followsStudentsCallTime() {
    callTimeIs("22:00");

    job.run(at("21:00"));
    expectNotRang();

    job.run(at("22:02"));
    expectRang();
  }

  @Test
  @DisplayName("전화 시간이 연락 금지 시간 안이면 보내지 않는다 (기본 23:00 ~ 07:00)")
  void respectsQuietHours() {
    callTimeIs("23:30");

    job.run(at("23:30"));

    expectNotRang();
  }

  @Test
  @DisplayName("오늘 걸 수 있는 통화가 남지 않았으면 보내지 않는다 (눌러도 통화를 못 함)")
  void skipsWhenNoCallsLeft() {
    given(gptUsageGuard.allowedCallSeconds(STUDENT)).willReturn(0);

    job.run(at("21:00"));

    expectNotRang();
  }

  @Test
  @DisplayName("알림을 켠 학생이 없으면 아무것도 하지 않고 '울림 0명' 으로 남긴다")
  void noSubscribers() {
    given(subscriptions.findSubscribedUserIds()).willReturn(List.of());

    job.run(at("21:00"));

    expectNotRang();
    assertThat(resultStore.find(CallRingJob.NAME, TODAY)).contains("울림 0명");
  }

  @Test
  @DisplayName("전화 알림은 연락 금지 시간에 걸리면 미루지 않고 버리는 종류다")
  void callMessageIsDropped() {
    assertThat(MessageKind.CALL.quietPolicy()).isEqualTo(QuietPolicy.DROP);
    assertThat(MessageKind.CALL.toValue()).isEqualTo("call");
  }
}
