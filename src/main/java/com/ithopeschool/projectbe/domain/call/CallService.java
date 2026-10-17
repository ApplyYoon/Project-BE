package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.auth.TokenHasher;
import com.ithopeschool.projectbe.domain.bias.BiasQuestion;
import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.bias.Weakness;
import com.ithopeschool.projectbe.domain.call.CallKeyClient.CallKey;
import com.ithopeschool.projectbe.domain.call.dto.CallSessionRequest;
import com.ithopeschool.projectbe.domain.call.dto.CallSessionResponse;
import com.ithopeschool.projectbe.domain.thinkingtest.StudyStyle;
import com.ithopeschool.projectbe.domain.thinkingtest.ThinkingGap;
import com.ithopeschool.projectbe.domain.thinkingtest.ThinkingTest;
import com.ithopeschool.projectbe.domain.thinkingtest.ThinkingTestRepository;
import com.ithopeschool.projectbe.domain.thinkingtest.ThinkingTestService;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// AI 통화를 시작하는 로직입니다. (이슈 #27 · FE src/lib/realtimeCall.ts)
//
// 순서
//   1. 로그인한 사람인지 확인한다             (아니면 401)
//   2. 통화를 켤 수 있는 서버인지 본다         (내 컴퓨터에서는 꺼져 있습니다 → 503)
//   3. 오늘 건 횟수가 한도 안인지 본다         (넘었으면 429)
//   4. 최근 2주의 통화 기록을 읽는다          (CallRecord)
//   5. 오늘 확인할 생각 습관 질문을 고른다     (BiasQuestion: 자주 보인 습관 → 어제 이유 → 시작 지연 → 가입 테스트)
//      가입 테스트는 서버에 저장된 결과(thinking_tests)를 먼저 쓰고, 없으면 FE 가 보낸 값을 씁니다.
//      저장된 결과가 있으면 최근 통화와 견줘 "생각과 실제가 달랐던 점" 도 지침에 넣습니다. (ThinkingGap)
//   6. 학생 카드와 지난 통화로 대본(지침)을 만든다 (CallScript: 기록에 따라 질문과 말투가 달라집니다)
//   7. OpenAI 에서 그 대본이 묶인 임시 키를 받는다 (못 받으면 503)
//   8. 통화 기록을 한 줄 만들고, 임시 키와 기록 번호를 돌려준다
//
// 최근 7일 안에 위기 표현이 감지된 통화가 있으면, FE 가 보낸 관리 강도와 상관없이 위로하는 통화(0)로 합니다.
// 안전 브레이크는 어떤 설정보다 먼저입니다. (CLAUDE.md 4장)
//
// ⚠️ 학생 카드와 대본, 임시 키, 지난 통화의 요약은 로그에 찍지 않습니다.
@Service
public class CallService {

  // 지난 통화를 볼 기간(일). 오늘을 넣어 최근 2주의 통화로 자주 보이는 생각 습관을 찾습니다.
  static final int RECENT_DAYS = 14;
  // 지침에 넣는 "테스트에서 많이 해당한 습관" 의 최대 개수 (FE 가 보내는 것과 같은 3개)
  static final int MAX_SELF_REPORTED = 3;
  // 오늘을 넣어 최근 이 기간(일) 안에 위기 표현이 감지된 통화가 있으면, 오늘 통화는 위로하는 통화로 합니다.
  static final int CRISIS_CARE_DAYS = 7;

  private final CurrentUserProvider currentUserProvider;
  private final CallKeyClient callKeyClient;
  private final CallDailyLimit callDailyLimit;
  private final TokenHasher tokenHasher;
  private final CallRecordRepository callRecordRepository;
  private final ThinkingTestRepository thinkingTestRepository;
  private final Clock clock;
  // 통화 한 번의 최대 길이(초). FE 가 이 시간이 되면 끊습니다.
  private final int maxCallSeconds;

  public CallService(
      CurrentUserProvider currentUserProvider,
      CallKeyClient callKeyClient,
      CallDailyLimit callDailyLimit,
      TokenHasher tokenHasher,
      CallRecordRepository callRecordRepository,
      ThinkingTestRepository thinkingTestRepository,
      Clock clock,
      @Value("${app.ai.call-max-seconds:300}") int maxCallSeconds) {
    this.currentUserProvider = currentUserProvider;
    this.callKeyClient = callKeyClient;
    this.callDailyLimit = callDailyLimit;
    this.tokenHasher = tokenHasher;
    this.callRecordRepository = callRecordRepository;
    this.thinkingTestRepository = thinkingTestRepository;
    this.clock = clock;
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

    Instant now = clock.instant();
    LocalDate today = DayRule.dateOf(now);
    // 최근 2주의 내 통화 기록 (최근 것이 앞)
    List<CallRecord> recentCalls =
        callRecordRepository.findByUserIdAndCallDateGreaterThanEqualOrderByStartedAtDescIdDesc(
            userId, today.minusDays(RECENT_DAYS - 1));

    // 최근에 위기 표현이 있었으면 위로하는 통화로 합니다. (관리 강도 0 이면 말투·순서가 모두 위로하는 쪽으로 바뀝니다)
    int careLevel = hasRecentCrisis(recentCalls, today) ? 0 : request.careLevel();
    CallSessionRequest.Card card = request.card();

    // 생각 습관 테스트 결과. 서버에 저장된 것이 있으면 그것을, 없으면 FE 가 보낸 것을 씁니다.
    CallScript.SelfReport selfReport = selfReportOf(userId, request, recentCalls);

    BiasQuestion biasQuestion =
        BiasQuestion.pick(
            CallScript.isSupportive(careLevel, card),
            card == null ? null : card.yesterdayMissReason(),
            card == null ? null : card.weekStartDelayMinutes(),
            foundBiasesOf(recentCalls),
            askedQuestionKeysOf(recentCalls),
            selfReport.biases());
    String instructions =
        CallScript.instructions(
            careLevel,
            request.maxHarshness(),
            card,
            biasQuestion,
            pastCallsOf(recentCalls),
            selfReport);
    // OpenAI 에는 "어느 사용자의 요청인지" 를 알아볼 수 없는 글자로만 알려 줍니다. (번호를 그대로 보내지 않습니다)
    String safetyIdentifier = tokenHasher.hash("starflow-user-" + userId);

    Optional<CallKey> key = callKeyClient.createKey(instructions, safetyIdentifier);
    if (key.isEmpty()) {
      // 통화가 시작되지 않았으니, 방금 올린 횟수를 되돌립니다. 통화 기록도 만들지 않습니다.
      callDailyLimit.giveBack(userId);
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "AI 와 연결하지 못했어요. 잠시 후 다시 시도해 주세요");
    }

    CallRecord record = callRecordRepository.save(new CallRecord(userId, today, now, biasQuestion));
    return new CallSessionResponse(
        key.get().value(),
        key.get().expiresAtMillis(),
        maxCallSeconds,
        String.valueOf(record.getId()));
  }

  // 통화 지침에 넣을 "생각" 쪽 자료를 모읍니다.
  //   서버에 저장된 테스트가 있으면: 많이 해당한 습관(최대 3개) · 약점 칩 · 최근 2주 통화와 견줘 달랐던 점
  //   없으면: FE 가 요청에 실어 보낸 습관 · 약점 칩 (견줄 것은 없음)
  // 견준 결과는 통화가 3번 이상 쌓였을 때만 넣습니다. 그 전에는 "달랐다" 고 말하기 이릅니다. (ThinkingGap.MIN_CALLS)
  private CallScript.SelfReport selfReportOf(
      Long userId, CallSessionRequest request, List<CallRecord> recentCalls) {
    Optional<ThinkingTest> saved = thinkingTestRepository.findFirstByUserIdOrderByIdDesc(userId);
    if (saved.isEmpty()) {
      return new CallScript.SelfReport(
          selfReportedOf(request.selfReportedBiases()),
          weaknessesOf(request.selfReportedWeaknesses()),
          List.of());
    }
    ThinkingTest test = saved.get();
    List<CognitiveBias> top = StudyStyle.topHabitsOf(test.getScores());
    List<Weakness> weaknesses = new ArrayList<>();
    for (Weakness weakness : Weakness.values()) {
      if (test.getWeaknesses().contains(weakness)) {
        weaknesses.add(weakness);
      }
    }
    ThinkingGap gap = ThinkingTestService.gapOf(test, recentCalls, RECENT_DAYS);
    return new CallScript.SelfReport(
        top.subList(0, Math.min(MAX_SELF_REPORTED, top.size())),
        weaknesses,
        gap.enoughData() ? gap.differenceSentences() : List.of());
  }

  // 가입 테스트에서 많이 고른 습관 이름들을 목록의 습관으로 바꿉니다. 안 보냈으면 빈 목록입니다.
  // (이름은 요청을 받을 때 이미 검사했습니다. CallSessionRequest.BIAS_NAMES)
  static List<CognitiveBias> selfReportedOf(List<String> names) {
    List<CognitiveBias> biases = new ArrayList<>();
    if (names == null) {
      return biases;
    }
    for (String name : names) {
      biases.add(CognitiveBias.valueOf(name));
    }
    return biases;
  }

  // 가입 테스트에서 고른 약점 칩 이름들을 Weakness 로 바꿉니다. 안 보냈으면 빈 목록입니다. (이름은 이미 검사했습니다)
  static List<Weakness> weaknessesOf(List<String> names) {
    List<Weakness> weaknesses = new ArrayList<>();
    if (names == null) {
      return weaknesses;
    }
    for (String name : names) {
      weaknesses.add(Weakness.valueOf(name));
    }
    return weaknesses;
  }

  // 오늘을 넣어 최근 7일 안에 위기 표현이 감지된 통화가 있으면 true 입니다.
  static boolean hasRecentCrisis(List<CallRecord> recentCalls, LocalDate today) {
    LocalDate from = today.minusDays(CRISIS_CARE_DAYS - 1);
    for (CallRecord call : recentCalls) {
      if (call.isCrisisDetected() && !call.getCallDate().isBefore(from)) {
        return true;
      }
    }
    return false;
  }

  // 지난 통화에서 보인 생각 습관들 (최근 것이 앞). 위기 표현이 있던 통화는 내용을 남기지 않아서 저절로 빠집니다.
  static List<CognitiveBias> foundBiasesOf(List<CallRecord> recentCalls) {
    List<CognitiveBias> found = new ArrayList<>();
    for (CallRecord call : recentCalls) {
      if (call.getFoundBias() != null) {
        found.add(call.getFoundBias());
      }
    }
    return found;
  }

  // 지난 통화에서 고른 질문의 이름들 (최근 것이 앞). 같은 질문이 연달아 나오지 않게 하는 데 씁니다.
  static List<String> askedQuestionKeysOf(List<CallRecord> recentCalls) {
    List<String> keys = new ArrayList<>();
    for (CallRecord call : recentCalls) {
      if (call.getAskedQuestionKey() != null) {
        keys.add(call.getAskedQuestionKey());
      }
    }
    return keys;
  }

  // 지침에 적을 지난 통화들 (최근 것이 앞). 마무리한 통화만 넣고, 위기 표현이 있던 통화는 넣지 않습니다.
  static List<CallScript.PastCall> pastCallsOf(List<CallRecord> recentCalls) {
    List<CallScript.PastCall> pastCalls = new ArrayList<>();
    for (CallRecord call : recentCalls) {
      if (!call.isFinished() || call.isCrisisDetected()) {
        continue;
      }
      pastCalls.add(
          new CallScript.PastCall(
              call.getCallDate(),
              call.getSummary(),
              call.getFoundBias(),
              call.getFoundEvidence(),
              CallSummary.promiseOf(call.getPromiseIf(), call.getPromiseThen()),
              call.getPromiseKept()));
    }
    return pastCalls;
  }
}
