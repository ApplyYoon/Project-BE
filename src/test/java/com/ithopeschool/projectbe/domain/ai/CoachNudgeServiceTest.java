package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.ai.dto.CoachNudgeRequest;
import com.ithopeschool.projectbe.domain.ai.dto.CoachNudgeResponse;
import com.ithopeschool.projectbe.domain.ai.dto.DayPlanRequest;
import com.ithopeschool.projectbe.domain.call.CallRecord;
import com.ithopeschool.projectbe.domain.call.CallRecordRepository;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.safety.SafetyService;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Service 단위 테스트: AI 를 가짜로 바꿔서 날짜 검사 · 안전(보호 조치 · 최근 위기 · 보낸 글) · 못 만듦 · 검사를 봅니다.
class CoachNudgeServiceTest {

  private static final Long USER_ID = 7L;
  // 한국 시간 2026-10-18 21:00 → 오늘은 10-18
  private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 18);

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final SafetyService safetyService = mock(SafetyService.class);
  private final CallRecordRepository callRecordRepository = mock(CallRecordRepository.class);
  private final CoachNudgeService service =
      new CoachNudgeService(
          currentUserProvider,
          aiClient,
          safetyService,
          callRecordRepository,
          Clock.fixed(NOW, ZoneOffset.UTC));

  private static CoachNudgeRequest request(LocalDate date, String cardTitle) {
    return new CoachNudgeRequest(
        date,
        List.of("국어", "미적분"),
        cardTitle == null ? null : List.of(new CoachNudgeRequest.Card("19:00", cardTitle, 40)),
        null,
        null,
        null,
        null);
  }

  @BeforeEach
  void loggedIn() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(
            callRecordRepository.findByUserIdAndCallDateGreaterThanEqualOrderByStartedAtDescIdDesc(
                USER_ID, TODAY.minusDays(6)))
        .willReturn(List.of());
  }

  @Test
  @DisplayName("날짜가 오늘이나 내일이 아니면 400 이고 AI 를 부르지 않는다")
  void rejectsOtherDates() {
    assertThatThrownBy(() -> service.make(request(TODAY.minusDays(1), null)))
        .isInstanceOf(ApiException.class)
        .hasMessage("알림 날짜는 오늘이나 내일로 보내 주세요");
    assertThatThrownBy(() -> service.make(request(TODAY.plusDays(2), null)))
        .isInstanceOf(ApiException.class);
    verifyNoInteractions(aiClient);
  }

  @Test
  @DisplayName("보호 조치 중이면 AI 를 부르지 않고 위로 문장 (comfort: true)")
  void comfortWhenSafetyActive() {
    given(safetyService.isActive(USER_ID)).willReturn(true);

    CoachNudgeResponse response = service.make(request(TODAY, "미적분"));

    assertThat(response).isEqualTo(CoachNudgeResponse.comforting());
    assertThat(response.comfort()).isTrue();
    assertThat(response.morning().body()).doesNotContain("공부해");
    verifyNoInteractions(aiClient);
  }

  @Test
  @DisplayName("최근 7일 안에 위기 표현이 감지된 통화 · 채팅이 있으면 위로 문장")
  void comfortWhenRecentCrisis() {
    CallRecord crisisCall = new CallRecord(USER_ID, TODAY.minusDays(6), NOW, null);
    crisisCall.markCrisis();
    given(
            callRecordRepository.findByUserIdAndCallDateGreaterThanEqualOrderByStartedAtDescIdDesc(
                USER_ID, TODAY.minusDays(6)))
        .willReturn(List.of(new CallRecord(USER_ID, TODAY, NOW, null), crisisCall));

    assertThat(service.make(request(TODAY.plusDays(1), "미적분")))
        .isEqualTo(CoachNudgeResponse.comforting());
    verifyNoInteractions(aiClient);
  }

  @Test
  @DisplayName("보낸 글(카드 제목 · 주제 · 시험 이름 · 준비하는 것)에 위기 표현이 있으면 위로 문장")
  void comfortWhenRequestHasCrisisText() {
    assertThat(service.make(request(TODAY, "다 그만두고 죽고 싶어")))
        .isEqualTo(CoachNudgeResponse.comforting());
    assertThat(
            service.make(
                new CoachNudgeRequest(
                    TODAY,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new DayPlanRequest.Focus("OTHER", "자해 안 하기"))))
        .isEqualTo(CoachNudgeResponse.comforting());
    verifyNoInteractions(aiClient);
  }

  @Test
  @DisplayName("AI 를 못 부르면(꺼짐 · 한도 · 실패) 둘 다 null, 받으면 검사한 문장 (ASSIST 한도)")
  void asksAiAndChecks() {
    given(aiClient.ask(eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(CoachNudge.Answer.class)))
        .willReturn(Optional.empty())
        .willReturn(
            Optional.of(
                new CoachNudge.Answer(
                    new CoachNudge.Answer.Item("오늘 2교시 미적분", "쉬는 시간에 한 문제만 보자."),
                    new CoachNudge.Answer.Item("오늘 카드 어땠어?", "어제 3시간 했으니 오늘도!"))));

    assertThat(service.make(request(TODAY, "미적분 문제집"))).isEqualTo(CoachNudgeResponse.notMade());

    CoachNudgeResponse made = service.make(request(TODAY, "미적분 문제집"));
    // 2교시는 입력(1교시 국어 · 2교시 미적분)에 있는 숫자라 통과, 3시간은 지어낸 숫자라 빠짐
    assertThat(made.morning()).isEqualTo(new CoachNudge.Nudge("오늘 2교시 미적분", "쉬는 시간에 한 문제만 보자."));
    assertThat(made.evening()).isNull();
    assertThat(made.comfort()).isFalse();
    verify(aiClient, times(2))
        .ask(eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(CoachNudge.Answer.class));
  }
}
