package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.auth.TokenHasher;
import com.ithopeschool.projectbe.domain.call.CallKeyClient.CallKey;
import com.ithopeschool.projectbe.domain.call.dto.CallSessionRequest;
import com.ithopeschool.projectbe.domain.call.dto.CallSessionResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// AI 통화를 시작하는 로직입니다. (이슈 #27 · FE src/lib/realtimeCall.ts)
//
// 순서
//   1. 로그인한 사람인지 확인한다             (아니면 401)
//   2. 통화를 켤 수 있는 서버인지 본다         (내 컴퓨터에서는 꺼져 있습니다 → 503)
//   3. 오늘 건 횟수가 한도 안인지 본다         (넘었으면 429)
//   4. 학생 카드로 대본(지침)을 만든다         (CallScript: 기록에 따라 질문과 말투가 달라집니다)
//   5. OpenAI 에서 그 대본이 묶인 임시 키를 받아 돌려준다   (못 받으면 503)
//
// ⚠️ 학생 카드와 대본, 임시 키는 로그에 찍지 않습니다.
@Service
public class CallService {

  private final CurrentUserProvider currentUserProvider;
  private final CallKeyClient callKeyClient;
  private final CallDailyLimit callDailyLimit;
  private final TokenHasher tokenHasher;
  // 통화 한 번의 최대 길이(초). FE 가 이 시간이 되면 끊습니다.
  private final int maxCallSeconds;

  public CallService(
      CurrentUserProvider currentUserProvider,
      CallKeyClient callKeyClient,
      CallDailyLimit callDailyLimit,
      TokenHasher tokenHasher,
      @Value("${app.ai.call-max-seconds:300}") int maxCallSeconds) {
    this.currentUserProvider = currentUserProvider;
    this.callKeyClient = callKeyClient;
    this.callDailyLimit = callDailyLimit;
    this.tokenHasher = tokenHasher;
    this.maxCallSeconds = maxCallSeconds;
  }

  public CallSessionResponse startSession(CallSessionRequest request) {
    // 로그인하지 않은 요청은 여기서 401 이 나갑니다. 아무나 통화를 걸어 비용을 쓰게 두면 안 됩니다.
    Long userId = currentUserProvider.getCurrentUserId();

    if (!callKeyClient.isEnabled()) {
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "AI 통화가 아직 켜져 있지 않아요");
    }
    if (!callDailyLimit.tryUse(userId)) {
      throw new ApiException(ErrorCode.TOO_MANY_REQUESTS, "오늘 통화는 다 썼어요. 내일 다시 만나요");
    }

    String instructions =
        CallScript.instructions(request.careLevel(), request.maxHarshness(), request.card());
    // OpenAI 에는 "어느 사용자의 요청인지" 를 알아볼 수 없는 글자로만 알려 줍니다. (번호를 그대로 보내지 않습니다)
    String safetyIdentifier = tokenHasher.hash("starflow-user-" + userId);

    Optional<CallKey> key = callKeyClient.createKey(instructions, safetyIdentifier);
    if (key.isEmpty()) {
      // 통화가 시작되지 않았으니, 방금 올린 횟수를 되돌립니다.
      callDailyLimit.giveBack(userId);
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "AI 와 연결하지 못했어요. 잠시 후 다시 시도해 주세요");
    }
    return new CallSessionResponse(key.get().value(), key.get().expiresAtMillis(), maxCallSeconds);
  }
}
