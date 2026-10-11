package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.auth.TokenHasher;
import com.ithopeschool.projectbe.domain.call.CallKeyClient.CallKey;
import com.ithopeschool.projectbe.domain.call.dto.CallSessionRequest;
import com.ithopeschool.projectbe.domain.call.dto.CallSessionRequest.Card;
import com.ithopeschool.projectbe.domain.call.dto.CallSessionResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// Service 단위 테스트: OpenAI 를 부르는 곳(CallKeyClient)을 가짜로 바꿔서, OpenAI 없이 로직만 확인합니다.
class CallServiceTest {

  private static final Long USER_ID = 7L;
  private static final CallKey KEY = new CallKey("ek_abc123", 1791700000000L);

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final CallKeyClient callKeyClient = mock(CallKeyClient.class);
  private final TokenHasher tokenHasher = new TokenHasher();
  // 하루 한도는 2번, 통화 한 번은 최대 300초로 두고 진짜 코드를 씁니다.
  private final CallDailyLimit callDailyLimit =
      new CallDailyLimit(2, Clock.fixed(Instant.parse("2026-10-11T06:00:00Z"), ZoneOffset.UTC));

  private final CallService callService =
      new CallService(currentUserProvider, callKeyClient, callDailyLimit, tokenHasher, 300);

  private static final CallSessionRequest REQUEST =
      new CallSessionRequest(
          2,
          3,
          new Card(
              100, 240, "phone", 3, 180, "19:00", 64, 55, 10, List.of(3, 3), 4, null, null, null));

  @BeforeEach
  void loggedInAndCallOn() {
    // 기본 상황: 로그인한 사람이고, 통화가 켜져 있고, OpenAI 가 임시 키를 줍니다.
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(callKeyClient.isEnabled()).willReturn(true);
    given(callKeyClient.createKey(any(), any())).willReturn(Optional.of(KEY));
  }

  private static void assertError(Runnable action, ErrorCode code, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(code);
  }

  @Test
  @DisplayName("임시 키와 끝나는 시각, 통화 최대 길이를 돌려준다")
  void returnsTheKey() {
    CallSessionResponse response = callService.startSession(REQUEST);

    assertThat(response.value()).isEqualTo("ek_abc123");
    assertThat(response.expiresAt()).isEqualTo(1791700000000L);
    assertThat(response.maxCallSeconds()).isEqualTo(300);
  }

  @Test
  @DisplayName("OpenAI 에는 학생 카드로 만든 대본을 건넨다 (기록에 맞는 질문과 말투가 들어 있다)")
  void passesTheScriptBuiltFromTheCard() {
    callService.startSession(REQUEST);

    ArgumentCaptor<String> instructions = ArgumentCaptor.forClass(String.class);
    verify(callKeyClient).createKey(instructions.capture(), any());
    assertThat(instructions.getValue())
        .isEqualTo(CallScript.instructions(2, 3, REQUEST.card()))
        .contains("폰을 어디에 두면", "담백하게 사실만");
  }

  @Test
  @DisplayName("OpenAI 에 알려 주는 사용자 구분값은 번호 그대로가 아니라 알아볼 수 없는 글자다. 같은 사람은 늘 같고, 다른 사람은 다르다")
  void safetyIdentifierIsAHash() {
    callService.startSession(REQUEST);
    callService.startSession(REQUEST);
    given(currentUserProvider.getCurrentUserId()).willReturn(8L);
    callService.startSession(REQUEST);

    ArgumentCaptor<String> identifier = ArgumentCaptor.forClass(String.class);
    verify(callKeyClient, org.mockito.Mockito.times(3)).createKey(any(), identifier.capture());
    String first = identifier.getAllValues().get(0);
    String again = identifier.getAllValues().get(1);
    String otherUser = identifier.getAllValues().get(2);

    // 64글자 16진수(SHA-256)이고, 사용자 번호 "7" 그 자체가 아닙니다.
    assertThat(first).hasSize(64).matches("[0-9a-f]+").isNotEqualTo("7");
    assertThat(again).isEqualTo(first);
    assertThat(otherUser).isNotEqualTo(first);
  }

  @Test
  @DisplayName("학생 카드 없이(null) 걸어도 통화가 시작된다")
  void worksWithoutACard() {
    CallSessionResponse response = callService.startSession(new CallSessionRequest(1, 3, null));

    assertThat(response.value()).isEqualTo("ek_abc123");
  }

  @Test
  @DisplayName("로그인하지 않았으면 401 이고, OpenAI 를 부르지 않는다")
  void requiresLogin() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    assertError(() -> callService.startSession(REQUEST), ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
    verify(callKeyClient, never()).createKey(any(), any());
  }

  @Test
  @DisplayName("통화가 꺼진 서버(내 컴퓨터)에서는 503 이고, 하루 한도를 쓰지 않는다")
  void unavailableWhenOff() {
    given(callKeyClient.isEnabled()).willReturn(false);
    for (int i = 0; i < 5; i++) {
      assertError(
          () -> callService.startSession(REQUEST),
          ErrorCode.SERVICE_UNAVAILABLE,
          "AI 통화가 아직 켜져 있지 않아요");
    }
    verify(callKeyClient, never()).createKey(any(), any());

    // 켜진 뒤에도 한도 2번이 그대로 남아 있습니다.
    given(callKeyClient.isEnabled()).willReturn(true);
    callService.startSession(REQUEST);
    callService.startSession(REQUEST);
  }

  @Test
  @DisplayName("하루 한도(2번)를 넘으면 429 이고, OpenAI 를 더 부르지 않는다. 다른 사람은 걸 수 있다")
  void tooManyCalls() {
    callService.startSession(REQUEST);
    callService.startSession(REQUEST);

    assertError(
        () -> callService.startSession(REQUEST),
        ErrorCode.TOO_MANY_REQUESTS,
        "오늘 통화는 다 썼어요. 내일 다시 만나요");
    verify(callKeyClient, org.mockito.Mockito.times(2)).createKey(any(), any());

    given(currentUserProvider.getCurrentUserId()).willReturn(8L);
    assertThat(callService.startSession(REQUEST).value()).isEqualTo("ek_abc123");
  }

  @Test
  @DisplayName("OpenAI 가 임시 키를 못 주면 503 이고, 통화가 시작되지 않았으니 한도를 쓰지 않는다")
  void failureDoesNotUseTheLimit() {
    given(callKeyClient.createKey(any(), any())).willReturn(Optional.empty());
    for (int i = 0; i < 5; i++) {
      assertError(
          () -> callService.startSession(REQUEST),
          ErrorCode.SERVICE_UNAVAILABLE,
          "AI 와 연결하지 못했어요. 잠시 후 다시 시도해 주세요");
    }

    // 다섯 번 실패했어도 한도 2번이 그대로 남아 있습니다.
    given(callKeyClient.createKey(any(), any())).willReturn(Optional.of(KEY));
    callService.startSession(REQUEST);
    callService.startSession(REQUEST);
    assertError(
        () -> callService.startSession(REQUEST),
        ErrorCode.TOO_MANY_REQUESTS,
        "오늘 통화는 다 썼어요. 내일 다시 만나요");
  }
}
