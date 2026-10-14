package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.bias.BiasQuestion;
import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest.Line;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishResponse;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// Service 단위 테스트: GPT(CallReviewClient)와 DB(CallRecordRepository)를 가짜로 바꿔서 마무리 로직만 확인합니다.
class CallRecordServiceTest {

  private static final Long USER_ID = 7L;
  private static final Long CALL_ID = 12L;
  private static final Instant NOW = Instant.parse("2026-10-14T11:05:00Z");

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final CallRecordRepository callRecordRepository = mock(CallRecordRepository.class);
  private final CallReviewClient callReviewClient = mock(CallReviewClient.class);

  private final CallRecordService service =
      new CallRecordService(
          currentUserProvider,
          callRecordRepository,
          callReviewClient,
          Clock.fixed(NOW, ZoneOffset.UTC));

  // 마무리 전의 12번 통화. "미래의 나 믿기" 를 확인하려던 통화입니다.
  private CallRecord call;

  private static final CallFinishRequest TRANSCRIPT =
      new CallFinishRequest(
          List.of(
              new Line("ai", "어제 약속 지켰어?"),
              new Line("me", "아니 폰 보다가 이따가 하면 된다고 생각했어"),
              new Line("ai", "내일의 너는 오늘의 너랑 뭐가 다를 것 같아?"),
              new Line("me", "음 별로 안 다를 것 같아")));

  private static final CallReview.Raw GPT_ANSWER =
      new CallReview.Raw(
          false,
          "폰을 보다가 늦게 시작했다고 함. 오늘은 폰을 서랍에 넣기로 함.",
          "FUTURE_SELF",
          "이따가 하면 된다고 함",
          "내일의 너는 오늘의 너랑 뭐가 다를 것 같아?",
          "phone",
          "학원에서 오면",
          "폰을 서랍에 넣기",
          false,
          3,
          90);

  @BeforeEach
  void setUp() {
    call =
        new CallRecord(
            USER_ID,
            LocalDate.of(2026, 10, 14),
            NOW.minusSeconds(300),
            new BiasQuestion(CognitiveBias.FUTURE_SELF, 0));
    ReflectionTestUtils.setField(call, "id", CALL_ID);

    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(callRecordRepository.findByIdAndUserId(CALL_ID, USER_ID)).willReturn(Optional.of(call));
    given(callReviewClient.isEnabled()).willReturn(true);
    given(callReviewClient.review(anyString(), anyString(), any()))
        .willReturn(Optional.of(GPT_ANSWER));
  }

  private static void assertError(Runnable action, ErrorCode code, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(code);
  }

  @Test
  @DisplayName("GPT 가 정리한 요약·생각 습관·약속을 저장하고, 결론(습관 이름·설명·해 볼 행동 포함)을 돌려준다")
  void savesTheReviewAndReturnsTheConclusion() {
    CallFinishResponse response = service.finish(CALL_ID, TRANSCRIPT);

    // 저장된 값
    assertThat(call.isFinished()).isTrue();
    assertThat(call.getFinishedAt()).isEqualTo(NOW);
    assertThat(call.getSummary()).startsWith("폰을 보다가 늦게 시작했다고 함.");
    assertThat(call.getFoundBias()).isEqualTo(CognitiveBias.FUTURE_SELF);
    assertThat(call.getFoundEvidence()).isEqualTo("이따가 하면 된다고 함");
    assertThat(call.getActualQuestion()).isEqualTo("내일의 너는 오늘의 너랑 뭐가 다를 것 같아?");
    assertThat(call.getMissReason()).isEqualTo(MissReason.PHONE);
    assertThat(call.getPromiseIf()).isEqualTo("학원에서 오면");
    assertThat(call.getPromiseKept()).isFalse();
    assertThat(call.isCrisisDetected()).isFalse();
    verify(callRecordRepository).save(call);

    // 돌려주는 결론
    assertThat(response.callId()).isEqualTo("12");
    assertThat(response.showHelp()).isFalse();
    assertThat(response.bias().key()).isEqualTo("FUTURE_SELF");
    assertThat(response.bias().label()).isEqualTo("미래의 나 믿기");
    assertThat(response.bias().solution()).isEqualTo(CognitiveBias.FUTURE_SELF.getSolution());
    assertThat(response.bias().evidence()).isEqualTo("이따가 하면 된다고 함");
    assertThat(response.missReason()).isEqualTo("phone");
    assertThat(response.mood()).isEqualTo(3);
    assertThat(response.expectedMinutes()).isEqualTo(90);
  }

  @Test
  @DisplayName("GPT 에는 정리 지침·자막·칸 모양을 건넨다")
  void passesThePromptToGpt() {
    service.finish(CALL_ID, TRANSCRIPT);

    verify(callReviewClient)
        .review(
            CallReviewPrompt.instructions(),
            CallReviewPrompt.input(TRANSCRIPT.lines()),
            CallReviewPrompt.schema());
  }

  @Test
  @DisplayName("학생 말에 위기 표현이 있으면 GPT 를 부르지 않고, 내용 없이 '감지됨' 만 남기고 도움 안내를 돌려준다")
  void crisisWordsSkipGpt() {
    CallFinishRequest crisis =
        new CallFinishRequest(List.of(new Line("ai", "요즘 어때?"), new Line("me", "그냥 다 사라지고 싶어")));

    CallFinishResponse response = service.finish(CALL_ID, crisis);

    assertThat(response.showHelp()).isTrue();
    assertThat(response.summary()).isNull();
    assertThat(response.bias()).isNull();
    assertThat(call.isCrisisDetected()).isTrue();
    assertThat(call.getSummary()).isNull();
    verify(callReviewClient, never()).review(anyString(), anyString(), any());
    verify(callRecordRepository).save(call);
  }

  @Test
  @DisplayName("AI 가 한 말에 위기 단어가 있는 것은 감지로 보지 않는다 (학생 말만 본다)")
  void aiLinesAreNotCheckedForCrisisWords() {
    CallFinishRequest request =
        new CallFinishRequest(
            List.of(new Line("ai", "자해 같은 생각이 들면 꼭 말해 줘"), new Line("me", "응 괜찮아")));

    assertThat(service.finish(CALL_ID, request).showHelp()).isFalse();
  }

  @Test
  @DisplayName("GPT 가 위기 표현이라고 하면 단어 목록에 없어도 감지로 본다 (놓치는 쪽이 더 위험)")
  void gptCrisisAlsoCounts() {
    given(callReviewClient.review(anyString(), anyString(), any()))
        .willReturn(
            Optional.of(
                new CallReview.Raw(
                    true, "요약", "FUTURE_SELF", "근거", null, null, null, null, null, null, null)));

    CallFinishResponse response = service.finish(CALL_ID, TRANSCRIPT);

    assertThat(response.showHelp()).isTrue();
    assertThat(call.isCrisisDetected()).isTrue();
    assertThat(call.getFoundBias()).isNull();
  }

  @Test
  @DisplayName("학생이 한마디도 안 했으면 GPT 를 부르지 않고 빈 기록으로 마무리한다")
  void silentCallIsFinishedWithoutGpt() {
    CallFinishRequest silent =
        new CallFinishRequest(List.of(new Line("ai", "여보세요?"), new Line("me", "   ")));

    CallFinishResponse response = service.finish(CALL_ID, silent);

    assertThat(call.isFinished()).isTrue();
    assertThat(response.showHelp()).isFalse();
    assertThat(response.summary()).isNull();
    verify(callReviewClient, never()).review(anyString(), anyString(), any());
  }

  @Test
  @DisplayName("없는 번호이거나 남의 통화면 404")
  void notMyCall() {
    given(callRecordRepository.findByIdAndUserId(99L, USER_ID)).willReturn(Optional.empty());

    assertError(() -> service.finish(99L, TRANSCRIPT), ErrorCode.NOT_FOUND, "통화 기록을 찾을 수 없어요");
  }

  @Test
  @DisplayName("이미 마무리한 통화를 또 보내면 409 이고, GPT 를 부르지 않는다")
  void alreadyFinished() {
    call.finishWithCrisis(NOW.minusSeconds(10));

    assertError(() -> service.finish(CALL_ID, TRANSCRIPT), ErrorCode.CONFLICT, "이미 마무리한 통화예요");
    verify(callReviewClient, never()).review(anyString(), anyString(), any());
  }

  @Test
  @DisplayName("AI 가 꺼진 서버(내 컴퓨터)면 503 이고, 기록은 마무리하지 않는다 (다시 시도할 수 있게)")
  void unavailableWhenOff() {
    given(callReviewClient.isEnabled()).willReturn(false);

    assertError(
        () -> service.finish(CALL_ID, TRANSCRIPT),
        ErrorCode.SERVICE_UNAVAILABLE,
        "AI 정리가 아직 켜져 있지 않아요");
    assertThat(call.isFinished()).isFalse();
  }

  @Test
  @DisplayName("GPT 가 실패하면 503 이고, 기록은 마무리하지 않는다 (다시 시도할 수 있게)")
  void gptFailureCanBeRetried() {
    given(callReviewClient.review(anyString(), anyString(), any())).willReturn(Optional.empty());

    assertError(
        () -> service.finish(CALL_ID, TRANSCRIPT),
        ErrorCode.SERVICE_UNAVAILABLE,
        "통화를 정리하지 못했어요. 잠시 후 다시 시도해 주세요");
    assertThat(call.isFinished()).isFalse();
    verify(callRecordRepository, never()).save(any());
  }

  @Test
  @DisplayName("로그인하지 않았으면 401 이고, 기록을 찾지도 않는다")
  void requiresLogin() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    assertError(() -> service.finish(CALL_ID, TRANSCRIPT), ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
    verify(callRecordRepository, never()).findByIdAndUserId(any(), any());
  }
}
